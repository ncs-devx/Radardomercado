package com.radardemercado.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.radardemercado.app.data.*
import com.radardemercado.app.notifications.AlertNotifications
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

enum class LiveStatus { CONNECTING, LIVE, STALE, OFFLINE }

data class MarketUiState(
    val quote: QuoteCurrency = QuoteCurrency.USDT,
    val tickers: List<Ticker> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val liveStatus: LiveStatus = LiveStatus.CONNECTING,
    val lastUpdatedAt: Long = 0L
)

data class AnalysisUiState(val loading: Boolean = false, val data: MarketAnalysis? = null, val error: String? = null)
data class NewsUiState(val loading: Boolean = true, val items: List<NewsItem> = emptyList(), val error: String? = null)

class RadarViewModel(
    private val repository: MarketRepository,
    context: Context
) : ViewModel() {
    private val appContext = context.applicationContext
    private val cachedTickers = repository.cachedTickers()
    private val _market = MutableStateFlow(
        MarketUiState(
            tickers = cachedTickers,
            loading = cachedTickers.isEmpty(),
            lastUpdatedAt = cachedTickers.maxOfOrNull { it.updatedAt } ?: 0L
        )
    )
    val market = _market.asStateFlow()
    private val _analysis = MutableStateFlow(AnalysisUiState())
    val analysis = _analysis.asStateFlow()
    val alerts = repository.alerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = repository.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, Asset.DEFAULTS)
    private val _twelveDataKey = MutableStateFlow(repository.twelveDataKey())
    val twelveDataKey = _twelveDataKey.asStateFlow()
    private val cachedNews = repository.cachedNews()
    private val _news = MutableStateFlow(NewsUiState(loading = cachedNews.isEmpty(), items = cachedNews))
    val news = _news.asStateFlow()
    val newsEnabled = repository.newsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, repository.isNewsEnabled())
    val alertSound = repository.alertSound.stateIn(viewModelScope, SharingStarted.Eagerly, repository.alertSoundEnabled())
    val alertVibration = repository.alertVibration.stateIn(viewModelScope, SharingStarted.Eagerly, repository.alertVibrationEnabled())
    val lastBackgroundCheck = repository.lastBackgroundCheck.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private var liveJob: Job? = null

    init {
        refresh()
        if (repository.isNewsEnabled()) refreshNews()
        startLive()
        viewModelScope.launch {
            favorites.drop(1).collect {
                refresh()
                startLive()
            }
        }
        viewModelScope.launch { repository.alerts.drop(1).collect { startLive() } }
        viewModelScope.launch { newsEnabled.drop(1).collect { enabled -> if (enabled) refreshNews() else _news.value = NewsUiState(loading = false) } }
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                _market.update { state ->
                    if (state.liveStatus == LiveStatus.LIVE && state.lastUpdatedAt > 0 && System.currentTimeMillis() - state.lastUpdatedAt > 30_000)
                        state.copy(liveStatus = LiveStatus.STALE)
                    else state
                }
            }
        }
    }

    fun setQuote(quote: QuoteCurrency) {
        _market.update { it.copy(quote = quote) }
        refresh()
        startLive()
    }

    fun refresh() = viewModelScope.launch {
        _market.update { it.copy(loading = true, error = null) }
        val quote = _market.value.quote
        runCatching {
            val available = repository.searchableAssets(quote).toSet()
            repository.tickers(quote, favorites.value.filter { it in available })
        }
            .onSuccess { values -> _market.update { it.copy(tickers = values, loading = false, lastUpdatedAt = values.maxOfOrNull { ticker -> ticker.updatedAt } ?: it.lastUpdatedAt) } }
            .onFailure { _market.update { it.copy(loading = false, error = if (it.tickers.isEmpty()) "Não foi possível atualizar as cotações" else "Exibindo os últimos preços salvos") } }
    }

    private fun startLive() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            while (true) {
                val selectedQuote = _market.value.quote
                _market.update { it.copy(liveStatus = LiveStatus.CONNECTING) }
                try {
                    val available = repository.searchableAssets(selectedQuote).toSet()
                    val alertAssets = repository.activeAlerts().filter { it.quote == selectedQuote }.map { it.asset }
                    val selectedAssets = (favorites.value + alertAssets).distinct().filter { it in available }
                    if (selectedAssets.isEmpty()) {
                        _market.update { it.copy(tickers = emptyList(), loading = false, liveStatus = LiveStatus.OFFLINE) }
                        return@launch
                    }
                    repository.liveTickers(
                        selectedQuote,
                        selectedAssets,
                        onConnected = {
                            _market.update { it.copy(liveStatus = LiveStatus.LIVE, error = null) }
                        }
                    ).collect { ticker ->
                        if (_market.value.quote != selectedQuote) return@collect
                        _market.update { state ->
                            val updated = state.tickers.filterNot { it.asset == ticker.asset } + ticker
                            state.copy(
                                tickers = updated.sortedBy { it.asset.symbol },
                                loading = false,
                                error = null,
                                liveStatus = LiveStatus.LIVE,
                                lastUpdatedAt = ticker.updatedAt
                            )
                        }
                        evaluateLiveAlerts(ticker)
                    }
                } catch (_: Exception) {
                    _market.update { it.copy(liveStatus = LiveStatus.OFFLINE) }
                }
                delay(2_000)
            }
        }
    }

    private suspend fun evaluateLiveAlerts(ticker: Ticker) {
        repository.activeAlerts()
            .filter { it.asset == ticker.asset && it.quote == ticker.quote }
            .forEach { alert ->
                if (repository.evaluate(alert, ticker.price)) {
                    AlertNotifications.show(appContext, AlertHistory(
                        alertId = alert.id,
                        asset = alert.asset,
                        quote = alert.quote,
                        direction = alert.direction,
                        targetPrice = alert.targetPrice,
                        observedPrice = ticker.price
                    ), repository.alertSoundEnabled(), repository.alertVibrationEnabled())
                }
            }
    }

    fun save(alert: PriceAlert, done: () -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.save(alert) }
            .onSuccess { done() }
            .onFailure { Log.e("RadarStorage", "Falha ao salvar alerta", it) }
    }

    fun delete(alert: PriceAlert) = viewModelScope.launch {
        runCatching { repository.delete(alert) }.onFailure { Log.e("RadarStorage", "Falha ao excluir alerta", it) }
    }

    fun toggleFavorite(asset: Asset) = viewModelScope.launch {
        runCatching { repository.toggleFavorite(asset) }.onFailure { Log.e("RadarStorage", "Falha ao salvar favorito", it) }
    }

    fun loadSearchAssets(quote: QuoteCurrency, result: (Result<List<Asset>>) -> Unit) =
        viewModelScope.launch { result(runCatching { repository.searchableAssets(quote) }) }

    fun loadCandles(asset: Asset, quote: QuoteCurrency, interval: String, result: (Result<List<Candle>>) -> Unit) =
        viewModelScope.launch { result(runCatching { repository.candles(asset, quote, interval) }) }
    fun saveTwelveDataKey(value: String): Boolean {
        val saved = repository.saveTwelveDataKey(value)
        if (saved) _twelveDataKey.value = value.trim()
        return saved
    }
    fun refreshNews() = viewModelScope.launch {
        if (!repository.isNewsEnabled()) return@launch
        _news.value = _news.value.copy(loading = true, error = null)
        runCatching { repository.topNews() }
            .onSuccess { items -> _news.value = NewsUiState(items = items.ifEmpty { _news.value.items }, error = if (items.isEmpty() && _news.value.items.isEmpty()) "Nenhuma manchete recebida" else null) }
            .onFailure { _news.value = NewsUiState(loading = false, items = _news.value.items, error = if (_news.value.items.isEmpty()) "Não foi possível carregar as notícias" else "Exibindo notícias salvas") }
    }
    fun setNewsEnabled(enabled: Boolean) = repository.setNewsEnabled(enabled)
    fun setAlertSound(enabled: Boolean) = repository.setAlertSound(enabled)
    fun setAlertVibration(enabled: Boolean) = repository.setAlertVibration(enabled)
    fun testNotification(): Boolean = AlertNotifications.show(appContext, AlertHistory(alertId = 987654, asset = Asset.BTC, quote = QuoteCurrency.USDT, direction = AlertDirection.ABOVE, targetPrice = 60000.0, observedPrice = 60000.0, simulated = true), repository.alertSoundEnabled(), repository.alertVibrationEnabled())
    fun notificationsEnabled() = AlertNotifications.enabled(appContext)
    fun searchGlobal(query: String, result: (Result<List<GlobalInstrument>>) -> Unit) = viewModelScope.launch { result(runCatching { repository.searchGlobal(query) }) }
    fun loadGlobalCandles(symbol: String, interval: String, result: (Result<List<Candle>>) -> Unit) = viewModelScope.launch { result(runCatching { repository.globalCandles(symbol, interval) }) }

    fun loadAnalysis() = viewModelScope.launch {
        _analysis.value = AnalysisUiState(loading = true)
        runCatching { repository.marketAnalysis() }
            .onSuccess { _analysis.value = AnalysisUiState(data = it) }
            .onFailure { _analysis.value = AnalysisUiState(error = "Não foi possível calcular a análise agora") }
    }

    fun toggle(alert: PriceAlert) = save(alert.copy(enabled = !alert.enabled, triggered = false, waitingForReset = false))

    fun reactivate(item: AlertHistory) = viewModelScope.launch {
        runCatching {
            val existing = alerts.value.firstOrNull { it.id == item.alertId }
            repository.save(
                existing?.copy(enabled = true, triggered = false, waitingForReset = false)
                    ?: PriceAlert(asset = item.asset, quote = item.quote, direction = item.direction, targetPrice = item.targetPrice)
            )
        }.onFailure { Log.e("RadarStorage", "Falha ao reativar alerta", it) }
    }

    fun simulate(asset: Asset, quote: QuoteCurrency, price: Double, onResult: (Int) -> Unit) = viewModelScope.launch {
        var hits = 0
        repository.activeAlerts().filter { it.asset == asset && it.quote == quote }.forEach { alert ->
            if (repository.evaluate(alert, price, simulated = true)) {
                hits++
                AlertNotifications.show(appContext, AlertHistory(
                    alertId = alert.id,
                    asset = alert.asset,
                    quote = alert.quote,
                    direction = alert.direction,
                    targetPrice = alert.targetPrice,
                    observedPrice = price,
                    simulated = true
                ), repository.alertSoundEnabled(), repository.alertVibrationEnabled())
            }
        }
        onResult(hits)
    }
}

class RadarViewModelFactory(
    private val repository: MarketRepository,
    context: Context
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RadarViewModel(repository, appContext) as T
}
