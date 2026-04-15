package com.moveinsight.domain.repository


import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.PainCheckIn
import com.moveinsight.domain.repository.PainCheckInRepository
import javax.inject.Inject

class SubmitPainCheckInUseCase @Inject constructor(
    private val repository: PainCheckInRepository
) {
    suspend operator fun invoke(
        sessionId  : Int,
        evaScore   : Int,
        hoursAfter : Int,
        bodyZones  : List<String>
    ): Resource<PainCheckIn> {
        if (evaScore !in 0..10)
            return Resource.Error("La puntuación EVA debe estar entre 0 y 10.")
        return repository.submitCheckIn(sessionId, evaScore, hoursAfter, bodyZones)
    }
}