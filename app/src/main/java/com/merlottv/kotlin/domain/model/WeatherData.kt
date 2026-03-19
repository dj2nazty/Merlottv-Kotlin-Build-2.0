package com.merlottv.kotlin.domain.model

data class CurrentWeather(
    val tempF: Double,
    val tempC: Double,
    val feelsLikeF: Double,
    val feelsLikeC: Double,
    val condition: String,
    val conditionIconUrl: String,
    val windMph: Double,
    val windGustMph: Double = 0.0,
    val windDir: String,
    val windDegree: Int = 0,
    val humidity: Int,
    val dewPointF: Double = 0.0,
    val pressureIn: Double,
    val uvIndex: Double,
    val visibilityMiles: Double,
    val cloudCover: Int = 0,
    val locationName: String,
    val region: String,
    val localTime: String,
    val isDay: Boolean,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    // Air Quality
    val airQuality: AirQuality? = null
)

data class AirQuality(
    val usEpaIndex: Int = 0,     // 1=Good, 2=Moderate, 3=Unhealthy(sensitive), 4=Unhealthy, 5=Very Unhealthy, 6=Hazardous
    val pm25: Double = 0.0,
    val pm10: Double = 0.0,
    val co: Double = 0.0,        // Carbon monoxide μg/m3
    val no2: Double = 0.0,       // Nitrogen dioxide μg/m3
    val o3: Double = 0.0,        // Ozone μg/m3
    val so2: Double = 0.0        // Sulphur dioxide μg/m3
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
    val moonrise: String = "",
    val moonset: String = "",
    val moonPhase: String = "",           // "Waxing Crescent", "Full Moon", etc.
    val moonIllumination: Int = 0,        // 0-100
    val uvIndex: Double = 0.0,
    val avgVisibilityMiles: Double = 0.0,
    val totalPrecipIn: Double = 0.0,
    val avgTempF: Double = 0.0,
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
    val windGustMph: Double = 0.0,
    val windDir: String,
    val windDegree: Int = 0,
    val humidity: Int,
    val chanceOfRain: Int,
    val chanceOfSnow: Int,
    val isDay: Boolean,
    val dewPointF: Double = 0.0,
    val pressureIn: Double = 0.0,
    val cloudCover: Int = 0,
    val uvIndex: Double = 0.0
)

data class RadarFrame(
    val path: String,
    val time: Long,
    val host: String
)

data class WeatherAlert(
    val id: String,
    val event: String,
    val headline: String,
    val description: String,
    val severity: String,
    val urgency: String,
    val senderName: String,
    val areaDesc: String,
    val onset: String,
    val expires: String,
    val instruction: String?
)

data class MarineData(
    val waveHeightFt: Double = 0.0,
    val wavePeriodSec: Double = 0.0,
    val waveDirectionDeg: Int = 0,
    val waterTempF: Double = 0.0,
    val swellHeightFt: Double = 0.0,
    val swellPeriodSec: Double = 0.0,
    val swellDirectionDeg: Int = 0,
    val windWaveHeightFt: Double = 0.0
)
