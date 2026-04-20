package com.moveinsight.domain.model

data class Session(
    val id        : Int,
    val status    : String,
    val message   : String = "",
    val createdAt : String = "",   // ISO timestamp from backend, e.g. "2024-12-15T18:32:00"
    val userNotes : String = ""
)