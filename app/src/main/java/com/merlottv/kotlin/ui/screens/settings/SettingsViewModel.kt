package com.merlottv.kotlin.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.SettingsDataStore
import com.merlottv.kotlin.domain.model.Addon
import com.merlottv.kotlin.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val playlistUrl: String = "",
    val torboxKey: String = "",
    val addons: List<Addon> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val addonRepository: AddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val playlist = settingsDataStore.playlistUrl.first()
            val torbox = settingsDataStore.torboxKey.first()
            val addons = addonRepository.getAllAddons().first()
            _uiState.value = SettingsUiState(
                playlistUrl = playlist,
                torboxKey = torbox,
                addons = addons
            )
        }
    }

    fun savePlaylistUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setPlaylistUrl(url)
            _uiState.value = _uiState.value.copy(playlistUrl = url)
        }
    }

    fun saveTorboxKey(key: String) {
        viewModelScope.launch {
            settingsDataStore.setTorboxKey(key)
            _uiState.value = _uiState.value.copy(torboxKey = key)
        }
    }

    fun addAddon(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            addonRepository.addAddon(url)
            val addons = addonRepository.getAllAddons().first()
            _uiState.value = _uiState.value.copy(addons = addons)
        }
    }
}
