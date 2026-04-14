package com.moveinsight.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.domain.auth.CheckAuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthCheckState {
    data object Checking       : AuthCheckState()
    data object Authenticated  : AuthCheckState()
    data object Unauthenticated: AuthCheckState()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val checkAuthUseCase: CheckAuthUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AuthCheckState>(AuthCheckState.Checking)
    val state: StateFlow<AuthCheckState> = _state.asStateFlow()

    init { checkAuth() }

    private fun checkAuth() {
        viewModelScope.launch {
            delay(700L) // Tiempo mínimo de splash para percepción de marca
            _state.value = if (checkAuthUseCase()) {
                AuthCheckState.Authenticated
            } else {
                AuthCheckState.Unauthenticated
            }
        }
    }
}