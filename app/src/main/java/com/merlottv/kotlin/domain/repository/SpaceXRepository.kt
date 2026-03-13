package com.merlottv.kotlin.domain.repository

import com.merlottv.kotlin.domain.model.SpaceXLaunch

interface SpaceXRepository {
    suspend fun getUpcomingLaunches(): List<SpaceXLaunch>
    suspend fun getPastLaunches(limit: Int = 10): List<SpaceXLaunch>
}
