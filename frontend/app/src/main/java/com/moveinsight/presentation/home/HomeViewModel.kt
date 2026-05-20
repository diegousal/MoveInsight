package com.moveinsight.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.notifications.NotificationHelper
import com.moveinsight.core.notifications.ScheduleWeeklySummaryUseCase
import com.moveinsight.core.security.UserPreferencesDataStore
import com.moveinsight.core.utils.Resource
import com.moveinsight.core.utils.safeApiCall
import com.moveinsight.data.remote.SessionApiService
import com.moveinsight.domain.model.Analytics
import com.moveinsight.domain.model.ReadinessLevel
import com.moveinsight.domain.model.Recommendation
import com.moveinsight.domain.model.readinessLevel
import com.moveinsight.domain.recommendations.GetRecommendationsUseCase
import com.moveinsight.domain.repository.GetAnalyticsUseCase
import com.moveinsight.domain.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiData(
    val analytics          : Analytics?          = null,
    val readinessLevel     : ReadinessLevel      = ReadinessLevel.HIGH,
    val isLoading          : Boolean             = true,
    val isProcessingAction : Boolean             = false,
    val userFullName       : String              = "",
    val userEmail          : String              = "",
    val recommendations    : List<Recommendation> = emptyList(),
    // ── Preferencias de notificación ──────────────────────────────────────
    val notifGeneral       : Boolean             = true,
    val notif24h           : Boolean             = true,
    val notif48h           : Boolean             = true
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
    private val logoutUseCase               : LogoutUseCase,
    private val getAnalyticsUseCase         : GetAnalyticsUseCase,
    private val getRecommendationsUseCase   : GetRecommendationsUseCase,
    private val sessionApiService           : SessionApiService,
    private val dataStore                   : UserPreferencesDataStore,
    private val scheduleWeeklySummaryUseCase: ScheduleWeeklySummaryUseCase,
    private val notificationHelper          : NotificationHelper
) : ViewModel() {

    private val _data   = MutableStateFlow(HomeUiData())
    val data: StateFlow<HomeUiData> = _data.asStateFlow()

    private val _events = Channel<HomeUiEvent>(Channel.BUFFERED)
    val events          = _events.receiveAsFlow()

    init {
        loadUserInfo()
        loadReadiness()
        loadNotifPrefs()
        scheduleWeeklySummaryUseCase()   // programa el resumen semanal (KEEP si ya existe)
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            when (val r = getRecommendationsUseCase()) {
                is Resource.Success -> _data.update { it.copy(recommendations = r.data) }
                else -> Unit   // no mostramos error — las recs son opcionales
            }
        }
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

    /** Llamar cada vez que la pantalla vuelve al foco (ON_RESUME). */
    fun refresh() {
        loadReadiness()
        loadRecommendations()
    }

    // ── Preferencias de notificación ──────────────────────────────────────

    private fun loadNotifPrefs() {
        viewModelScope.launch {
            combine(
                dataStore.getNotifGeneral(),
                dataStore.getNotif24h(),
                dataStore.getNotif48h()
            ) { general, h24, h48 -> Triple(general, h24, h48) }
                .collect { (general, h24, h48) ->
                    _data.update { it.copy(notifGeneral = general, notif24h = h24, notif48h = h48) }
                }
        }
    }

    /**
     * Activa/desactiva el interruptor maestro.
     * – Si se activa y ambas sub-opciones estaban OFF → se activan las dos.
     * – Si se desactiva → no toca las sub-opciones (solo bloquea el scheduling).
     */
    fun onNotifGeneralToggle(enabled: Boolean) {
        viewModelScope.launch {
            val current = _data.value
            val new24h = if (enabled && !current.notif24h && !current.notif48h) true else current.notif24h
            val new48h = if (enabled && !current.notif24h && !current.notif48h) true else current.notif48h
            dataStore.saveNotifPrefs(general = enabled, h24 = new24h, h48 = new48h)
        }
    }

    /** Al desactivar 24h: si 48h también está OFF → apaga el general. */
    fun onNotif24hToggle(enabled: Boolean) {
        viewModelScope.launch {
            val current   = _data.value
            val newGeneral = enabled || current.notif48h   // general sigue ON si al menos una está ON
            dataStore.saveNotifPrefs(general = newGeneral, h24 = enabled, h48 = current.notif48h)
        }
    }

    /** Al desactivar 48h: si 24h también está OFF → apaga el general. */
    fun onNotif48hToggle(enabled: Boolean) {
        viewModelScope.launch {
            val current    = _data.value
            val newGeneral = current.notif24h || enabled
            dataStore.saveNotifPrefs(general = newGeneral, h24 = current.notif24h, h48 = enabled)
        }
    }

    private fun loadReadiness() {
        viewModelScope.launch {
            _data.update { it.copy(isLoading = true) }
            when (val result = getAnalyticsUseCase()) {
                is Resource.Success -> {
                    val analytics = result.data
                    _data.update {
                        it.copy(
                            analytics      = analytics,
                            readinessLevel = analytics.readinessLevel(),
                            isLoading      = false
                        )
                    }
                    loadRecommendations()
                    // Alerta de sobreentrenamiento (solo si no se ha mostrado ya hoy)
                    if (analytics.overtrainingRisk) {
                        val avgBorg = if (analytics.readinessScore < 40)
                            ((100 - analytics.readinessScore) / 8f)
                        else 8f
                        notificationHelper.showOvertrainingAlert(avgBorg, analytics.readinessScore)
                    }
                }
                else -> _data.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onLogoutClick() {
        viewModelScope.launch {
            scheduleWeeklySummaryUseCase.cancel()
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
