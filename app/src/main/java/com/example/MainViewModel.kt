package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApiClient
import com.example.data.LocationHelper
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Presets for Fabric Wetness Multiplier
enum class FabricPreset(val displayName: String, val multiplier: Double) {
    THIN_SHEETS("Thin Sheets (0.5x)", 0.5),
    STANDARD_TOWELS("Standard Cotton (1.0x)", 1.0),
    THICK_FLEECE("Thick Fleece/Hoodie (1.8x)", 1.8)
}

// State classes for the UI
sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(
        val locationName: String,
        val latitude: Double,
        val longitude: Double,
        val currentTemp: Double,
        val currentRh: Double,
        val currentWind: Double,
        val currentPrecip: Double,
        val weatherCode: Int,
        val currentAqi: Int,
        val currentPm25: Double,
        val currentPm10: Double,
        val currentO3: Double,
        val currentNo2: Double,
        val currentSo2: Double,
        val currentCo: Double,
        val aqiLabel: String,
        val aqiRating: AqiRating,
        
        // Settings
        val selectedPreset: FabricPreset,
        val isIndoorDrying: Boolean,
        
        // Drying physical outputs
        val dryingIndex: Double,
        val dryingScore: Int,
        val dryingStateLabel: String,
        val estimatedDryingHours: Double,
        val bestWindow12h: String,
        val finishPredictionLabel: String,
        val finishNote: String,
        val nextRainTime: String,
        val nextRainAmt: String,
        
        // Series for Charting (Next 12 Hours)
        val next12HoursDrying: List<HourlyDryingPoint>,
        
        // 7-day drying outlook
        val weeklyOutlook: List<DailyOutlookPoint>,
        
        // Recommendations
        val maskRecommendation: String,
        val activityRecommendation: String,
        val apparelRecommendation: String,
        
        // Gemini AI Smart Stylist Suggestion
        val aiStylistSuggestion: String,
        val isAiLoading: Boolean
    ) : UiState
    data class Error(val message: String) : UiState
}

// Data point for next 12-hours timeline
data class HourlyDryingPoint(
    val timeLabel: String,
    val dryingScore: Int,
    val precipitation: Double,
    val temp: Double,
    val rh: Double
)

// Data point for 7-day outlook list
data class DailyOutlookPoint(
    val dayLabel: String,
    val tempMax: Double,
    val tempMin: Double,
    val maxAqi: Int,
    val aqiRating: AqiRating,
    val avgDryingScore: Int,
    val dryingStatus: String,
    val summary: String
)

// AQI Color definitions & Advice categories
enum class AqiRating(val title: String, val colorHex: Long, val severity: String) {
    GOOD("Good", 0xFF4CAF50L, "Minimal impact"),
    MODERATE("Moderate", 0xFFFFEB3BL, "Acceptable air, some risk for sensitive people"),
    SENSITIVE_UNHEALTHY("USG (Caution)", 0xFFFF9800L, "Sensitive groups should limit exertion"),
    UNHEALTHY("Unhealthy", 0xFFF44336L, "Everyone begins to experience health effects"),
    VERY_UNHEALTHY("Very Unhealthy", 0xFF9C27B0L, "Health alert: more serious effects for all"),
    HAZARDOUS("Hazardous", 0xFF7A1C1CL, "Health warnings of emergency conditions")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResult>> = _searchResults.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    // Default Fallback coordinates: New Delhi, India
    private val defaultLat = 28.6139
    private val defaultLon = 77.2090
    private val defaultLocName = "New Delhi, India"

    // Backing config selection properties
    private var currentPreset = FabricPreset.STANDARD_TOWELS
    private var isIndoor = false

    // Backing coordinates
    private var currentLatitude = defaultLat
    private var currentLongitude = defaultLon
    private var currentPlaceName = defaultLocName

    init {
        // Initial setup - default New Delhi or prompt location
        loadAdvisorData(defaultLat, defaultLon, defaultLocName)
    }

    // Auto-detect location from the phone using safe permission check
    fun detectAndLoadCurrentLocation(context: Context) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val location = LocationHelper.getCurrentLocation(context)
            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                currentPlaceName = "Detected Location"
                
