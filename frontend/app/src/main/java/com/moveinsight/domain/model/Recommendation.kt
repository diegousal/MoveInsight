package com.moveinsight.domain.model

enum class RecommendationCategory(val display: String) {
    TECHNIQUE   ("Técnica"),
    LOAD        ("Carga"),
    RECOVERY    ("Recuperación"),
    PROGRESSION ("Progresión"),
    UNKNOWN     ("General");

    companion object {
        fun from(raw: String) = when (raw) {
            "technique"   -> TECHNIQUE
            "load"        -> LOAD
            "recovery"    -> RECOVERY
            "progression" -> PROGRESSION
            else          -> UNKNOWN
        }
    }
}

data class Recommendation(
    val id       : String,
    val category : RecommendationCategory,
    val title    : String,
    val body     : String,
    val priority : Int,
    val videoUrl : String?,
    val trigger  : String,
)
