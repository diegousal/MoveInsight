package com.moveinsight.domain.model

enum class ReadinessTrend(val display: String) {
    DECLINING  ("Descendente ↓"),
    STABLE     ("Estable →"),
    IMPROVING  ("Ascendente ↑");

    companion object {
        fun from(raw: String) = when (raw) {
            "declining"  -> DECLINING
            "improving"  -> IMPROVING
            else         -> STABLE
        }
    }
}

data class ValidationDetail(
    val incidentId     : Int,
    val bodyZone       : String,
    val readinessBefore: Int?,
    val readinessAt    : Int?,
    val delta          : Int?,
    val trend          : ReadinessTrend,
)

data class ValidationResult(
    val incidentsAnalyzed  : Int,
    val earlyWarningCount  : Int,
    val earlyWarningRate   : Float,
    val details            : List<ValidationDetail>,
    val summary            : String,
)
