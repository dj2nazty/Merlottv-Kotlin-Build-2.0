package com.merlottv.kotlin.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merlottv.kotlin.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    SIGNED_IN
}

data class AccountUiState(
    val mode: AccountMode = AccountMode.SIGNED_OUT,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val userEmail: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { state ->
                    if (user != null) {
                        state.copy(
                            mode = AccountMode.SIGNED_IN,
                            userEmail = user.email,
                            isLoading = false,
                            error = null,
                            password = "",
                            confirmPassword = ""
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

    fun showDeviceCode() {
        _uiState.update { it.copy(mode = AccountMode.DEVICE_CODE, error = null) }
    }

    fun goBack() {
        _uiState.update { it.copy(mode = AccountMode.SIGNED_OUT, error = null, email = "", password = "", confirmPassword = "") }
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
}
