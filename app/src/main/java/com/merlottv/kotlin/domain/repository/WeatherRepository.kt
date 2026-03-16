package com.merlottv.kotlin.domain.repository

import com.merlottv.kotlin.domain.model.CurrentWeather
import com.merlottv.kotlin.domain.model.DayForecast
import com.merlottv.kotlin.domain.model.RadarFrame
import com.merlottv.kotlin.domain.model.WeatherAlert

interface WeatherRepository {
    suspend fun getCurrentAndForecast(zipCode: String): Pair<CurrentWeather, List<DayForecast>>?
    suspend fun getRadarFrames(): List<RadarFrame>
    suspend fun getActiveAlerts(lat: Double, lon: Double): List<WeatherAlert>
    suspend fun getCoordinatesForZip(zipCode: String): Pair<Double, Double>?
}
