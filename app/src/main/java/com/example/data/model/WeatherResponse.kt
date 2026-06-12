package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "timezone") val timezone: String?,
    @Json(name = "timezone_abbreviation") val timezoneAbbreviation: String?,
    @Json(name = "current") val current: CurrentWeather?,
    @Json(name = "hourly") val hourly: HourlyWeather?
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @Json(name = "time") val time: String,
    @Json(name = "temperature_2m") val temperature2m: Double,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Double,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double,
    @Json(name = "precipitation") val precipitation: Double,
    @Json(name = "cloud_cover") val cloudCover: Double,
    @Json(name = "weather_code") val weatherCode: Int?
)

@JsonClass(generateAdapter = true)
data class HourlyWeather(
    @Json(name = "time") val time: List<String>,
    @Json(name = "temperature_2m") val temperature2m: List<Double>,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: List<Double>,
    @Json(name = "wind_speed_10m") val windSpeed10m: List<Double>,
    @Json(name = "precipitation") val precipitation: List<Double>,
    @Json(name = "cloud_cover") val cloudCover: List<Double>,
    @Json(name = "weather_code") val weatherCode: List<Int>?
)
