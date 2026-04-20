package com.moveinsight.presentation.results

import com.moveinsight.domain.model.PainCheckInSummary
import com.moveinsight.domain.model.RepResult

/** Resumen de fatiga intra-sesión calculado a partir de las repeticiones. */
data class RepFatigueData(
    val firstHalfAvg  : Float,   // media de la primera mitad de reps
    val secondHalfAvg : Float,   // media de la segunda mitad de reps
    val delta         : Float,   // secondHalf - firstHalf (negativo = fatiga, positivo = mejora)
    val label         : String   // "Técnica consistente" | "Fatiga detectada" | "Mejora intra-sesión"
)

/** Comparativa con la sesión anterior del mismo usuario. */
data class SessionComparison(
    val prevCreatedAt     : String,  // "15/12/2024"
    val overallDelta      : Float,   // current.overallAverage - prev.overallAverage
    val weightDelta       : Float    // current.weightKg - prev.weightKg
)

data class ResultsData(
    val sessionId      : Int,
    val weightKg       : Float = 0f,
    val borgScore      : Int = 0,
    val userNotes      : String = "",
    val createdAt      : String = "",
    val reps           : List<RepResult> = emptyList(),
    val checkins       : List<PainCheckInSummary> = emptyList(),
    val avgDepth       : Float = 0f,
    val avgTorso       : Float = 0f,
    val avgStability   : Float = 0f,
    val avgKnees       : Float = 0f,
    val avgRhythm      : Float = 0f,
    val overallAverage : Float = 0f,
    val fatigue        : RepFatigueData?    = null,
    val comparison     : SessionComparison? = null
)

sealed class ResultsUiState {
    data object Loading : ResultsUiState()
    data class Success(val data: ResultsData) : ResultsUiState()
    data class Error(val message: String) : ResultsUiState()
}
