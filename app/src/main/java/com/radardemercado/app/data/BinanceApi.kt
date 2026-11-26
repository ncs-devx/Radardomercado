package com.radardemercado.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class BinanceTicker(
    val symbol: String,
    @SerializedName("lastPrice") val lastPrice: String,
    @SerializedName("priceChangePercent") val priceChangePercent: String,
    @SerializedName("quoteVolume") val quoteVolume: String = "0"
)

data class BinanceExchangeInfo(val symbols: List<BinanceSymbol>)
data class BinanceSymbol(
    val symbol: String,
    val status: String,
    val baseAsset: String,
    val quoteAsset: String
)

interface BinanceApi {
    @GET("api/v3/exchangeInfo")
    suspend fun exchangeInfo(): BinanceExchangeInfo
    @GET("api/v3/ticker/24hr")
    suspend fun ticker(@Query("symbol") symbol: String): BinanceTicker
    @GET("api/v3/ticker/24hr")
    suspend fun allTickers(): List<BinanceTicker>

    @GET("api/v3/klines")
    suspend fun klines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 80
    ): List<List<Any>>
}
