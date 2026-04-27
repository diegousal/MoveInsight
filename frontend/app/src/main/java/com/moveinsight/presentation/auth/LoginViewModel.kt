package com.moveinsight.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.auth.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _form  = MutableStateFlow(LoginFormState())
    val form: StateFlow<LoginFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Channel para eventos efímeros — no se replayan al recomposición
    private val _events = Channel<LoginUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onEmailChange(value: String) {
        _form.update { it.copy(email = value) }
        clearError()
    }

    fun onPasswordChange(value: String) {
        _form.update { it.copy(password = value) }
        clearError()
    }

    fun onLoginClick() {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            when (val result = loginUseCase(_form.value.email, _form.value.password)) {
                is Resource.Success -> {
                    _uiState.value = LoginUiState.Success
                    _events.send(LoginUiEvent.NavigateToHome)
                }
                is Resource.Error -> {
                    if (result.code == 403 && result.message == "email_not_verified") {
                        _uiState.value = LoginUiState.Idle
                        _events.send(LoginUiEvent.NavigateToVerifyEmail(_form.value.email))
                    } else {
                        _uiState.value = LoginUiState.Error(result.message)
                        _events.send(LoginUiEvent.ShowSnackbar(result.message))
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun clearError() {
        if (_uiState.value is LoginUiState.Error) _uiState.value = LoginUiState.Idle
    }
}