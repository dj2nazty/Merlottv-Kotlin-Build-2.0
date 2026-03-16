package com.merlottv.kotlin.domain.model

data class CurrentWeather(
    val tempF: Double,
    val tempC: Double,
    val feelsLikeF: Double,
    val feelsLikeC: Double,
    val condition: String,
    val conditionIconUrl: String,
    val windMph: Double,
    val windDir: String,
    val humidity: Int,
    val pressureIn: Double,
    val uvIndex: Double,
    val visibilityMiles: Double,
    val locationName: String,
    val region: String,
    val localTime: String,
    val isDay: Boolean,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

data class DayForecast(
    val date: String,
    val dayOfWeek: String,
    val maxTempF: Double,
    val minTempF: Double,
    val condition: String,
    val conditionIconUrl: String,
    val chanceOfRain: Int,
    val chanceOfSnow: Int,
    val avgHumidity: Int,
    val maxWindMph: Double,
    val sunrise: String = "",
    val sunset: String = "",
    val uvIndex: Double = 0.0,
    val avgVisibilityMiles: Double = 0.0,
    val hourly: List<HourForecast> = emptyList()
)

data class HourForecast(
    val time: String,          // "2026-03-15 14:00"
    val displayTime: String,   // "2 PM"
    val tempF: Double,
    val feelsLikeF: Double,
    val condition: String,
    val conditionIconUrl: String,
    val windMph: Double,
    val windDir: String,
    val humidity: Int,
    val chanceOfRain: Int,
    val chanceOfSnow: Int,
    val isDay: Boolean
)

data class RadarFrame(
    val path: String,
    val time: Long,
    val host: String
)

data class WeatherAlert(
    val id: String,
    val event: String,           // "Tornado Warning", "Wind Advisory"
    val headline: String,        // Full headline text
    val description: String,     // Detailed description
    val severity: String,        // "Extreme", "Severe", "Moderate", "Minor", "Unknown"
    val urgency: String,         // "Immediate", "Expected", "Future", "Unknown"
    val senderName: String,      // "NWS Cleveland OH"
    val areaDesc: String,        // Affected counties/zones
    val onset: String,           // ISO timestamp — when conditions begin
    val expires: String,         // ISO timestamp — when alert expires
    val instruction: String?     // Recommended actions (may be null)
)
