package com.merlottv.kotlin.ui.screens.account

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.data.sync.CloudSyncManager
import com.merlottv.kotlin.domain.repository.AuthRepository
import com.merlottv.kotlin.domain.repository.DeviceCodeRepository
import com.merlottv.kotlin.domain.repository.DeviceCodeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccountMode {
    SIGNED_OUT,
    SIGN_IN,
    SIGN_UP,
    DEVICE_CODE,
    DEVICE_CODE_PASSWORD,
    SIGNED_IN
}

data class AccountUiState(
    val mode: AccountMode = AccountMode.SIGNED_OUT,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val userEmail: String? = null,
    val deviceCode: String? = null,
    val deviceCodeExpiresIn: Int = 600,
    val linkedEmail: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deviceCodeRepository: DeviceCodeRepository,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private var deviceCodeJob: Job? = null
    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                Log.d("MerlotTV", "Auth state changed: user=${user?.email ?: "null"}")
                _uiState.update { state ->
                    if (user != null) {
                        cancelDeviceCodeFlow()
                        // Cloud sync: download all data on sign-in
                        triggerCloudDownload()
                        state.copy(
                            mode = AccountMode.SIGNED_IN,
                            userEmail = user.email,
                            isLoading = false,
                            error = null,
                            password = "",
                            confirmPassword = "",
                            deviceCode = null,
                            linkedEmail = null
                        )
                    } else {
                        // Stop real-time sync on sign-out
                        cloudSyncManager.stopRealtimeSync()
                        state.copy(
                            mode = AccountMode.SIGNED_OUT,
                            userEmail = null
                        )
                    }
                }
            }
        }
    }

    fun showSignIn() {
        _uiState.update { it.copy(mode = AccountMode.SIGN_IN, error = null, email = "", password = "") }
    }

    fun showSignUp() {
        _uiState.update { it.copy(mode = AccountMode.SIGN_UP, error = null, email = "", password = "", confirmPassword = "") }
    }

    fun startDeviceCodeFlow() {
        cancelDeviceCodeFlow()
        _uiState.update { it.copy(mode = AccountMode.DEVICE_CODE, error = null, isLoading = true, deviceCode = null, deviceCodeExpiresIn = 600) }

        deviceCodeJob = viewModelScope.launch {
            try {
                val code = deviceCodeRepository.generateCode()
                _uiState.update { it.copy(deviceCode = code, isLoading = false) }

                countdownJob = launch {
                    var remaining = 600
                    while (remaining > 0) {
                        _uiState.update { it.copy(deviceCodeExpiresIn = remaining) }
                        delay(1000)
                        remaining--
                    }
                    _uiState.update { it.copy(mode = AccountMode.SIGNED_OUT, error = "Code expired", deviceCode = null) }
                    deviceCodeRepository.deleteCode(code)
                }

                var linked = false
                deviceCodeRepository.observeCodeStatus(code).collect { status ->
                    if (linked) return@collect // Ignore emissions after link (delete triggers Expired)
                    when (status) {
                        is DeviceCodeStatus.Linked -> {
                            linked = true
                            countdownJob?.cancel()
                            val linkedEmail = status.email
                            val linkedPassword = status.password
                            Log.d("MerlotTV", "Device code linked! email=$linkedEmail, hasPassword=${linkedPassword.isNotBlank()}")
                            // Delete code first (cleans up Firestore), then sign in
                            deviceCodeRepository.deleteCode(code)
                            if (linkedPassword.isNotBlank()) {
                                // Auto sign-in: web page provided credentials
                                Log.d("MerlotTV", "Attempting auto sign-in for $linkedEmail")
                                _uiState.update {
                                    it.copy(isLoading = true, error = null, deviceCode = null)
                                }
                                authRepository.signInWithEmail(linkedEmail.trim(), linkedPassword)
                                    .onSuccess { user ->
                                        Log.d("MerlotTV", "Auto sign-in SUCCESS for ${user.email}")
                                        // AuthStateListener in init{} will handle mode transition to SIGNED_IN
                                    }
                                    .onFailure { e ->
                                        Log.e("MerlotTV", "Auto sign-in FAILED: ${e.message}", e)
                                        _uiState.update {
                                            it.copy(
                                                mode = AccountMode.DEVICE_CODE_PASSWORD,
                                                linkedEmail = linkedEmail,
                                                email = linkedEmail,
                                                password = "",
                                                isLoading = false,
                                                error = "Auto sign-in failed: ${formatError(e)}"
                                            )
                                        }
                                    }
                            } else {
                                Log.d("MerlotTV", "No password provided, falling back to manual entry")
                                // Fallback: ask for password on TV
                                _uiState.update {
                                    it.copy(
                                        mode = AccountMode.DEVICE_CODE_PASSWORD,
                                        linkedEmail = linkedEmail,
                                        email = linkedEmail,
                                        password = "",
                                        error = null
                                    )
                                }
                            }
                        }
                        is DeviceCodeStatus.Expired -> {
                            Log.d("MerlotTV", "Device code expired")
                            countdownJob?.cancel()
                            _uiState.update { it.copy(mode = AccountMode.SIGNED_OUT, error = "Code expired", deviceCode = null) }
                        }
                        is DeviceCodeStatus.Error -> {
                            Log.e("MerlotTV", "Device code error: ${status.message}")
                            countdownJob?.cancel()
                            _uiState.update { it.copy(error = status.message) }
                        }
                        DeviceCodeStatus.Pending -> {
                            Log.d("MerlotTV", "Device code pending, waiting for link...")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to generate code: ${e.message}") }
            }
        }
    }

    private fun cancelDeviceCodeFlow() {
        deviceCodeJob?.cancel()
        countdownJob?.cancel()
        deviceCodeJob = null
        countdownJob = null
    }

    fun goBack() {
        cancelDeviceCodeFlow()
        val currentCode = _uiState.value.deviceCode
        if (currentCode != null) {
            viewModelScope.launch { deviceCodeRepository.deleteCode(currentCode) }
        }
        _uiState.update { it.copy(mode = AccountMode.SIGNED_OUT, error = null, email = "", password = "", confirmPassword = "", deviceCode = null, linkedEmail = null) }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value, error = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun updateConfirmPassword(value: String) {
        _uiState.update { it.copy(confirmPassword = value, error = null) }
    }

    fun signIn() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password are required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signInWithEmail(state.email.trim(), state.password)
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = formatError(e)) }
                }
        }
    }

    fun signInWithLinkedEmail() {
        val state = _uiState.value
        val email = state.linkedEmail ?: state.email
        if (email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Password is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signInWithEmail(email.trim(), state.password)
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = formatError(e)) }
                }
        }
    }

    fun signUp() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password are required") }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(error = "Passwords do not match") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signUpWithEmail(state.email.trim(), state.password)
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = formatError(e)) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            cloudSyncManager.stopRealtimeSync()
            authRepository.signOut()
        }
    }

    fun syncNow() {
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            try {
                cloudSyncManager.uploadAll()
            } finally {
                // Small delay so user sees the indicator
                delay(1000)
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun triggerCloudDownload() {
        viewModelScope.launch {
            try {
                cloudSyncManager.downloadAll()
                cloudSyncManager.startRealtimeSync()
            } catch (e: Exception) {
                Log.e("MerlotTV", "Cloud sync failed: ${e.message}", e)
            }
        }
    }

    private fun formatError(e: Throwable): String {
        val msg = e.message ?: "Unknown error"
        return when {
            "INVALID_LOGIN_CREDENTIALS" in msg || "INVALID_EMAIL" in msg ->
                "Invalid email or password"
            "EMAIL_EXISTS" in msg || "email address is already in use" in msg ->
                "An account with this email already exists"
            "WEAK_PASSWORD" in msg ->
                "Password is too weak (minimum 6 characters)"
            "network" in msg.lowercase() ->
                "Network error — check your internet connection"
            "TOO_MANY_ATTEMPTS" in msg ->
                "Too many attempts — try again later"
            else -> msg
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelDeviceCodeFlow()
    }
}
