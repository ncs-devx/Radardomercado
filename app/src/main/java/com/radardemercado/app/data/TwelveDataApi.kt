package com.radardemercado.app.data

import retrofit2.http.GET
import retrofit2.http.Query

data class GlobalInstrument(val symbol: String, val instrument_name: String = symbol, val exchange: String = "", val instrument_type: String = "Mercado global", val currency: String = "USD")
data class GlobalSearchResponse(val data: List<GlobalInstrument> = emptyList(), val status: String? = null, val message: String? = null)
data class TwelveCandle(val datetime: String, val open: String, val high: String, val low: String, val close: String, val volume: String? = null)
data class TwelveTimeSeries(val values: List<TwelveCandle> = emptyList(), val status: String? = null, val message: String? = null)

interface TwelveDataApi {
    @GET("symbol_search") suspend fun search(@Query("symbol") query: String, @Query("apikey") key: String): GlobalSearchResponse
    @GET("time_series") suspend fun timeSeries(@Query("symbol") symbol: String, @Query("interval") interval: String, @Query("outputsize") size: Int = 80, @Query("order") order: String = "asc", @Query("apikey") key: String): TwelveTimeSeries
}
