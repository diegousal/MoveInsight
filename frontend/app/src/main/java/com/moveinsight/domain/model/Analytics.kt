package com.moveinsight.domain.model
data class Analytics(
    val totalSessions      : Int,
    val avgTechniqueScore  : Float?,
    val avgVelocity        : Float?,
    val maxWeightKg        : Float?,
    val bestTechniqueScore : Float?  = null,
    val maxReps            : Int?    = null,
    val overtrainingRisk   : Boolean = false,
    val readinessScore     : Int,        // 0-100
    val readinessLabel     : String,     // "high" | "medium" | "low"
    val insights           : List<Insight>
)

data class Insight(
    val type    : String,   // "warning" | "tip" | "achievement"
    val title   : String,
    val message : String
)

/** Simplifica la lógica de semáforo en la UI */
enum class ReadinessLevel { HIGH, MEDIUM, LOW }

fun Analytics.readinessLevel(): ReadinessLevel = when (readinessLabel.lowercase()) {
    "high"   -> ReadinessLevel.HIGH
    "medium" -> ReadinessLevel.MEDIUM
    else     -> ReadinessLevel.LOW
}