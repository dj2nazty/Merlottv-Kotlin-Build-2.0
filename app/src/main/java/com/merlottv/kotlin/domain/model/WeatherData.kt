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
    val maxWindMph: Double
)

data class RadarFrame(
    val path: String,
    val time: Long,
    val host: String
)
