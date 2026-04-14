package com.moveinsight.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.auth.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _form    = MutableStateFlow(RegisterFormState())
    val form: StateFlow<RegisterFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events  = Channel<RegisterUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onFullNameChange(v: String)        { _form.update { it.copy(fullName = v) };        clearError() }
    fun onEmailChange(v: String)           { _form.update { it.copy(email = v) };           clearError() }
    fun onPasswordChange(v: String)        { _form.update { it.copy(password = v) };        clearError() }
    fun onConfirmPasswordChange(v: String) { _form.update { it.copy(confirmPassword = v) }; clearError() }

    fun onRegisterClick() {
        viewModelScope.launch {
            _uiState.value = RegisterUiState.Loading
            val f = _form.value
            when (val r = registerUseCase(f.email, f.password, f.confirmPassword, f.fullName)) {
                is Resource.Success -> {
                    _uiState.value = RegisterUiState.Success
                    _events.send(RegisterUiEvent.NavigateToLogin)
                }
                is Resource.Error -> {
                    _uiState.value = RegisterUiState.Error(r.message)
                    _events.send(RegisterUiEvent.ShowSnackbar(r.message))
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun clearError() {
        if (_uiState.value is RegisterUiState.Error) _uiState.value = RegisterUiState.Idle
    }
}