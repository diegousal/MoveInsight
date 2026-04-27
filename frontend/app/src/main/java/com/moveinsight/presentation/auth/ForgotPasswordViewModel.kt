package com.moveinsight.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.auth.ForgotPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ForgotPasswordUiState {
    data object Idle    : ForgotPasswordUiState()
    data object Loading : ForgotPasswordUiState()
    data object Sent    : ForgotPasswordUiState()   // código enviado
    data class  Error(val message: String) : ForgotPasswordUiState()
}

sealed class ForgotPasswordEvent {
    data class NavigateToReset(val email: String) : ForgotPasswordEvent()
    data class ShowSnackbar(val msg: String)       : ForgotPasswordEvent()
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val forgotUseCase: ForgotPasswordUseCase
) : ViewModel() {

    private val _email   = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _uiState = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.Idle)
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _events  = Channel<ForgotPasswordEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onEmailChange(v: String) {
        _email.value = v
        if (_uiState.value is ForgotPasswordUiState.Error) _uiState.value = ForgotPasswordUiState.Idle
    }

    fun onSubmitClick() {
        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading
            when (val r = forgotUseCase(_email.value)) {
                is Resource.Success -> {
                    _uiState.value = ForgotPasswordUiState.Sent
                    _events.send(ForgotPasswordEvent.NavigateToReset(_email.value.trim()))
                }
                is Resource.Error -> {
                    _uiState.value = ForgotPasswordUiState.Error(r.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }
}
