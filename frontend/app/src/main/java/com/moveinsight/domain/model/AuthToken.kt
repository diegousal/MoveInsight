package com.moveinsight.domain.model


data class AuthToken(
    val accessToken: String,
    val tokenType: String
)