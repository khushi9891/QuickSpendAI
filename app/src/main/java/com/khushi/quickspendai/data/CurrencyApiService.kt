package com.khushi.quickspendai.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class ExchangeRateResponse(
    val base_code: String = "INR",
    val rates: Map<String, Double> = emptyMap()
)

interface ExchangeRateApi {
    @GET("v6/latest/{base}")
    suspend fun getRates(@Path("base") baseCurrency: String): ExchangeRateResponse
}

object CurrencyNetworkClient {
    private const val BASE_URL = "https://open.er-api.com/"

    val api: ExchangeRateApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }

    fun convertOfflineFallback(amount: Double, fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency == toCurrency) return amount
        val toInr = when (fromCurrency.uppercase()) {
            "USD" -> amount * 84.0
            "EUR" -> amount * 91.0
            "GBP" -> amount * 106.0
            else -> amount
        }
        return when (toCurrency.uppercase()) {
            "USD" -> toInr / 84.0
            "EUR" -> toInr / 91.0
            "GBP" -> toInr / 106.0
            else -> toInr
        }
    }
}
