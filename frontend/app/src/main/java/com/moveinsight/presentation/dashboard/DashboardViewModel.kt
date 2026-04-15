package com.moveinsight.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.ExportReportUseCase
import com.moveinsight.domain.repository.GetAnalyticsUseCase
import com.moveinsight.domain.session.GetSessionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getSessionsUseCase  : GetSessionsUseCase,
    private val getAnalyticsUseCase : GetAnalyticsUseCase,
    private val exportReportUseCase : ExportReportUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _events = Channel<DashboardUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { loadAll() }

    fun loadAll() {
        loadSessions()
        loadAnalytics()
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
}