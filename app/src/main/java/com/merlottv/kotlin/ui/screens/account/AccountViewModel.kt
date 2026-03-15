package com.merlottv.kotlin.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val error: String? = null,
    val userEmail: String? = null,
    val deviceCode: String? = null,
    val deviceCodeExpiresIn: Int = 600,
    val linkedEmail: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deviceCodeRepository: DeviceCodeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private var deviceCodeJob: Job? = null
    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { state ->
                    if (user != null) {
                        cancelDeviceCodeFlow()
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

                deviceCodeRepository.observeCodeStatus(code).collect { status ->
                    when (status) {
                        is DeviceCodeStatus.Linked -> {
                            countdownJob?.cancel()
                            deviceCodeRepository.deleteCode(code)
                            if (status.password.isNotBlank()) {
                                // Auto sign-in: web page provided credentials
                                _uiState.update {
                                    it.copy(isLoading = true, error = null, deviceCode = null)
                                }
                                authRepository.signInWithEmail(status.email.trim(), status.password)
                                    .onFailure { e ->
                                        _uiState.update {
                                            it.copy(
                                                mode = AccountMode.DEVICE_CODE_PASSWORD,
                                                linkedEmail = status.email,
                                                email = status.email,
                                                password = "",
                                                isLoading = false,
                                                error = "Auto sign-in failed: ${formatError(e)}"
                                            )
                                        }
                                    }
                            } else {
                                // Fallback: ask for password on TV
                                _uiState.update {
                                    it.copy(
                                        mode = AccountMode.DEVICE_CODE_PASSWORD,
                                        linkedEmail = status.email,
                                        email = status.email,
                                        password = "",
                                        error = null
                                    )
                                }
                            }
                        }
                        is DeviceCodeStatus.Expired -> {
                            countdownJob?.cancel()
                            _uiState.update { it.copy(mode = AccountMode.SIGNED_OUT, error = "Code expired", deviceCode = null) }
                        }
                        is DeviceCodeStatus.Error -> {
                            countdownJob?.cancel()
                            _uiState.update { it.copy(error = status.message) }
                        }
                        DeviceCodeStatus.Pending -> { /* waiting */ }
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
            authRepository.signOut()
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
