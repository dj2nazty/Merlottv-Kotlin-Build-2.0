package com.merlottv.kotlin.domain.repository

import kotlinx.coroutines.flow.Flow

sealed class DeviceCodeStatus {
    object Pending : DeviceCodeStatus()
    data class Linked(val email: String) : DeviceCodeStatus()
    object Expired : DeviceCodeStatus()
    data class Error(val message: String) : DeviceCodeStatus()
}

interface DeviceCodeRepository {
    suspend fun generateCode(): String
    fun observeCodeStatus(code: String): Flow<DeviceCodeStatus>
    suspend fun deleteCode(code: String)
}
