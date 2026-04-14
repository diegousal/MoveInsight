package com.moveinsight.domain.model

data class Session(
    val id      : Int,
    val status  : String,
    val message : String = ""
)