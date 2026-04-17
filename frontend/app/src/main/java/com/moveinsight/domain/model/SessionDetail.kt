package com.moveinsight.domain.model

data class SessionDetail(
    val id             : Int,
    val status         : String,
    val message        : String = "",
    val weightKg       : Float = 0f,
    val borgScore      : Int = 0,
    val createdAt      : String = "",
    val results        : List<RepResult> = emptyList(),
    val overallScore   : Float? = null,
    val repCount       : Int?   = null,
    val checkins       : List<PainCheckInSummary> = emptyList()
) {
    val isCompleted  get() = status == "completed"
    val isProcessing get() = status == "processing" || status == "queued"
}
