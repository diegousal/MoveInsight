package com.moveinsight.presentation.results

import com.moveinsight.domain.model.PainCheckInSummary
import com.moveinsight.domain.model.RepResult

data class ResultsData(
    val sessionId      : Int,
    val weightKg       : Float = 0f,
    val borgScore      : Int = 0,
    val createdAt      : String = "",
    val reps           : List<RepResult> = emptyList(),
    val checkins       : List<PainCheckInSummary> = emptyList(),
    val avgDepth       : Float = 0f,
    val avgTorso       : Float = 0f,
    val avgStability   : Float = 0f,
    val avgKnees       : Float = 0f,
    val avgRhythm      : Float = 0f,
    val overallAverage : Float = 0f
)

sealed class ResultsUiState {
    data object Loading : ResultsUiState()
    data class Success(val data: ResultsData) : ResultsUiState()
    data class Error(val message: String) : ResultsUiState()
}
