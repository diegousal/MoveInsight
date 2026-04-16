package com.moveinsight.domain.model

data class SessionDetail(
    val id             : Int,
    val status         : String,
    val message        : String = "",
    val weightKg       : Float = 0f,
    val borgScore      : Int = 0,
    val createdAt      : String = "",

    // Resultados por repetición (del endpoint de detalle)
    val results        : List<RepResult> = emptyList(),

    // Campos legacy para el Dashboard (null cuando vienen del listado)
    val techniqueScore : Float? = null,
    val avgVelocity    : Float? = null,
    val depthDeg       : Float? = null,
    val symmetryPct    : Float? = null,
    val repCount       : Int?   = null
) {
    val isCompleted  get() = status == "completed"
    val isProcessing get() = status == "processing" || status == "queued"
}
