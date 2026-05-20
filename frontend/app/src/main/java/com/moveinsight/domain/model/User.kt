package com.moveinsight.domain.model

data class User(
    val id       : Int,
    val email    : String,
    val fullName : String
)

data class UserProfile(
    val id                  : Int,
    val email               : String,
    val fullName            : String,
    val age                 : Int?    = null,
    val bodyWeightKg        : Float?  = null,
    val level               : String? = null,  // "beginner" | "intermediate" | "advanced"
    val objective           : String? = null,  // "technique" | "progression" | "pain_monitoring" | "health"
    val onboardingCompleted : Boolean = false
)
