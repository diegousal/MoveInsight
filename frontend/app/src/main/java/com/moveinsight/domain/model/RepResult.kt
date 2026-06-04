package com.moveinsight.domain.model

/**
 * Resultado de una repetición. Los KPIs están en escala 0-10 (mapeados desde
 * las clases 1/2/3 del modelo XGBoost). `knees` (valgo) y `symmetry` son null
 * cuando el vídeo es de perfil lateral (no medibles en ese plano).
 */
data class RepResult(
    val repNumber      : Int,
    val depthScore     : Float?,
    val torsoScore     : Float?,
    val kneesScore     : Float?,   // valgo de rodilla (solo frontal)
    val symmetryScore  : Float?,   // simetría L/R (solo frontal)
    val rhythmScore    : Float?,
    val overallScore   : Float?
)
