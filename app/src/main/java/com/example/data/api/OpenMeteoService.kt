package com.example.data.api

import com.example.data.model.AirQualityResponse
import com.example.data.model.GeocodingResponse
import com.example.data.model.WeatherResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface OpenMeteoService {

    @GET
    suspend fun getGeocoding(@Url url: String): GeocodingResponse

    @GET
    suspend fun getWeather(@Url url: String): WeatherResponse

    @GET
    suspend fun getAirQuality(@Url url: String): AirQualityResponse

    companion object {
        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        val api: OpenMeteoService by lazy {
            Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/") // Fallback placeholder base URL
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(OpenMeteoService::class.java)
        }
    }
}
