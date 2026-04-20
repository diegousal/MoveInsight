package com.moveinsight.presentation.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.SessionDetail
import com.moveinsight.domain.session.GetSessionDetailUseCase
import com.moveinsight.domain.session.GetSessionsUseCase
import com.moveinsight.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.floor

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle            : SavedStateHandle,
    private val getSessionDetailUseCase : GetSessionDetailUseCase,
    private val getSessionsUseCase      : GetSessionsUseCase
) : ViewModel() {

    private val sessionId: Int = savedStateHandle[Routes.Results.ARG_SESSION_ID] ?: -1

    private val _uiState = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        pollSessionResults()
    }

    private fun pollSessionResults() {
        viewModelScope.launch {
            while (true) {
                try {
                    when (val result = getSessionDetailUseCase(sessionId)) {
                        is Resource.Success -> {
                            val detail = result.data
                            when {
                                detail.status == "failed" -> {
                                    _uiState.value = ResultsUiState.Error(
                                        detail.message.ifEmpty { "El análisis falló en el servidor." }
                                    )
                                    return@launch
                                }
                                detail.isCompleted -> {
                                    val reps           = detail.results
                                    val overallAverage = reps.map { it.overallScore }.average()
                                        .toFloat().takeIf { it.isFinite() } ?: 0f

                                    // ── Fetch session list for comparison (parallel) ──
                                    val sessionsDeferred = async { getSessionsUseCase() }
                                    val sessionsResult   = sessionsDeferred.await()
                                    val comparison       = buildComparison(detail, overallAverage, sessionsResult)

                                    _uiState.value = ResultsUiState.Success(
                                        ResultsData(
                                            sessionId      = sessionId,
                                            weightKg       = detail.weightKg,
                                            borgScore      = detail.borgScore,
                                            userNotes      = detail.userNotes,
                                            createdAt      = detail.createdAt,
                                            reps           = reps,
                                            checkins       = detail.checkins,
                                            avgDepth       = reps.map { it.depthScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            avgTorso       = reps.map { it.torsoScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            avgStability   = reps.map { it.stabilityScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            avgKnees       = reps.map { it.kneesScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            avgRhythm      = reps.map { it.rhythmScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            overallAverage = overallAverage,
                                            fatigue        = calcFatigue(reps.map { it.overallScore }),
                                            comparison     = comparison
                                        )
                                    )
                                    return@launch
                                }
                                else -> delay(3_000L)
                            }
                        }
                        is Resource.Error -> {
                            _uiState.value = ResultsUiState.Error(result.message)
                            return@launch
                        }
                        is Resource.Loading -> delay(3_000L)
                    }
                } catch (e: Exception) {
                    _uiState.value = ResultsUiState.Error(
                        "Error inesperado al obtener los resultados: ${e.localizedMessage}"
                    )
                    return@launch
                }
            }
        }
    }

    // ── Fatiga intra-sesión ───────────────────────────────────────────────────

    private fun calcFatigue(scores: List<Float>): RepFatigueData? {
        if (scores.size < 4) return null   // necesitamos al menos 4 reps para dividir
        val half       = floor(scores.size / 2.0).toInt()
        val firstHalf  = scores.take(half)
        val secondHalf = scores.drop(half)
        val firstAvg   = firstHalf.average().toFloat()
        val secondAvg  = secondHalf.average().toFloat()
        val delta      = secondAvg - firstAvg
        val label = when {
            delta < -1.0f -> "Fatiga detectada"
            delta >  0.5f -> "Mejora intra-sesión"
            else          -> "Técnica consistente"
        }
        return RepFatigueData(firstAvg, secondAvg, delta, label)
    }

    // ── Comparativa con sesión anterior ──────────────────────────────────────

    private fun buildComparison(
        current        : SessionDetail,
        currentAverage : Float,
        sessionsResult : Resource<List<SessionDetail>>
    ): SessionComparison? {
        if (sessionsResult !is Resource.Success) return null
        val completed = sessionsResult.data
            .filter { it.status == "completed" && it.id != current.id && it.overallScore != null }
            .sortedByDescending { it.createdAt }
        val prev = completed.firstOrNull() ?: return null
        val prevAvg    = prev.overallScore ?: return null
        val prevDate   = formatDate(prev.createdAt)
        return SessionComparison(
            prevCreatedAt = prevDate,
            overallDelta  = currentAverage - prevAvg,
            weightDelta   = current.weightKg - prev.weightKg
        )
    }

    private fun formatDate(iso: String): String {
        return try {
            val parts = iso.take(10).split("-")
            if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
        } catch (_: Exception) { iso }
    }

    fun retry() {
        _uiState.value = ResultsUiState.Loading
        pollSessionResults()
    }
}
