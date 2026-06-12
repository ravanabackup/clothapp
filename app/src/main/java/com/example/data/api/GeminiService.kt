package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "responseMimeType") val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val api: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun generateClothingAdvisory(
        locationName: String,
        temp: Double,
        humidity: Double,
        windSpeed: Double,
        precipitation: Double,
        cloudCover: Double,
        aqi: Double
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Please configure your GEMINI_API_KEY in the Secrets panel to enable smart AI-powered clothing, mask, and health recommendations."
        }

        val prompt = """
            Provide a concise, professional, and visually structured outdoor laundry, clothing, and health safety advisor based on the following meteorological and atmospheric observations:
            - Location: $locationName
            - Temperature: $temp°C
            - Relative Humidity: $humidity%
            - Wind Speed: $windSpeed km/h
            - Precipitation: $precipitation mm/h
            - Cloud Cover: $cloudCover%
            - AQI (US Index): $aqi (where 0-50 is Good, 51-100 Moderate, 101-150 Unhealthy for Sensitive Groups, 151-200 Unhealthy, 201-300 Very Unhealthy, 301+ Hazardous)

            Structure your advice in 4 clear, concise bullet points:
            1. 👕 **Clothing Recommendation**: Recommend what type of clothes to wear (e.g., layers, breathable fabrics, insulation level) based on temperature, wind force, and rain.
            2. 🎭 **Mask & Protection**: Give a specific recommendation on whether to wear a mask (e.g. N95, surgical fabric, or none) and eyewear, strictly guided by the current AQI of $aqi.
            3. 🏃‍♂️ **Outdoor Protection**: State if the air quality and weather are suitable for outdoor exercise, cycling, running, or hanging out. Provide precise, actionable advice.
            4. 🧺 **Drying Advice**: Based on the humidity ($humidity%) and wind ($windSpeed km/h), briefly comment on whether clothes will dry fast or slow, and specify key fabric tips.

            Adopt a helpful, reassuring, and completely evidence-based tone. Max 4 lines of output, keeping it incredibly scannable and beautiful. Do not use generic introductions or conclusions. Keep it strictly focused on these four points.
        """.trimIndent()

        val systemInstruction = "You are an expert atmospheric health, personal fitness trainer, and laundry care consultant. Keep advice incredibly brief, highly scannable, using clear bullet points with relevant bold text."

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
            generationConfig = GeminiGenerationConfig(temperature = 0.6f)
        )

        return try {
            val response = api.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Advisory currently unavailable. Please check back later."
        } catch (e: Exception) {
            "Unable to sync with live AI advisory. (${e.localizedMessage ?: "Network error"})."
        }
    }
}
