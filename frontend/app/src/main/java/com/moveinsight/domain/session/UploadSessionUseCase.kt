package com.moveinsight.domain.session

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Session
import com.moveinsight.domain.repository.SessionRepository
import java.io.File
import javax.inject.Inject

class UploadSessionUseCase @Inject constructor(
    private val repository: SessionRepository
) {
    suspend operator fun invoke(
        videoFile : File,
        weightKg  : Float,
        borgScore : Int,
        userNotes : String = ""
    ): Resource<Session> {
        // Validaciones de dominio previas al upload
        if (!videoFile.exists() || videoFile.length() == 0L)
            return Resource.Error("El archivo de vídeo no es válido.")
        if (weightKg < 0f)
            return Resource.Error("La carga no puede ser negativa.")
        if (borgScore !in 0..10)
            return Resource.Error("La puntuación Borg debe estar entre 0 y 10.")

        return repository.uploadSession(videoFile, weightKg, borgScore, userNotes)
    }
}