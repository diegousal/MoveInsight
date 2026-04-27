package com.moveinsight.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.auth.ChangePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordFormState(
    val currentPassword : String = "",
    val newPassword     : String = "",
    val confirmPassword : String = ""
)

sealed class ChangePasswordUiState {
    data object Idle    : ChangePasswordUiState()
    data object Loading : ChangePasswordUiState()
    data object Success : ChangePasswordUiState()
    data class  Error(val message: String) : ChangePasswordUiState()
}

sealed class ChangePasswordEvent {
    data object NavigateBack                 : ChangePasswordEvent()
    data class  ShowSnackbar(val msg: String): ChangePasswordEvent()
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val changeUseCase: ChangePasswordUseCase
) : ViewModel() {

    private val _form    = MutableStateFlow(ChangePasswordFormState())
    val form: StateFlow<ChangePasswordFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<ChangePasswordUiState>(ChangePasswordUiState.Idle)
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    private val _events  = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onCurrentPasswordChange(v: String) { _form.update { it.copy(currentPassword = v) }; clearError() }
    fun onNewPasswordChange(v: String)     { _form.update { it.copy(newPassword = v) };     clearError() }
    fun onConfirmPasswordChange(v: String) { _form.update { it.copy(confirmPassword = v) }; clearError() }

    fun onSubmitClick() {
        viewModelScope.launch {
            _uiState.value = ChangePasswordUiState.Loading
            val f = _form.value
            when (val r = changeUseCase(f.currentPassword, f.newPassword, f.confirmPassword)) {
                is Resource.Success -> {
                    _uiState.value = ChangePasswordUiState.Success
                    _events.send(ChangePasswordEvent.ShowSnackbar("Contraseña actualizada correctamente."))
                    _events.send(ChangePasswordEvent.NavigateBack)
                }
                is Resource.Error -> {
                    _uiState.value = ChangePasswordUiState.Error(r.message)
                    _events.send(ChangePasswordEvent.ShowSnackbar(r.message))
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun clearError() {
        if (_uiState.value is ChangePasswordUiState.Error) _uiState.value = ChangePasswordUiState.Idle
    }
}
