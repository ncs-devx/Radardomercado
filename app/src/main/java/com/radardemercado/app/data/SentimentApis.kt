package com.radardemercado.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

data class CmcFearGreedResponse(val data: CmcFearGreedData)
data class CmcFearGreedData(
    val value: Int,
    @SerializedName("value_classification") val classification: String,
    @SerializedName("update_time") val updateTime: String?
)

data class CmcAltcoinResponse(val data: CmcAltcoinData)
data class CmcAltcoinData(@SerializedName("altcoin_index") val altcoinIndex: Int)

interface CoinMarketCapPublicApi {
    @GET("public-api/v3/fear-and-greed/latest")
    suspend fun fearAndGreed(): CmcFearGreedResponse

    @GET("public-api/v1/altcoin-season-index/latest")
    suspend fun altcoinSeason(): CmcAltcoinResponse
}

data class AlternativeFearGreedResponse(val data: List<AlternativeFearGreedData>)
data class AlternativeFearGreedData(
    val value: String,
    @SerializedName("value_classification") val classification: String
)

interface AlternativeMeApi {
    @GET("fng/?limit=1")
    suspend fun fearAndGreed(): AlternativeFearGreedResponse
}
