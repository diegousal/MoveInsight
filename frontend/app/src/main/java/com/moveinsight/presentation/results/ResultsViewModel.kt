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
                when (val result = getSessionDetailUseCase(sessionId)) {
                    is Resource.Success -> {
                        val detail = result.data
                        if (detail.isCompleted && detail.results.isNotEmpty()) {
                            val reps = detail.results
                            val avgDepth     = reps.map { it.depthScore }.average().toFloat()
                            val avgTorso     = reps.map { it.torsoScore }.average().toFloat()
                            val avgStability = reps.map { it.stabilityScore }.average().toFloat()
                            val avgKnees     = reps.map { it.kneesScore }.average().toFloat()
                            val avgRhythm    = reps.map { it.rhythmScore }.average().toFloat()
                            val overall      = reps.map { it.overallScore }.average().toFloat()

                            _uiState.value = ResultsUiState.Success(
                                ResultsData(
                                    sessionId      = sessionId,
                                    weightKg       = detail.weightKg,
                                    borgScore      = detail.borgScore,
                                    createdAt      = detail.createdAt,
                                    reps           = reps,
                                    avgDepth       = avgDepth,
                                    avgTorso       = avgTorso,
                                    avgStability   = avgStability,
                                    avgKnees       = avgKnees,
                                    avgRhythm      = avgRhythm,
                                    overallAverage = overall
                                )
                            )
                            return@launch // Detener polling
                        } else {
                            // Todavía procesando → seguir polling
                            delay(3_000L)
                        }
                    }
                    is Resource.Error -> {
                        _uiState.value = ResultsUiState.Error(result.message)
                        return@launch
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun retry() {
        _uiState.value = ResultsUiState.Loading
        pollSessionResults()
    }
}
