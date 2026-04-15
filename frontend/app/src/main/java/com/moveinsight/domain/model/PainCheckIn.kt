package com.moveinsight.domain.model

data class PainCheckIn(
    val id        : Int,
    val sessionId : Int,
    val evaScore  : Int,
    val hoursAfter: Int,
    val bodyZones : List<String>
)