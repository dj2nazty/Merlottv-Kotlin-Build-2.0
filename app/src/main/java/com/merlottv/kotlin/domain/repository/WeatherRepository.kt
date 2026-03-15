package com.merlottv.kotlin.domain.repository

import com.merlottv.kotlin.domain.model.CurrentWeather
import com.merlottv.kotlin.domain.model.DayForecast
import com.merlottv.kotlin.domain.model.RadarFrame

interface WeatherRepository {
    suspend fun getCurrentAndForecast(zipCode: String): Pair<CurrentWeather, List<DayForecast>>?
    suspend fun getRadarFrames(): List<RadarFrame>
}
