package com.moveinsight.domain.model

data class PainCheckInSummary(
    val id         : Int,
    val evaScore   : Int,
    val hoursAfter : Int,
    val bodyZones  : List<String> = emptyList(),
    val notes      : String = "",
    val createdAt  : String = ""
)
