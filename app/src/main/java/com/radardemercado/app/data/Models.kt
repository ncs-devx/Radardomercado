package com.radardemercado.app.data

data class Asset(val symbol: String) {
    override fun toString() = symbol
    companion object {
        val BTC = Asset("BTC")
        val ETH = Asset("ETH")
        val SOL = Asset("SOL")
        val DEFAULTS = listOf(BTC, ETH, SOL)
    }
}
enum class QuoteCurrency { BRL, USDT }
enum class AlertDirection { ABOVE, BELOW }

data class Ticker(
    val asset: Asset,
    val quote: QuoteCurrency,
    val price: Double,
    val changePercent: Double,
    val updatedAt: Long = System.currentTimeMillis()
) { val symbol: String get() = asset.symbol + quote.name }

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class PriceAlert(
    val id: Long = 0,
    val asset: Asset,
    val quote: QuoteCurrency,
    val direction: AlertDirection,
    val targetPrice: Double,
    val enabled: Boolean = true,
    val triggered: Boolean = false,
    val waitingForReset: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class AlertHistory(
    val id: Long = 0,
    val alertId: Long,
    val asset: Asset,
    val quote: QuoteCurrency,
    val direction: AlertDirection,
    val targetPrice: Double,
    val observedPrice: Double,
    val triggeredAt: Long = System.currentTimeMillis(),
    val simulated: Boolean = false
)

enum class MarketCycle { BULL, TRANSITION, BEAR }

data class MarketAnalysis(
    val sentimentScore: Int,
    val sentimentLabel: String,
    val radarSentimentScore: Int,
    val coinMarketCapSentiment: Int?,
    val alternativeMeSentiment: Int?,
    val sentimentSources: Int,
    val cycle: MarketCycle,
    val btcPrice: Double,
    val btcMa50: Double,
    val btcMa200: Double,
    val btcReturn90: Double,
    val altcoinSeasonIndex: Int,
    val radarAltcoinSeasonIndex: Int,
    val coinMarketCapAltcoinSeasonIndex: Int?,
    val altcoinSources: Int,
    val altcoinsAnalyzed: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
