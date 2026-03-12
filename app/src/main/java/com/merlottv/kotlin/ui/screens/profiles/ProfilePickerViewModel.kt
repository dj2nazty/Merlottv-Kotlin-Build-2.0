package com.merlottv.kotlin.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.local.ProfileDataStore
import com.merlottv.kotlin.data.local.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilePickerViewModel @Inject constructor(
    private val profileDataStore: ProfileDataStore
) : ViewModel() {

    val profiles: StateFlow<List<UserProfile>> = profileDataStore.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileDataStore.setActiveProfile(profileId)
        }
    }

    fun createAndSelectProfile(name: String, colorIndex: Int, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val profile = profileDataStore.addProfile(name, colorIndex)
                profileDataStore.setActiveProfile(profile.id)
                onCreated(profile.id)
            } catch (_: Exception) {}
        }
    }
}
