package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    @Json(name = "results") val results: List<GeocodingResult>?
)

@JsonClass(generateAdapter = true)
data class GeocodingResult(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "elevation") val elevation: Double?,
    @Json(name = "feature_code") val featureCode: String?,
    @Json(name = "country_code") val countryCode: String?,
    @Json(name = "admin1_id") val admin1Id: Long?,
    @Json(name = "admin2_id") val admin2Id: Long?,
    @Json(name = "admin1") val admin1: String?,
    @Json(name = "admin2") val admin2: String?,
    @Json(name = "country") val country: String?,
    @Json(name = "country_id") val countryId: Long?
)
