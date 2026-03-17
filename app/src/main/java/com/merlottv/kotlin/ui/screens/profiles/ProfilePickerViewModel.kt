package com.merlottv.kotlin.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.local.UserProfile
import com.merlottv.kotlin.data.sync.CloudSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilePickerViewModel @Inject constructor(
    private val profileDataStore: ProfileDataStore,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    val profiles: StateFlow<List<UserProfile>> = profileDataStore.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeProfileId: StateFlow<String> = profileDataStore.activeProfileId
        .stateIn(viewModelScope, SharingStarted.Eagerly, "default")

    /** True if a profile was already selected (auto-redirect to Home) */
    private val _hasExistingProfile = MutableStateFlow<Boolean?>(null)
    val hasExistingProfile: StateFlow<Boolean?> = _hasExistingProfile.asStateFlow()

    init {
        viewModelScope.launch {
            _hasExistingProfile.value = profileDataStore.hasSelectedProfile()
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileDataStore.setActiveProfile(profileId)
            cloudSyncManager.notifyProfilesChanged()
        }
    }

    fun createAndSelectProfile(name: String, colorIndex: Int, avatarUrl: String = "", onCreated: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val profile = profileDataStore.addProfile(name, colorIndex, avatarUrl)
                profileDataStore.setActiveProfile(profile.id)
                cloudSyncManager.notifyProfilesChanged()
                onCreated(profile.id)
            } catch (_: Exception) {}
        }
    }
}
