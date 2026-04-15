package com.moveinsight.domain.model

data class SessionDetail(
    val id             : Int,
    val createdAt      : String,
    val weightKg       : Float,
    val borgScore      : Int,
    val status         : String,
    val techniqueScore : Float?,
    val avgVelocity    : Float?,
    val depthDeg       : Float?,
    val symmetryPct    : Float?,
    val repCount       : Int?
) {
    val isCompleted  get() = status == "completed"
    val isProcessing get() = status == "processing"
}