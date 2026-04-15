package com.moveinsight.presentation.dashboard

import com.moveinsight.domain.model.Analytics
import com.moveinsight.domain.model.SessionDetail

data class DashboardUiState(
    val isLoadingSessions  : Boolean             = true,
    val isLoadingAnalytics : Boolean             = true,
    val sessions           : List<SessionDetail> = emptyList(),
    val analytics          : Analytics?          = null,
    val errorSessions      : String?             = null,
    val errorAnalytics     : String?             = null,
    val isExporting        : Boolean             = false,
    val exportError        : String?             = null
) {
    val isFullyLoaded get() = !isLoadingSessions && !isLoadingAnalytics
}

sealed class DashboardUiEvent {
    data class ExportReady(val filePath: String)  : DashboardUiEvent()
    data class ShowSnackbar(val message: String)  : DashboardUiEvent()
}