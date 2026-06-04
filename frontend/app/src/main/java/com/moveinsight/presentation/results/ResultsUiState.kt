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
    // KPIs 0-10. knees (valgo) y symmetry son null en vídeos laterales (no medibles).
    val avgDepth       : Float? = null,
    val avgTorso       : Float? = null,
    val avgKnees       : Float? = null,   // valgo de rodilla
    val avgSymmetry    : Float? = null,
    val avgRhythm      : Float? = null,
    val overallAverage : Float = 0f,
    val fatigue        : RepFatigueData?    = null,
    val comparison     : SessionComparison? = null
)

sealed class ResultsUiState {
    data object Loading : ResultsUiState()
    data class Success(val data: ResultsData) : ResultsUiState()
    data class Error(val message: String) : ResultsUiState()
}
