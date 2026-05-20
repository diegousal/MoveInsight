package com.moveinsight.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.notifications.SchedulePainNotificationsUseCase
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.ExportReportUseCase
import com.moveinsight.domain.repository.GetAnalyticsUseCase
import com.moveinsight.domain.session.GetSessionsUseCase
import com.moveinsight.domain.wellness.ListIncidentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getSessionsUseCase           : GetSessionsUseCase,
    private val getAnalyticsUseCase          : GetAnalyticsUseCase,
    private val exportReportUseCase          : ExportReportUseCase,
    private val schedulePainNotificationsUseCase : SchedulePainNotificationsUseCase,
    private val listIncidentsUseCase         : ListIncidentsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _events = Channel<DashboardUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { loadAll() }

    fun loadAll() {
        loadSessions()
        loadAnalytics()
        loadIncidents()
    }

    private fun loadIncidents() {
        viewModelScope.launch {
            when (val result = listIncidentsUseCase()) {
                is Resource.Success ->
                    _uiState.update { it.copy(incidents = result.data) }
                else -> Unit   // silencioso: no es bloqueante para el dashboard
            }
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true, errorSessions = null) }
            when (val result = getSessionsUseCase()) {
                is Resource.Success ->
                    _uiState.update { it.copy(isLoadingSessions = false, sessions = result.data) }
                is Resource.Error   ->
                    _uiState.update { it.copy(isLoadingSessions = false, errorSessions = result.message) }
                else -> Unit
            }
        }
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAnalytics = true, errorAnalytics = null) }
            when (val result = getAnalyticsUseCase()) {
                is Resource.Success ->
                    _uiState.update { it.copy(isLoadingAnalytics = false, analytics = result.data) }
                is Resource.Error   ->
                    _uiState.update { it.copy(isLoadingAnalytics = false, errorAnalytics = result.message) }
                else -> Unit
            }
        }
    }

    fun onExportClick() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            when (val result = exportReportUseCase()) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isExporting = false) }
                    _events.send(DashboardUiEvent.ExportReady(result.data.absolutePath))
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isExporting = false, exportError = result.message) }
                    _events.send(DashboardUiEvent.ShowSnackbar(result.message))
                }
                else -> Unit
            }
        }
    }

    fun testNotification() {
        val sessionId = _uiState.value.sessions.firstOrNull()?.id ?: 1
        schedulePainNotificationsUseCase.triggerTest(sessionId)
        viewModelScope.launch {
            _events.send(DashboardUiEvent.ShowSnackbar(
                "🔔 EVA 24h en ~10s · EVA 48h en ~25s (sesión #$sessionId)"
            ))
        }
    }
}