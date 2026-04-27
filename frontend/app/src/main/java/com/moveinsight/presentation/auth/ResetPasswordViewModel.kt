package com.moveinsight.presentation.auth

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.auth.ResetPasswordUseCase
import com.moveinsight.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResetPasswordFormState(
    val code            : String = "",
    val newPassword     : String = "",
    val confirmPassword : String = ""
)

sealed class ResetPasswordUiState {
    data object Idle    : ResetPasswordUiState()
    data object Loading : ResetPasswordUiState()
    data object Success : ResetPasswordUiState()
    data class  Error(val message: String) : ResetPasswordUiState()
}

sealed class ResetPasswordEvent {
    data object NavigateToLogin              : ResetPasswordEvent()
    data class  ShowSnackbar(val msg: String): ResetPasswordEvent()
}

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    savedStateHandle       : SavedStateHandle,
    private val resetUseCase: ResetPasswordUseCase
) : ViewModel() {

    val email: String = Uri.decode(
        savedStateHandle.get<String>(Routes.ResetPassword.ARG_EMAIL) ?: ""
    )

    private val _form    = MutableStateFlow(ResetPasswordFormState())
    val form: StateFlow<ResetPasswordFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<ResetPasswordUiState>(ResetPasswordUiState.Idle)
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private val _events  = Channel<ResetPasswordEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onCodeChange(v: String) {
        if (v.length <= 6 && v.all { it.isDigit() }) _form.update { it.copy(code = v) }
        clearError()
    }
    fun onNewPasswordChange(v: String)     { _form.update { it.copy(newPassword = v) };     clearError() }
    fun onConfirmPasswordChange(v: String) { _form.update { it.copy(confirmPassword = v) }; clearError() }

    fun onSubmitClick() {
        viewModelScope.launch {
            _uiState.value = ResetPasswordUiState.Loading
            val f = _form.value
            when (val r = resetUseCase(email, f.code, f.newPassword, f.confirmPassword)) {
                is Resource.Success -> {
                    _uiState.value = ResetPasswordUiState.Success
                    _events.send(ResetPasswordEvent.NavigateToLogin)
                }
                is Resource.Error -> _uiState.value = ResetPasswordUiState.Error(r.message)
                is Resource.Loading -> Unit
            }
        }
    }

    private fun clearError() {
        if (_uiState.value is ResetPasswordUiState.Error) _uiState.value = ResetPasswordUiState.Idle
    }
}
