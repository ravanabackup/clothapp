package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AirQualityResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "timezone") val timezone: String?,
    @Json(name = "current") val current: CurrentAirQuality?,
    @Json(name = "hourly") val hourly: HourlyAirQuality?
)

@JsonClass(generateAdapter = true)
data class CurrentAirQuality(
    @Json(name = "time") val time: String,
    @Json(name = "us_aqi") val usAqi: Double?,
    @Json(name = "pm2_5") val pm25: Double?,
    @Json(name = "pm10") val pm10: Double?,
    @Json(name = "o3") val ozone: Double?,
    @Json(name = "no2") val nitrogenDioxide: Double?,
    @Json(name = "so2") val sulphurDioxide: Double?,
    @Json(name = "co") val carbonMonoxide: Double?
)

@JsonClass(generateAdapter = true)
data class HourlyAirQuality(
    @Json(name = "time") val time: List<String>,
    @Json(name = "us_aqi") val usAqi: List<Double?>?
)
