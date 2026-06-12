package com.example.data

import com.example.data.model.AirQualityResponse
import com.example.data.model.GeocodingResponse
import com.example.data.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("https://geocoding-api.open-meteo.com/v1/search")
    suspend fun searchLocation(
        @Query("name") name: String,
        @Query("count") count: Int = 5,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse

    @GET("https://api.open-meteo.com/v1/forecast")
    suspend fun getWeatherData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation,cloud_cover,weather_code",
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation,cloud_cover,weather_code",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse

    @GET("https://air-quality-api.open-meteo.com/v1/air-quality")
    suspend fun getAirQualityData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "us_aqi,pm2_5,pm10,o3,no2,so2,co",
        @Query("hourly") hourly: String = "us_aqi,pm2_5,pm10",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("timezone") timezone: String = "auto"
    ): AirQualityResponse

    @retrofit2.http.POST("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateGeminiContent(
        @Query("key") apiKey: String,
        @retrofit2.http.Body request: com.example.data.model.GenerateContentRequest
    ): com.example.data.model.GenerateContentResponse
}
