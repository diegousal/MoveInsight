package com.moveinsight.domain.repository

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Session
import java.io.File

/** Contrato puro — el dominio no sabe nada de Retrofit ni Multipart */
interface SessionRepository {
    suspend fun uploadSession(
        videoFile : File,
        weightKg  : Float,
        borgScore : Int
    ): Resource<Session>
}