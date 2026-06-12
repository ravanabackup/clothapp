package com.example.data.model

import kotlin.math.exp
import kotlin.math.max

object LaundryEngine {

    data class DryingSettings(
        val wetness: Double = 1.0,
        val placement: String = "outdoor_open", // "indoor", "outdoor_sheltered", "outdoor_open"
        val sun: String = "direct", // "direct", "shade"
        val air: String = "breeze" // "stagnant", "breeze", "windy"
    )

    data class DryingResult(
        val di: Double,
        val baseDi: Double,
        val vpd: Double,
        val pFactor: Double,
        val score: Int,
        val estHours: Double
    )

    fun calculateDrying(
        temp: Double,
        humidity: Double,
        windSpeedKmh: Double,
        precipitationMm: Double,
        cloudCover: Double,
        isDaytime: Boolean,
        settings: DryingSettings
    ): DryingResult {
        // 1. Vapor Pressure Deficit (VPD) in kPa
        val es = 0.61078 * exp((17.27 * temp) / (temp + 237.3))
        val ea = es * (humidity / 100.0)
        val vpd = (es - ea).coerceAtLeast(0.01)

        // 2. Air Movement factor
        val effectiveWind = when (settings.placement) {
            "indoor" -> {
                when (settings.air) {
                    "stagnant" -> 0.3
                    "breeze" -> 4.0 // simulating a fan on low
                    "windy" -> 10.0 // simulating a fan on high
                    else -> 2.0
                }
            }
            "outdoor_sheltered" -> {
                (windSpeedKmh * 0.45).coerceAtLeast(1.0)
            }
            else -> { // outdoor open
                windSpeedKmh.coerceAtLeast(1.0)
            }
        }
        val windFactor = 1.0 + (effectiveWind / 15.0)

        // 3. Sun and Radiation factor
        var baseDi = vpd * windFactor

        if (isDaytime && settings.placement == "outdoor_open") {
            val sunIntensityMultiplier = if (settings.sun == "direct") 1.0 else 0.35
            val cloudFactor = (1.0 - (cloudCover / 100.0)).coerceIn(0.1, 1.0)
            val solarContribution = 0.45 * sunIntensityMultiplier * cloudFactor
            baseDi += solarContribution
        } else if (isDaytime && settings.placement == "outdoor_sheltered") {
            // some indirect ambient light
            baseDi += 0.10
        }

        // 4. Precipitation Penalty
        // Precipitation acts as a heavy dampener. If precip > 0.1 mm/h, we drop drying power heavily
        val pFactor = if (precipitationMm > 0.05) {
            // severe penalty for rain
            max(0.01, 1.0 - (precipitationMm * 2.5)).coerceIn(0.01, 1.0)
        } else {
            1.0
        }

        // Indoor clothes are shielded from rain, but humidity rises
        val finalPFactor = if (settings.placement == "indoor") 1.0 else pFactor
        val finalDi = baseDi * finalPFactor

        // 5. Estimate Drying Hours
        // Base hours for normal thickness: e.g. 5 hours at a standard DI of 1.0
        val baseHours = 4.8 * settings.wetness
        // finalDI is clamped block to avoid extremes (infinite or instant drying)
        val clampedDi = finalDi.coerceIn(0.10, 3.5)
        val estHours = (baseHours / clampedDi).coerceIn(0.5, 36.0)

        // 6. Score from DI
        // Maps DI from 0.10 to 2.2 linearly towards 100 points
        val s = (finalDi - 0.1) / (2.2 - 0.1)
        val score = (s.coerceIn(0.0, 1.0) * 100).toInt()

        return DryingResult(
            di = finalDi,
            baseDi = baseDi,
            vpd = vpd,
            pFactor = finalPFactor,
            score = score,
            estHours = estHours
        )
    }

    // Integrates finish: iterates hour by hour through the forecast list to estimate exact drying completion time!
    fun integrateFinish(
        startIndex: Int,
        times: List<String>,
        temperatures: List<Double>,
        humidities: List<Double>,
        windSpeeds: List<Double>,
        precips: List<Double>,
        cloudCovers: List<Double>,
        settings: DryingSettings
    ): Double {
        var dryness = 0.0
        val baseHours = 4.8 * settings.wetness
        val dtHours = 0.1667 // check every 10 mins (1/6 hours)
        var hoursTreated = 0.0
        var currentIndex = startIndex

        while (dryness < 1.0 && currentIndex < times.size && hoursTreated < 48.0) {
            val i = currentIndex
            // Simple daylight approximation based on hour slot: assume 6 AM to 6 PM is daytime
            val timeString = times.getOrNull(i) ?: ""
            val isDay = try {
                val hourStr = timeString.substringAfter("T").take(2)
                val hourInt = hourStr.toIntOrNull() ?: 12
                hourInt in 6..18
            } catch (e: Exception) {
                true
            }

            val t = temperatures.getOrNull(i) ?: 25.0
            val rh = humidities.getOrNull(i) ?: 60.0
            val w = windSpeeds.getOrNull(i) ?: 8.0
            val pr = precips.getOrNull(i) ?: 0.0
            val cc = cloudCovers.getOrNull(i) ?: 20.0

            val result = calculateDrying(t, rh, w, pr, cc, isDay, settings)
            val ratePerHour = result.di.coerceIn(0.10, 3.5) / baseHours
            dryness += ratePerHour * dtHours
            hoursTreated += dtHours

            // After every hour of simulation, increment index (6 steps of 10 min = 1 hour)
            if ((hoursTreated * 6.0).toInt() % 6 == 0) {
                currentIndex++
            }
        }
        return hoursTreated
    }
}
