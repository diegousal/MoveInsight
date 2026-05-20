package com.moveinsight.domain.model

// ── Tipos auxiliares ────────────────────────────────────────────────────────

enum class IncidentType(val apiValue: String, val display: String) {
    MOLESTIA     ("molestia",     "Molestia"),
    DOLOR_AGUDO  ("dolor_agudo",  "Dolor agudo"),
    FATIGA       ("fatiga",       "Fatiga"),
    CONTRACTURA  ("contractura",  "Contractura"),
    LESION       ("lesion",       "Lesión"),
    OTRO         ("otro",         "Otro");

    companion object {
        fun fromApi(value: String): IncidentType =
            entries.firstOrNull { it.apiValue == value } ?: MOLESTIA
    }
}

// ── Incidencia ─────────────────────────────────────────────────────────────

data class Incident(
    val id           : Int,
    val bodyZone     : String,
    val severity     : Int,                 // 0-10
    val incidentType : IncidentType,
    val startDate    : String,              // ISO yyyy-MM-dd
    val endDate      : String? = null,
    val notes        : String  = "",
    val createdAt    : String? = null,
    val isActive     : Boolean = true
)

// ── Timeline ───────────────────────────────────────────────────────────────

data class TimelinePoint(
    val date      : String,
    val sessionId : Int,
    val readiness : Int,
    val load      : Float,
    val technique : Float? = null,
    val borg      : Int?   = null,
    val weightKg  : Float? = null
)

data class WellnessTimeline(
    val points    : List<TimelinePoint> = emptyList(),
    val incidents : List<Incident>      = emptyList()
)

// ── Factores observados ────────────────────────────────────────────────────

enum class FactorSeverity(val apiValue: String) {
    LOW("low"), MODERATE("moderate"), HIGH("high");

    companion object {
        fun fromApi(value: String): FactorSeverity =
            entries.firstOrNull { it.apiValue == value } ?: LOW
    }
}

data class ObservedFactor(
    val name        : String,
    val value       : Float,
    val baseline    : Float,
    val deltaPct    : Float,
    val severity    : FactorSeverity,
    val description : String
)

data class IncidentFactors(
    val incidentId     : Int,
    val windowDays     : Int,
    val sampleSize     : Int,
    val factors        : List<ObservedFactor> = emptyList(),
    val note           : String,
    val timelinePoints : List<TimelinePoint>  = emptyList()
)
