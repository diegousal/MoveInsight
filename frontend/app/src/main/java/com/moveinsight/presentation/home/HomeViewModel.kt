package com.moveinsight.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
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
    val analytics      : Analytics?     = null,
    val readinessLevel : ReadinessLevel = ReadinessLevel.HIGH,
    val isLoading      : Boolean        = true
)

sealed class HomeUiEvent {
    data object NavigateToLogin : HomeUiEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val logoutUseCase      : LogoutUseCase,
    private val getAnalyticsUseCase: GetAnalyticsUseCase
) : ViewModel() {

    private val _data   = MutableStateFlow(HomeUiData())
    val data: StateFlow<HomeUiData> = _data.asStateFlow()

    private val _events = Channel<HomeUiEvent>(Channel.BUFFERED)
    val events          = _events.receiveAsFlow()

    init { loadReadiness() }

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
                // Si falla (primera vez sin sesiones) mostramos readiness verde por defecto
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
}