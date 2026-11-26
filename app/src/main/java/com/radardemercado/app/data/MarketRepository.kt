package com.radardemercado.app.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.roundToInt
import android.util.Log
import android.util.Xml
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.io.IOException

class MarketRepository(private val dao: AlertStore) {
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        .build()
    private val api: BinanceApi = Retrofit.Builder()
        .baseUrl("https://api.binance.com/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BinanceApi::class.java)
    private val coinMarketCapApi: CoinMarketCapPublicApi = Retrofit.Builder()
        .baseUrl("https://pro-api.coinmarketcap.com/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CoinMarketCapPublicApi::class.java)
    private val alternativeMeApi: AlternativeMeApi = Retrofit.Builder()
        .baseUrl("https://api.alternative.me/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AlternativeMeApi::class.java)
    private val twelveDataApi: TwelveDataApi = Retrofit.Builder().baseUrl("https://api.twelvedata.com/").client(httpClient).addConverterFactory(GsonConverterFactory.create()).build().create(TwelveDataApi::class.java)
    private val exchangeInfoMutex = Mutex()
    private val analysisSemaphore = Semaphore(5)
    private var exchangeInfoCache: Pair<Long, List<BinanceSymbol>>? = null
    private var tickerMemory = dao.cachedTickers().associateBy { it.symbol }.toMutableMap()
    private var lastTickerCacheWrite = 0L

    val alerts = dao.observeAlerts()
    val history = dao.observeHistory()
    val favorites = dao.observeFavorites()
    val newsEnabled = dao.observeNewsEnabled()
    val alertSound = dao.observeAlertSound()
    val alertVibration = dao.observeAlertVibration()
    val lastBackgroundCheck = dao.observeLastBackgroundCheck()

    suspend fun tickers(quote: QuoteCurrency, assets: List<Asset> = Asset.DEFAULTS): List<Ticker> {
        val values = coroutineScope {
            assets.map { asset ->
                async {
                    runCatching { remoteTicker(asset, quote) }
                        .onFailure { Log.w("RadarMarket", "Cotação inválida para ${asset.symbol}${quote.name}", it) }
                        .getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        if (values.isEmpty()) throw IOException("Nenhuma cotação válida recebida")
        cacheTickers(values, force = true)
        return values
    }

    private suspend fun remoteTicker(asset: Asset, quote: QuoteCurrency): Ticker {
        val remote = api.ticker(asset.symbol + quote.name)
        val price = remote.lastPrice.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
            ?: throw IOException("Preço inválido")
        val change = remote.priceChangePercent.toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: throw IOException("Variação inválida")
        return Ticker(asset, quote, price, change)
    }

    suspend fun ticker(asset: Asset, quote: QuoteCurrency): Ticker = remoteTicker(asset, quote)

    suspend fun candles(asset: Asset, quote: QuoteCurrency, interval: String): List<Candle> =
        api.klines(asset.symbol + quote.name, interval).mapNotNull(::parseCandle)
            .also { if (it.isEmpty()) throw IOException("Nenhum candle válido recebido") }

    /** Cotações públicas em tempo real; não usa conta, chave ou endpoint de ordens. */
    fun liveTickers(
        quote: QuoteCurrency,
        assets: List<Asset>,
        onConnected: () -> Unit = {}
    ): Flow<Ticker> = callbackFlow {
        val streams = assets.joinToString("/") {
            "${it.symbol.lowercase()}${quote.name.lowercase()}@ticker"
        }
        val request = Request.Builder()
            .url("wss://data-stream.binance.vision/stream?streams=$streams")
            .build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("RadarSocket", "Conectado: ${assets.joinToString { it.symbol + quote.name }}")
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val root = JsonParser.parseString(text).asJsonObject
                    val data = root.getAsJsonObject("data")
                    val symbol = data["s"].asString
                    val asset = assets.first { symbol == it.symbol + quote.name }
                    Ticker(
                        asset = asset,
                        quote = quote,
                        price = data["c"].asString.toDouble(),
                        changePercent = data["P"].asString.toDouble(),
                        updatedAt = data["E"].asLong
                    )
                }.onSuccess {
                    cacheTickers(listOf(it))
                    trySend(it)
                }
                    .onFailure { Log.w("RadarSocket", "Mensagem da Binance não reconhecida", it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("RadarSocket", "WebSocket falhou: HTTP ${response?.code ?: "sem resposta"}", t)
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("RadarSocket", "WebSocket fechado: $code $reason")
                close()
            }
        }
        val socket = httpClient.newWebSocket(request, listener)
        awaitClose { socket.close(1000, "App em segundo plano") }
    }

    suspend fun save(alert: PriceAlert) = dao.saveAlert(alert)
    suspend fun delete(alert: PriceAlert) = dao.deleteAlert(alert)
    suspend fun toggleFavorite(asset: Asset) = dao.toggleFavorite(asset)
    suspend fun searchableAssets(quote: QuoteCurrency): List<Asset> = exchangeSymbols()
        .asSequence()
        .filter { it.status == "TRADING" && it.quoteAsset == quote.name }
        .map { Asset(it.baseAsset) }
        .distinct()
        .sortedBy { it.symbol }
        .toList()
    suspend fun find(id: Long) = dao.findAlert(id)
    suspend fun activeAlerts() = dao.activeAlerts()
    fun twelveDataKey() = dao.twelveDataKey()
    fun saveTwelveDataKey(value: String) = dao.saveTwelveDataKey(value)
    fun isNewsEnabled() = dao.isNewsEnabled()
    fun setNewsEnabled(enabled: Boolean) = dao.setNewsEnabled(enabled)
    fun cachedTickers() = dao.cachedTickers()
    fun cachedNews() = dao.cachedNews()
    fun alertSoundEnabled() = dao.alertSoundEnabled()
    fun alertVibrationEnabled() = dao.alertVibrationEnabled()
    fun setAlertSound(enabled: Boolean) = dao.setAlertSound(enabled)
    fun setAlertVibration(enabled: Boolean) = dao.setAlertVibration(enabled)
    fun recordBackgroundCheck() = dao.recordBackgroundCheck()
    suspend fun searchGlobal(query: String): List<GlobalInstrument> {
        val key = twelveDataKey(); require(key.isNotBlank()) { "Configure a chave da Twelve Data" }
        val response = twelveDataApi.search(query, key)
        require(response.status != "error") { response.message ?: "Falha na pesquisa" }
        return response.data.distinctBy { it.symbol + it.exchange }.take(50)
    }
    suspend fun globalCandles(symbol: String, interval: String): List<Candle> {
        val key = twelveDataKey(); require(key.isNotBlank()) { "Configure a chave da Twelve Data" }
        val response = twelveDataApi.timeSeries(symbol, interval, key = key)
        require(response.status != "error") { response.message ?: "Falha no gráfico" }
        return response.values.mapIndexed { index, it -> Candle(index.toLong(), it.open.toDouble(), it.high.toDouble(), it.low.toDouble(), it.close.toDouble(), it.volume?.toDoubleOrNull() ?: 0.0) }
    }
    suspend fun topNews(): List<NewsItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            listOf(
                "CoinDesk" to "https://www.coindesk.com/arc/outboundfeeds/rss/?outputType=xml",
                "Cointelegraph" to "https://cointelegraph.com/rss",
                "Decrypt" to "https://decrypt.co/feed"
            ).map { (source, url) ->
                async {
                    runCatching { readNewsFeed(source, url) }
                        .onFailure { Log.w("RadarNews", "Falha ao carregar $source: ${it.message}", it) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll()
                .flatten()
                .distinctBy { it.link }
                .sortedByDescending { it.publishedAt }
                .take(6)
                .also { if (it.isNotEmpty()) dao.saveCachedNews(it) }
        }
    }

    private suspend fun exchangeSymbols(): List<BinanceSymbol> = exchangeInfoMutex.withLock {
        val now = System.currentTimeMillis()
        exchangeInfoCache?.takeIf { now - it.first < EXCHANGE_INFO_TTL_MS }?.second?.let { return@withLock it }
        try {
            api.exchangeInfo().symbols.also { exchangeInfoCache = now to it }
        } catch (error: Exception) {
            exchangeInfoCache?.second ?: throw error
        }
    }

    @Synchronized
    private fun cacheTickers(incoming: List<Ticker>, force: Boolean = false) {
        incoming.forEach { tickerMemory[it.symbol] = it }
        val now = System.currentTimeMillis()
        if (force || now - lastTickerCacheWrite >= TICKER_CACHE_INTERVAL_MS) {
            dao.saveCachedTickers(tickerMemory.values.sortedBy { it.asset.symbol })
            lastTickerCacheWrite = now
        }
    }

    private fun readNewsFeed(source: String, url: String): List<NewsItem> {
        val response = httpClient.newCall(Request.Builder().url(url).header("User-Agent", "RadarDeMercado/1.3").build()).execute()
        response.use {
            if (!it.isSuccessful) error("HTTP ${it.code}")
            val body = it.body ?: return emptyList()
            val parser = Xml.newPullParser().apply { setInput(body.byteStream(), null) }
            val items = mutableListOf<NewsItem>(); var inside = false; var title = ""; var link = ""; var date = ""
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                        "item", "entry" -> { inside = true; title = ""; link = ""; date = "" }
                        "title" -> if (inside) title = parser.nextText().trim()
                        "link" -> if (inside) link = parser.getAttributeValue(null, "href") ?: parser.nextText().trim()
                        "pubdate", "published", "updated" -> if (inside) date = parser.nextText().trim()
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name.lowercase() in listOf("item", "entry") && inside) {
                        if (title.isNotBlank() && link.startsWith("http")) items += NewsItem(title, link, source, parseNewsDate(date)); inside = false
                    }
                }
                parser.next()
            }
            return items.take(5)
        }
    }

    private fun parseNewsDate(value: String): Long = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX")
        .firstNotNullOfOrNull { pattern -> runCatching { SimpleDateFormat(pattern, Locale.US).parse(value)?.time }.getOrNull() } ?: 0L

    private fun parseCandle(row: List<Any>): Candle? = runCatching {
        if (row.size < 6) return null
        val candle = Candle(
            openTime = (row[0] as? Number)?.toLong() ?: row[0].toString().toLong(),
            open = row[1].toString().toDouble(),
            high = row[2].toString().toDouble(),
            low = row[3].toString().toDouble(),
            close = row[4].toString().toDouble(),
            volume = row[5].toString().toDouble()
        )
        candle.takeIf {
            listOf(it.open, it.high, it.low, it.close, it.volume).all(Double::isFinite) &&
                it.low >= 0 && it.high >= it.low
        }
    }.getOrNull()

    suspend fun marketAnalysis(): MarketAnalysis = coroutineScope {
        val cmcFearDeferred = async { runCatching { coinMarketCapApi.fearAndGreed().data.value }.getOrNull() }
        val alternativeFearDeferred = async { runCatching { alternativeMeApi.fearAndGreed().data.firstOrNull()?.value?.toIntOrNull() }.getOrNull() }
        val cmcAltcoinDeferred = async { runCatching { coinMarketCapApi.altcoinSeason().data.altcoinIndex }.getOrNull() }
        val btcCandles = api.klines("BTCUSDT", "1d", 210)
        val btcCloses = btcCandles.map { it[4].toString().toDouble() }
        require(btcCloses.size >= 200) { "Histórico insuficiente" }
        val btcPrice = btcCloses.last()
        val ma50 = btcCloses.takeLast(50).average()
        val ma200 = btcCloses.takeLast(200).average()
        val return30 = btcPrice / btcCloses[btcCloses.lastIndex - 30] - 1.0
        val return90 = btcPrice / btcCloses[btcCloses.lastIndex - 90] - 1.0

        val stableOrLeveraged = setOf("USDC", "FDUSD", "TUSD", "USDP", "DAI", "EUR", "TRY", "BRL", "BUSD")
        val top = api.allTickers().asSequence()
            .filter { it.symbol.endsWith("USDT") }
            .map { it to it.symbol.removeSuffix("USDT") }
            .filter { (_, base) -> base != "BTC" && base !in stableOrLeveraged && !listOf("UP", "DOWN", "BULL", "BEAR").any { base.endsWith(it) } }
            .sortedByDescending { (ticker, _) -> ticker.quoteVolume.toDoubleOrNull() ?: 0.0 }
            .take(30)
            .toList()

        val performances = top.map { (ticker, base) ->
            async {
                analysisSemaphore.withPermit {
                    runCatching {
                        val closes = api.klines("${base}USDT", "1d", 91).mapNotNull { row -> row.getOrNull(4)?.toString()?.toDoubleOrNull() }
                        require(closes.size >= 91)
                        (closes.last() / closes.first() - 1.0) to (ticker.priceChangePercent.toDoubleOrNull() ?: 0.0)
                    }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()

        val radarAltIndex = if (performances.isEmpty()) 0 else
            (performances.count { it.first > return90 } * 100.0 / performances.size).roundToInt()
        val positiveBreadth = if (performances.isEmpty()) 50.0 else
            performances.count { it.second > 0 } * 100.0 / performances.size
        val trendScore = (50.0 + ((btcPrice / ma200) - 1.0) * 250.0).coerceIn(0.0, 100.0)
        val momentumScore = (50.0 + return30 * 200.0).coerceIn(0.0, 100.0)
        val radarSentiment = (trendScore * 0.45 + momentumScore * 0.35 + positiveBreadth * 0.20).roundToInt().coerceIn(0, 100)
        val cmcSentiment = cmcFearDeferred.await()?.coerceIn(0, 100)
        val alternativeSentiment = alternativeFearDeferred.await()?.coerceIn(0, 100)
        val sentimentValues = listOfNotNull(radarSentiment, cmcSentiment, alternativeSentiment)
        val sentiment = sentimentValues.average().roundToInt()
        val sentimentLabel = when (sentiment) {
            in 0..20 -> "Medo extremo"
            in 21..40 -> "Medo"
            in 41..59 -> "Neutro"
            in 60..79 -> "Ganância"
            else -> "Ganância extrema"
        }
        val cycle = when {
            btcPrice > ma200 && ma50 > ma200 && return90 > 0 -> MarketCycle.BULL
            btcPrice < ma200 && ma50 < ma200 && return90 < 0 -> MarketCycle.BEAR
            else -> MarketCycle.TRANSITION
        }
        val cmcAltcoin = cmcAltcoinDeferred.await()?.coerceIn(0, 100)
        val altcoinValues = listOfNotNull(radarAltIndex, cmcAltcoin)
        val combinedAltcoin = altcoinValues.average().roundToInt()
        MarketAnalysis(
            sentimentScore = sentiment,
            sentimentLabel = sentimentLabel,
            radarSentimentScore = radarSentiment,
            coinMarketCapSentiment = cmcSentiment,
            alternativeMeSentiment = alternativeSentiment,
            sentimentSources = sentimentValues.size,
            cycle = cycle,
            btcPrice = btcPrice,
            btcMa50 = ma50,
            btcMa200 = ma200,
            btcReturn90 = return90,
            altcoinSeasonIndex = combinedAltcoin,
            radarAltcoinSeasonIndex = radarAltIndex,
            coinMarketCapAltcoinSeasonIndex = cmcAltcoin,
            altcoinSources = altcoinValues.size,
            altcoinsAnalyzed = performances.size
        )
    }

    suspend fun evaluate(alert: PriceAlert, price: Double, simulated: Boolean = false): Boolean {
        return when (AlertRules.action(alert.direction, alert.targetPrice, price, alert.waitingForReset)) {
            AlertRules.Action.TRIGGER -> {
                dao.recordTrigger(alert, price, simulated)
                true
            }
            AlertRules.Action.REARM -> {
                dao.saveAlert(alert.copy(waitingForReset = false, triggered = false))
                false
            }
            AlertRules.Action.NONE -> false
        }
    }

    private companion object {
        const val EXCHANGE_INFO_TTL_MS = 6 * 60 * 60 * 1000L
        const val TICKER_CACHE_INTERVAL_MS = 15_000L
    }
}
