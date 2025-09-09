package com.example.gamified.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamified.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    sealed class AuthenticationState {
        object AUTHENTICATED : AuthenticationState()
        object UNAUTHENTICATED : AuthenticationState()
        object AUTHENTICATING : AuthenticationState()
    }

    private val _authenticationState =
        MutableStateFlow<AuthenticationState>(AuthenticationState.AUTHENTICATING)
    val authenticationState: StateFlow<AuthenticationState> = _authenticationState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow<Boolean?>(null)
    val isAuthenticated: StateFlow<Boolean?> = _isAuthenticated

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            _authenticationState.value = AuthenticationState.AUTHENTICATING
            try {
                val isAuthenticated = authRepository.isUserAuthenticated()
                _isAuthenticated.value = isAuthenticated
                _authenticationState.value = if (isAuthenticated) {
                    AuthenticationState.AUTHENTICATED
                } else {
                    AuthenticationState.UNAUTHENTICATED
                }
            } catch (e: Exception) {
                _authenticationState.value = AuthenticationState.UNAUTHENTICATED
            }
        }
    }

    // Add your authentication methods here
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authenticationState.value = AuthenticationState.AUTHENTICATING
            try {
                // Implement actual login logic here
                // val result = authRepository.login(email, password)
                _isAuthenticated.value = true
                _authenticationState.value = AuthenticationState.AUTHENTICATED
            } catch (e: Exception) {
                _authenticationState.value = AuthenticationState.UNAUTHENTICATED
                throw e
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                // authRepository.logout()
                _isAuthenticated.value = false
                _authenticationState.value = AuthenticationState.UNAUTHENTICATED
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
