package com.moveinsight.presentation.auth

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.auth.ResendVerificationUseCase
import com.moveinsight.domain.auth.VerifyEmailUseCase
import com.moveinsight.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerifyEmailFormState(
    val code: String = ""
)

sealed class VerifyEmailUiState {
    data object Idle    : VerifyEmailUiState()
    data object Loading : VerifyEmailUiState()
    data object Success : VerifyEmailUiState()
    data class  Error(val message: String) : VerifyEmailUiState()
}

sealed class VerifyEmailEvent {
    data object NavigateToLogin              : VerifyEmailEvent()
    data class  ShowSnackbar(val msg: String): VerifyEmailEvent()
}

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    savedStateHandle           : SavedStateHandle,
    private val verifyUseCase  : VerifyEmailUseCase,
    private val resendUseCase  : ResendVerificationUseCase,
) : ViewModel() {

    val email: String = Uri.decode(
        savedStateHandle.get<String>(Routes.VerifyEmail.ARG_EMAIL) ?: ""
    )

    private val _form    = MutableStateFlow(VerifyEmailFormState())
    val form: StateFlow<VerifyEmailFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<VerifyEmailUiState>(VerifyEmailUiState.Idle)
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    private val _events  = Channel<VerifyEmailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Cooldown reenvío
    private val _resendCooldown = MutableStateFlow(0)
    val resendCooldown: StateFlow<Int> = _resendCooldown.asStateFlow()
    private var cooldownJob: Job? = null

    fun onCodeChange(v: String) {
        if (v.length <= 6 && v.all { it.isDigit() })
            _form.update { it.copy(code = v) }
        if (_uiState.value is VerifyEmailUiState.Error) _uiState.value = VerifyEmailUiState.Idle
    }

    fun onVerifyClick() {
        viewModelScope.launch {
            _uiState.value = VerifyEmailUiState.Loading
            when (val r = verifyUseCase(email, _form.value.code)) {
                is Resource.Success -> {
                    _uiState.value = VerifyEmailUiState.Success
                    _events.send(VerifyEmailEvent.NavigateToLogin)
                }
                is Resource.Error -> {
                    _uiState.value = VerifyEmailUiState.Error(r.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun onResendClick() {
        if (_resendCooldown.value > 0) return
        viewModelScope.launch {
            when (val r = resendUseCase(email)) {
                is Resource.Success -> {
                    _events.send(VerifyEmailEvent.ShowSnackbar("Código reenviado. Revisa tu correo."))
                    startCooldown()
                }
                is Resource.Error -> _events.send(VerifyEmailEvent.ShowSnackbar(r.message))
                is Resource.Loading -> Unit
            }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var secs = 60
            while (secs > 0) {
                _resendCooldown.value = secs--
                delay(1_000)
            }
            _resendCooldown.value = 0
        }
    }
}
