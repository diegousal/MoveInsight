package com.moveinsight.presentation.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.session.GetSessionDetailUseCase
import com.moveinsight.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSessionDetailUseCase: GetSessionDetailUseCase
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
                                    val reps = detail.results
                                    _uiState.value = ResultsUiState.Success(
                                        ResultsData(
                                            sessionId      = sessionId,
                                            weightKg       = detail.weightKg,
                                            borgScore      = detail.borgScore,
                                            createdAt      = detail.createdAt,
                                            reps           = reps,
                                            checkins       = detail.checkins,
                                            avgDepth       = reps.map { it.depthScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            avgTorso       = reps.map { it.torsoScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            avgStability   = reps.map { it.stabilityScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            avgKnees       = reps.map { it.kneesScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            avgRhythm      = reps.map { it.rhythmScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f,
                                            overallAverage = reps.map { it.overallScore }.average().toFloat().takeIf { it.isFinite() } ?: 0f
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

    fun retry() {
        _uiState.value = ResultsUiState.Loading
        pollSessionResults()
    }
}