                // Do reverse geocoding to find a tidy city name
                val name = fetchCityNameFromCoords(location.latitude, location.longitude)
                currentPlaceName = name ?: "Current GPS Location"
                
                loadAdvisorData(currentLatitude, currentLongitude, currentPlaceName)
            } else {
                // If it fails or permissions are not set, fall back to our default
                loadAdvisorData(defaultLat, defaultLon, "$defaultLocName (Location Error)")
            }
        }
    }

    // Manual load of a location
    fun loadAdvisorData(lat: Double, lon: Double, name: String) {
        currentLatitude = lat
        currentLongitude = lon
        currentPlaceName = name
        
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                // Fetch concurrently from open-meteo
                val weatherDeferred = withContext(Dispatchers.IO) {
                    ApiClient.apiService.getWeatherData(lat, lon)
                }
                val aqiDeferred = withContext(Dispatchers.IO) {
                    ApiClient.apiService.getAirQualityData(lat, lon)
                }
                
                processAndEmitSuccessState(weatherDeferred, aqiDeferred)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching API data", e)
                _uiState.value = UiState.Error("Failed to fetch weather/AQI reports: ${e.localizedMessage ?: "Network Timeout"}")
            }
        }
    }

    // Search locations manually
    fun searchCities(query: String) {
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            return
        }
        _searchLoading.value = true
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    ApiClient.apiService.searchLocation(query)
                }
                _searchResults.value = results.results ?: emptyList()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Search query failed", e)
            } finally {
                _searchLoading.value = false
            }
        }
    }

    fun cleanSearchResults() {
        _searchResults.value = emptyList()
    }

    // Dynamic config updates
    fun updateFabricPreset(preset: FabricPreset) {
        currentPreset = preset
        refreshModelCalculationsOnCurrentData()
    }

    fun toggleIndoorDrying(indoor: Boolean) {
        isIndoor = indoor
        refreshModelCalculationsOnCurrentData()
    }

    private fun refreshModelCalculationsOnCurrentData() {
        val currentUi = _uiState.value
        if (currentUi is UiState.Success) {
            _uiState.value = UiState.Loading
            loadAdvisorData(currentLatitude, currentLongitude, currentPlaceName)
        }
    }

    // Reverse geocodes the coordinates to extract a descriptive city name
    private suspend fun fetchCityNameFromCoords(lat: Double, lon: Double): String? {
        return try {
            val results = withContext(Dispatchers.IO) {
                // Look up by nearby standard coordinates using search
                ApiClient.apiService.searchLocation("$lat,$lon")
            }
            results.results?.firstOrNull()?.name?.plus(", ${results.results.firstOrNull()?.country ?: ""}")
        } catch (e: Exception) {
            null
        }
    }

    // Process open-meteo response datasets to compute AQI levels and Laundry Physics
    private fun processAndEmitSuccessState(
        weather: WeatherResponse,
        airQuality: AirQualityResponse
    ) {
        val currWeather = weather.current ?: throw Exception("Current weather details blank")
        val hourlyWeather = weather.hourly ?: throw Exception("Hourly weather details blank")
        
        // Pull current AQI info
        val currAqiRaw = airQuality.current?.usAqi ?: 0.0
        val currentAqi = currAqiRaw.roundToInt()
        
        val pm25 = airQuality.current?.pm25 ?: 0.0
        val pm10 = airQuality.current?.pm10 ?: 0.0
        val o3 = airQuality.current?.ozone ?: 0.0
        val no2 = airQuality.current?.nitrogenDioxide ?: 0.0
        val so2 = airQuality.current?.sulphurDioxide ?: 0.0
        val co = airQuality.current?.carbonMonoxide ?: 0.0

        val (rating, label) = parseAqiRating(currentAqi)

        // Process Recommendations
        val maskRecommendation = when {
            currentAqi > 300 -> "🔴 AVOID GOING OUTDOORS ENTIRELY. High particulate load alert."
            currentAqi > 200 -> "⚠️ N95/N99 respirator mask is MANDATORY for all outdoor actions."
            currentAqi > 150 -> "😷 N95 filter mask highly advised; limit outdoor activities."
            currentAqi > 100 -> "😷 Standard face/cloth mask advised for highly sensitive individuals."
            else -> "✨ Air is safe. No mask required."
        }

        val activityRecommendation = when {
            currentAqi > 200 -> "❌ Strictly restrict outdoor tasks. Active sports, jogging, or biking will cause breathing irritation."
            currentAqi > 150 -> "⚠️ Minimize outdoor exposure; move exercises indoors. Close home windows and activate air purifiers."
            currentAqi > 100 -> "🏃 Sensitive groups should avoid strenuous physical activity. Keep windows shut."
            currentAqi > 50 -> "🏃 Okay (Moderate). Extremely sensitive persons should monitor symptoms."
            else -> "🌳 Perfect conditions for running, long walks, outdoor cycling, and general park exercises."
        }

        // Apparel Recommendation based on TEMPERATURE + AQI protection
        val temp = currWeather.temperature2m
        val isRaining = currWeather.precipitation > 0.0

        val apparelRecommendation = buildString {
            // Temperature segment
            when {
                temp < 10 -> append("🧥 Cold weather: Wrap up securely! Wear thermals, thick woolen sweaters, jacket, and beanies.")
                temp < 18 -> append("🧣 Moderate cool: Long-sleeve shirt, fleece hoodie, jeans, or windbreaker.")
                temp < 28 -> append("👕 Pleasant warmth: Light layer, casual cotton t-shirt, jeans, or cargo pants.")
                else -> append("☀️ Hot climate: Wear breathable, lightweight clothing (pure cotton shorts, loose tees).")
            }
            
            // Rain segment
            if (isRaining) {
                append(" ☔ Carry a comprehensive umbrella/waterproof windproof shell raincoat, non-slip footwear.")
            }
            
            // AQI protection segment (if AQI is dangerously bad, advise covering skin to prevent dust absorption)
            if (currentAqi > 150) {
                append(" 🛡️ Since particulates are high, cover skin fully (long sleeves, trousers, glasses) to prevent physical dust deposit.")
            }
        }

        // --- Core Laundry drying modeling ---
        // dryingIndex calculations
        val currentTemp = currWeather.temperature2m
        val currentRh = currWeather.relativeHumidity2m
        val currentWind = if (isIndoor) 1.0 else currWeather.windSpeed10m
        val currentPrecip = if (isIndoor) 0.0 else currWeather.precipitation
        
        // Daylight approximation
        val hourFormat = SimpleDateFormat("H", Locale.getDefault())
        val currentHourInt = try {
            hourFormat.format(Date()).toInt()
        } catch (e: Exception) {
            12
        }
        val isDayNow = currentHourInt in 6..18

        val (currentDI, rawBaseDI, vpd, pFactor) = calculateDryingIndexValue(
            currentTemp, currentRh, currentWind, currentPrecip, isDayNow
        )
        
        // Drying Score percentage (0% to 100%)
        val s = (currentDI - 0.2) / (2.0 - 0.2)
        val dryingScore = (s.coerceIn(0.0, 1.0) * 100).roundToInt()
        
        val dryingStateLabel = when {
            isRaining -> "Rain/Precipitation Active - Drying Ruined"
            dryingScore > 85 -> "Excellent Fast Drying (Generous Sun & Breeze)"
            dryingScore > 65 -> "Good Drying Power"
            dryingScore > 40 -> "Moderate/Slow Drying (Damp Air)"
            dryingScore > 15 -> "Very Slow/Damp"
            else -> "No Drying (Saturated Air or Active Rain)"
        }

        // Current direct estimate
        val baseHours = 5.0 * currentPreset.multiplier
        val estimatedDryingHours = baseHours / currentDI.coerceIn(0.12, 3.2)

        // SERIES COLLECTION: Next 12 Hours
        val hourlyTimes = hourlyWeather.time
        val hourlyTemps = hourlyWeather.temperature2m
        val hourlyRhs = hourlyWeather.relativeHumidity2m
        val hourlyWinds = hourlyWeather.windSpeed10m
        val hourlyPrecips = hourlyWeather.precipitation
        
        // Find close matching starting index (default index 0)
        val next12Points = mutableListOf<HourlyDryingPoint>()
        for (i in 0 until 12) {
            if (i > hourlyTemps.lastIndex) break
            
            val hTime = hourlyTimes[i]
            val hTemp = hourlyTemps[i]
            val hRh = hourlyRhs[i]
            val hWind = if (isIndoor) 1.0 else hourlyWinds[i]
            val hPrecip = if (isIndoor) 0.0 else hourlyPrecips[i]
            
            val hourInt = hTime.substringAfter('T').substringBefore(':').toIntOrNull() ?: 12
            val hIsDay = hourInt in 6..18
            
            val (hDI, _, _, _) = calculateDryingIndexValue(hTemp, hRh, hWind, hPrecip, hIsDay)
            val hS = (hDI - 0.2) / (2.0 - 0.2)
            val hScore = (hS.coerceIn(0.0, 1.0) * 100).roundToInt()
            
            // Format hour label "5 PM" or "17:00"
            val label = try {
                val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                val outputFmt = SimpleDateFormat("h a", Locale.getDefault())
                val date = inputFmt.parse(hTime)
                if (date != null) outputFmt.format(date) else hTime.substringAfter('T')
            } catch (e: Exception) {
                hTime.substringAfter('T')
            }

            next12Points.add(HourlyDryingPoint(label, hScore, hPrecip, hTemp, hRh))
        }

        // Physics Forecast-integrated simulation to get target drying finish time!
        val simulationResult = simulateDryingCompletion(
            hourlyTimes,
            hourlyTemps,
            hourlyRhs,
            hourlyWinds,
            hourlyPrecips,
            currentPreset.multiplier,
            isIndoor
        )

        val finishPredictionLabel: String
        val finishNote: String
        if (simulationResult.completed) {
            val totalMinutes = (simulationResult.hours * 60.0).roundToInt()
            val totalHrs = totalMinutes / 60
            val remainingMins = totalMinutes % 60
            
            val c = Calendar.getInstance()
            c.add(Calendar.MINUTE, totalMinutes)
            val timeFmt = SimpleDateFormat("h:mm a (EEEE)", Locale.getDefault())
            
            finishPredictionLabel = if (totalHrs > 0) {
                "${totalHrs}h ${remainingMins}m -> Approx. ${timeFmt.format(c.time)}"
            } else {
                "${remainingMins}m -> Approx. ${timeFmt.format(c.time)}"
            }
            finishNote = "Physical simulation: Clothes reached 100% dry state factoring live evaporation rates & dew parameters."
        } else {
            finishPredictionLabel = "Unable to dry outside (Saturated Forecast)"
            finishNote = "Humidity and rain forecasted to remain high. Recommended to hang laundry indoors."
        }

        // NEXT RAIN EVENT calculation
        var rainTime = "None forecasted"
        var rainAmt = "Keep laundry outside safe"
        for (i in 0..48) {
            if (i > hourlyPrecips.lastIndex) break
            val amount = hourlyPrecips[i]
            if (amount > 0.0) {
                val targetTime = hourlyTimes[i]
                val timeFmtLabel = try {
                    val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                    val outputFmt = SimpleDateFormat("h:00 a (EEEE)", Locale.getDefault())
                    val d = inputFmt.parse(targetTime)
                    if (d != null) outputFmt.format(d) else targetTime
                } catch (e: Exception) {
                    targetTime
                }
                rainTime = timeFmtLabel
                rainAmt = "Precipitation volume: $amount mm/h"
                break
            }
        }

        // BEST WINDOW calculation: 3-hour sliding window over next 12 hours
        var bestWindowName = "-"
        var maxWinSum = -1.0
        for (i in 0..9) {
            if (i + 2 >= next12Points.size) break
            val avgDI = (next12Points[i].dryingScore + next12Points[i+1].dryingScore + next12Points[i+2].dryingScore) / 3.0
            if (avgDI > maxWinSum) {
                maxWinSum = avgDI
                bestWindowName = "${next12Points[i].timeLabel} - ${next12Points[i+2].timeLabel} (${maxWinSum.roundToInt()}% Dry Power)"
            }
        }

        // WEEKLY OUTLOOK calculation (7 days)
        // Group hourly points in groups of 24 to represent days
        val weeklyPoints = mutableListOf<DailyOutlookPoint>()
        for (day in 0..6) {
            val startHr = day * 24
            if (startHr >= hourlyTemps.size) break
            val endHr = (startHr + 23).coerceAtMost(hourlyTemps.lastIndex)
            
            val tempsInDay = hourlyTemps.subList(startHr, endHr + 1)
            val rhInDay = hourlyRhs.subList(startHr, endHr + 1)
            val windsInDay = hourlyWinds.subList(startHr, endHr + 1)
            val precipInDay = hourlyPrecips.subList(startHr, endHr + 1)
            val timesInDay = hourlyTimes.subList(startHr, endHr + 1)
            
            val tMax = tempsInDay.maxOrNull() ?: 0.0
            val tMin = tempsInDay.minOrNull() ?: 0.0
            
            // Calc average daytime DI score (hours 8 AM to 6 PM)
            val diScores = mutableListOf<Int>()
            var dayHasRain = false
            for (hIdx in startHr..endHr) {
                val precipitationAmount = hourlyPrecips[hIdx]
                if (precipitationAmount > 0.0) {
                    dayHasRain = true
                }
                val localTimeStr = hourlyTimes[hIdx]
                val hourInt = localTimeStr.substringAfter('T').substringBefore(':').toIntOrNull() ?: 12
                val isD = hourInt in 8..18
                if (isD) {
                    val (hDI, _, _, _) = calculateDryingIndexValue(
                        hourlyTemps[hIdx],
                        hourlyRhs[hIdx],
                        if (isIndoor) 1.0 else hourlyWinds[hIdx],
                        if (isIndoor) 0.0 else hourlyPrecips[hIdx],
                        isD
                    )
                    val hS = (hDI - 0.2) / (2.0 - 0.2)
                    diScores.add((hS.coerceIn(0.0, 1.0) * 100).roundToInt())
                }
            }
            val avgDryingScore = if (diScores.isNotEmpty()) diScores.average().roundToInt() else 50
            
            val dryingStatus = when {
                dayHasRain && !isIndoor -> "☔ Rain Risk!"
                avgDryingScore > 75 -> "🌟 Perfect"
                avgDryingScore > 50 -> "⛅ Good"
                avgDryingScore > 25 -> "⚠️ Slow"
                else -> "❌ Poor"
            }
            
            val daySummaryNotes = when {
                dayHasRain && !isIndoor -> "Laundry will get wet due to predicted precipitation."
                avgDryingScore > 75 -> "Very dry, warm air & active winds. Hang laundry outdoors."
                avgDryingScore > 50 -> "Favorable drying. Clothes will dry in average timeframe."
                else -> "Heavy chill or humid air. Hang inside configuration with a fan."
            }

            // Estimate daily max AQI using the average AQI forecast (normally hourlyUsAqi is available)
            val dailyUsAqiList = airQuality.hourly?.usAqi ?: emptyList()
            val dayAqiMax = if (dailyUsAqiList.size > endHr) {
                dailyUsAqiList.subList(startHr, endHr + 1).filterNotNull().maxOrNull()?.roundToInt() ?: currentAqi
            } else {
                currentAqi
            }
            val (dayAqiRating, _) = parseAqiRating(dayAqiMax)

            // Day label e.g., "Monday"
            val dayLabel = try {
                val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                val outputFmt = SimpleDateFormat("EEEE", Locale.getDefault())
                val d = inputFmt.parse(timesInDay.firstOrNull() ?: "")
                if (d != null) outputFmt.format(d) else "Day ${day + 1}"
            } catch (e: Exception) {
                "Day ${day + 1}"
            }

            weeklyPoints.add(
                DailyOutlookPoint(
                    dayLabel = dayLabel,
                    tempMax = tMax,
                    tempMin = tMin,
                    maxAqi = dayAqiMax,
                    aqiRating = dayAqiRating,
                    avgDryingScore = avgDryingScore,
                    dryingStatus = dryingStatus,
                    summary = daySummaryNotes
                )
            )
        }

        // Trigger Gemini Stylist query in background asynchronously!
        val lastSuccessState = _uiState.value as? UiState.Success
        val initialSuggestion = lastSuccessState?.aiStylistSuggestion ?: "Stylist is crafting your customized advisory..."
        
        val newState = UiState.Success(
            locationName = currentPlaceName,
            latitude = currentLatitude,
            longitude = currentLongitude,
            currentTemp = currentTemp,
            currentRh = currentRh,
            currentWind = currentWind,
            currentPrecip = currentPrecip,
            weatherCode = currWeather.weatherCode ?: 0,
            currentAqi = currentAqi,
            currentPm25 = pm25,
            currentPm10 = pm10,
            currentO3 = o3,
            currentNo2 = no2,
            currentSo2 = so2,
            currentCo = co,
            aqiLabel = label,
            aqiRating = rating,
            selectedPreset = currentPreset,
            isIndoorDrying = isIndoor,
            dryingIndex = currentDI,
            dryingScore = dryingScore,
            dryingStateLabel = dryingStateLabel,
            estimatedDryingHours = estimatedDryingHours,
            bestWindow12h = bestWindowName,
            finishPredictionLabel = finishPredictionLabel,
            finishNote = finishNote,
            nextRainTime = rainTime,
            nextRainAmt = rainAmt,
            next12HoursDrying = next12Points,
            weeklyOutlook = weeklyPoints,
            maskRecommendation = maskRecommendation,
            activityRecommendation = activityRecommendation,
            apparelRecommendation = apparelRecommendation,
            aiStylistSuggestion = initialSuggestion,
            isAiLoading = true
        )
        
        _uiState.value = newState
        
        // Load Gemini AI Advisor response
        fetchGeminiStylistAdvisory(newState)
    }

    // Direct API call to Gemini using BuildConfig
    private fun fetchGeminiStylistAdvisory(currentState: UiState.Success) {
        viewModelScope.launch {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
                // If API key is blank or placeholder, issue a notice
                updateStateWithAiAdvisory(
                    "✨ Gemini Stylist: Enter your real GEMINI_API_KEY in the Secrets panel of AI Studio to fetch localized weather styling advice tailored specifically to your skin and health safety. Currently using physical rule advisor."
                )
                return@launch
            }

            val prompt = """
                You are "Cosmo Stylist", a professional environmental apparel designer and health protection advisor.
                Based on these actual sensor parameters for ${currentState.locationName}:
                - Temperature: ${currentState.currentTemp}°C
                - Relative Humidity: ${currentState.currentRh}%
                - Wind Speed: ${currentState.currentWind} km/h
                - Active Precipitation: ${currentState.currentPrecip} mm/h
                - US Air Quality Index (AQI): ${currentState.currentAqi} (${currentState.aqiLabel})
                - Drying index score: ${currentState.dryingScore}% for laundry
                - Fabric configuration selected: ${currentState.selectedPreset.displayName}
                
                Generate a highly stylized, engaging 3-sentence health, wardrobe, and laundry styling brief.
                Format rules:
                - Output as a single paragraph.
                - Keep it friendly, objective, extremely professional.
                - Recommend matching garment colors (e.g., neutrals, activewear fits) and particulate precautions.
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(temperature = 0.7f)
            )

            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.generateGeminiContent(apiKey, request)
                }
                val suggestionText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!suggestionText.isNullOrBlank()) {
                    updateStateWithAiAdvisory(suggestionText)
                } else {
                    updateStateWithAiAdvisory("✨ Gemini Stylist is currently unavailable: empty response.")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Gemini call failure", e)
                updateStateWithAiAdvisory("✨ Gemini Stylist: Unable to sync AI suggestion at this time.")
            }
        }
    }

    private fun updateStateWithAiAdvisory(advisory: String) {
        val current = _uiState.value
        if (current is UiState.Success) {
            _uiState.value = current.copy(
                aiStylistSuggestion = advisory,
                isAiLoading = false
            )
        }
    }

    // Core physics calculations based on evap equations
    private fun calculateDryingIndexValue(
        temp: Double,
        rh: Double,
        windKmh: Double,
        precipMm: Double,
        isDay: Boolean
    ): DryIndexComponents {
        // Vapor Pressure Saturated: 0.61078 * exp((17.27 * T) / (T + 237.3)) in kPa
        val saturatedVp = 0.61078 * Math.exp((17.27 * temp) / (temp + 237.3))
        val actualVp = saturatedVp * (rh / 100.0)
        val vpd = (saturatedVp - actualVp).coerceAtLeast(0.0)

        // Wind speed impact multiplier
        val windFactor = 1.0 + (windKmh / 15.0)

        // Solar/sunlight ambient factor
        val sunFactor = if (isDay) 1.4 else 0.6

        val rawBaseDI = vpd * windFactor * sunFactor

        // Precipitation penalty
        val pFactor = if (precipMm > 0.0) {
            Math.max(0.01, Math.exp(-2.0 * precipMm))
        } else {
            1.0
        }

        val finalDI = rawBaseDI * pFactor
        return DryIndexComponents(finalDI, rawBaseDI, vpd, pFactor)
    }

    private data class DryIndexComponents(
        val finalDI: Double,
        val baseDI: Double,
        val vpd: Double,
        val pFactor: Double
    )

    private fun parseAqiRating(aqi: Int): Pair<AqiRating, String> {
        return when {
            aqi <= 50 -> Pair(AqiRating.GOOD, "Good (Safe Air)")
            aqi <= 100 -> Pair(AqiRating.MODERATE, "Moderate (Unhealthy for highly sensitive)")
            aqi <= 150 -> Pair(AqiRating.SENSITIVE_UNHEALTHY, "Unhealthy for Sensitive Groups")
            aqi <= 200 -> Pair(AqiRating.UNHEALTHY, "Unhealthy (High particulate load)")
            aqi <= 300 -> Pair(AqiRating.VERY_UNHEALTHY, "Very Unhealthy (Heavy pollution alert)")
            else -> Pair(AqiRating.HAZARDOUS, "Hazardous Respiratory Hazard!")
        }
    }

    private data class ViewDryingResult(val completed: Boolean, val hours: Double)

    // Simulates laundry state over the next 48 hours to find expected dry-time
    private fun simulateDryingCompletion(
        hourlyTimes: List<String>,
        hourlyTemp: List<Double>,
        hourlyRh: List<Double>,
        hourlyWind: List<Double>,
        hourlyPrecip: List<Double>,
        wetnessMultiplier: Double,
        isIndoor: Boolean
    ): ViewDryingResult {
        val baseHours = 5.0 * wetnessMultiplier
        var dryness = 0.0
        val dt = 10.0 / 60.0 // 10 minutes (0.166h)
        var simulatedSteps = 0
        val maxSteps = 48 * 6 // 48 hours max simulation range

        while (dryness < 1.0 && simulatedSteps < maxSteps) {
            val idx = (simulatedSteps / 6).coerceAtMost(hourlyTemp.lastIndex)
            if (idx < 0) break

            val temp = hourlyTemp[idx]
            val rh = hourlyRh[idx]
            val wind = if (isIndoor) 1.0 else hourlyWind[idx]
            val precip = if (isIndoor) 0.0 else hourlyPrecip[idx]

            val timeStr = hourlyTimes.getOrNull(idx) ?: ""
            val hourOfForecast = timeStr.substringAfter('T').substringBefore(':').toIntOrNull() ?: 12
            val isDay = hourOfForecast in 6..18

            val (currentDI, _, _, _) = calculateDryingIndexValue(temp, rh, wind, precip, isDay)
            val ratePerHour = currentDI.coerceIn(0.12, 3.2) / baseHours
            
            dryness += ratePerHour * dt
            simulatedSteps++
        }

        val totalHoursNeeded = simulatedSteps * dt
        val completed = dryness >= 1.0
        return ViewDryingResult(completed, totalHoursNeeded)
    }
}
