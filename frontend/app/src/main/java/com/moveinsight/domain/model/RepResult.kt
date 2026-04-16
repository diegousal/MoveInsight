package com.moveinsight.domain.model

data class RepResult(
    val repNumber      : Int,
    val depthScore     : Float,
    val torsoScore     : Float,
    val stabilityScore : Float,
    val kneesScore     : Float,
    val rhythmScore    : Float,
    val overallScore   : Float
)
