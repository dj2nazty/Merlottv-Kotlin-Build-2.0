package com.merlottv.kotlin.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.domain.model.WeatherAlert
import com.merlottv.kotlin.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Global ViewModel for NWS weather alerts.
 * Polls every 5 minutes for active alerts using the user's saved ZIP code.
 * Used by the global WeatherAlertTicker overlay on Live TV and VOD screens.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _activeAlerts = MutableStateFlow<List<WeatherAlert>>(emptyList())
    val activeAlerts: StateFlow<List<WeatherAlert>> = _activeAlerts.asStateFlow()

    private val _showBanner = MutableStateFlow(false)
    val showBanner: StateFlow<Boolean> = _showBanner.asStateFlow()

    // Track if user manually dismissed the banner (resets on new alerts)
    private var dismissedAlertIds = setOf<String>()

    init {
        // Start polling for alerts
        viewModelScope.launch {
            while (true) {
                fetchAlerts()
                delay(5 * 60 * 1000L) // Poll every 5 minutes
            }
        }
    }

    fun dismissBanner() {
        dismissedAlertIds = _activeAlerts.value.map { it.id }.toSet()
        _showBanner.value = false
    }

    private suspend fun fetchAlerts() {
        try {
            val zip = settingsDataStore.weatherZipCode.first()
            val coords = weatherRepository.getCoordinatesForZip(zip)

            if (coords != null) {
                val alerts = weatherRepository.getActiveAlerts(coords.first, coords.second)
                _activeAlerts.value = alerts

                // Show banner if there are new alerts (not previously dismissed)
                val newAlertIds = alerts.map { it.id }.toSet()
                val hasNewAlerts = newAlertIds.any { it !in dismissedAlertIds }
                _showBanner.value = alerts.isNotEmpty() && hasNewAlerts

                Log.d("AlertsVM", "Fetched ${alerts.size} alerts for ZIP $zip (show=${ _showBanner.value})")
            }
        } catch (e: Exception) {
            Log.e("AlertsVM", "Failed to fetch alerts", e)
        }
    }
}
