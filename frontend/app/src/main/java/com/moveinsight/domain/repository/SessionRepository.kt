package com.moveinsight.domain.repository

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Session
import com.moveinsight.domain.model.SessionDetail
import java.io.File

/** Contrato puro — el dominio no sabe nada de Retrofit ni Multipart */
interface SessionRepository {
    suspend fun uploadSession(
        videoFile : File,
        weightKg  : Float,
        borgScore : Int
    ): Resource<Session>
    suspend fun getSessions(): Resource<List<SessionDetail>>
    suspend fun getSessionDetail(sessionId: Int): Resource<SessionDetail>
}