package com.moveinsight.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.security.UserPreferencesDataStore
import com.moveinsight.core.utils.Resource
import com.moveinsight.core.utils.safeApiCall
import com.moveinsight.data.remote.SessionApiService
import com.moveinsight.domain.model.Analytics
import com.moveinsight.domain.model.ReadinessLevel
import com.moveinsight.domain.model.readinessLevel
import com.moveinsight.domain.repository.GetAnalyticsUseCase
import com.moveinsight.domain.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiData(
    val analytics          : Analytics?     = null,
    val readinessLevel     : ReadinessLevel = ReadinessLevel.HIGH,
    val isLoading          : Boolean        = true,
    val isProcessingAction : Boolean        = false,
    val userFullName       : String         = "",
    val userEmail          : String         = ""
) {
    val userInitials: String get() = userFullName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
}

sealed class HomeUiEvent {
    data object NavigateToLogin                    : HomeUiEvent()
    data class  ShowSnackbar(val message: String)  : HomeUiEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val logoutUseCase       : LogoutUseCase,
    private val getAnalyticsUseCase : GetAnalyticsUseCase,
    private val sessionApiService   : SessionApiService,
    private val dataStore           : UserPreferencesDataStore
) : ViewModel() {

    private val _data   = MutableStateFlow(HomeUiData())
    val data: StateFlow<HomeUiData> = _data.asStateFlow()

    private val _events = Channel<HomeUiEvent>(Channel.BUFFERED)
    val events          = _events.receiveAsFlow()

    init {
        loadUserInfo()
        loadReadiness()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            combine(
                dataStore.getUserFullName(),
                dataStore.getUserEmail()
            ) { name, email -> Pair(name ?: "", email ?: "") }
                .collect { (name, email) ->
                    _data.update { it.copy(userFullName = name, userEmail = email) }
                }
        }
    }

    private fun loadReadiness() {
        viewModelScope.launch {
            _data.update { it.copy(isLoading = true) }
            when (val result = getAnalyticsUseCase()) {
                is Resource.Success -> _data.update {
                    it.copy(
                        analytics      = result.data,
                        readinessLevel = result.data.readinessLevel(),
                        isLoading      = false
                    )
                }
                else -> _data.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onLogoutClick() {
        viewModelScope.launch {
            logoutUseCase()
            _events.send(HomeUiEvent.NavigateToLogin)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            _data.update { it.copy(isProcessingAction = true) }
            val result = safeApiCall { sessionApiService.clearHistory() }
            _data.update { it.copy(isProcessingAction = false) }
            when (result) {
                is Resource.Success -> {
                    loadReadiness()
                    _events.send(HomeUiEvent.ShowSnackbar("Historial eliminado correctamente"))
                }
                is Resource.Error -> _events.send(HomeUiEvent.ShowSnackbar("Error: ${result.message}"))
                else -> Unit
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _data.update { it.copy(isProcessingAction = true) }
            val result = safeApiCall { sessionApiService.deleteAccount() }
            _data.update { it.copy(isProcessingAction = false) }
            when (result) {
                is Resource.Success -> {
                    logoutUseCase()
                    _events.send(HomeUiEvent.NavigateToLogin)
                }
                is Resource.Error -> _events.send(HomeUiEvent.ShowSnackbar("Error: ${result.message}"))
                else -> Unit
            }
        }
    }
}
