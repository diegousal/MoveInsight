package com.moveinsight.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.auth.CheckAuthUseCase
import com.moveinsight.domain.auth.GetUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthCheckState {
    data object Checking          : AuthCheckState()
    data object Authenticated     : AuthCheckState()
    data object NeedsOnboarding   : AuthCheckState()
    data object Unauthenticated   : AuthCheckState()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val checkAuthUseCase     : CheckAuthUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AuthCheckState>(AuthCheckState.Checking)
    val state: StateFlow<AuthCheckState> = _state.asStateFlow()

    init { checkAuth() }

    private fun checkAuth() {
        viewModelScope.launch {
            delay(700L)
            if (!checkAuthUseCase()) {
                _state.value = AuthCheckState.Unauthenticated
                return@launch
            }
            // Token existe — comprobamos si el onboarding está completado
            when (val profile = getUserProfileUseCase()) {
                is Resource.Success -> {
                    _state.value = if (profile.data.onboardingCompleted)
                        AuthCheckState.Authenticated
                    else
                        AuthCheckState.NeedsOnboarding
                }
                // Error de red → asumimos autenticado para no bloquear el acceso
                else -> _state.value = AuthCheckState.Authenticated
            }
        }
    }
}
