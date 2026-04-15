package com.moveinsight.domain.repository


import com.moveinsight.core.security.UserPreferencesDataStore
import com.moveinsight.core.utils.Resource
import com.moveinsight.core.utils.safeApiCall
import com.moveinsight.data.remote.PainCheckInApiService
import com.moveinsight.data.remote.PainCheckInRequestDto
import com.moveinsight.domain.model.PainCheckIn
import com.moveinsight.domain.repository.PainCheckInRepository
import java.time.LocalDate
import javax.inject.Inject

class PainCheckInRepositoryImpl @Inject constructor(
    private val api       : PainCheckInApiService,
    private val dataStore : UserPreferencesDataStore
) : PainCheckInRepository {

    override suspend fun submitCheckIn(
        sessionId  : Int,
        evaScore   : Int,
        hoursAfter : Int,
        bodyZones  : List<String>
    ): Resource<PainCheckIn> {
        val result = safeApiCall {
            api.submitPainCheckIn(
                PainCheckInRequestDto(sessionId, evaScore, hoursAfter, bodyZones)
            )
        }
        return when (result) {
            is Resource.Success -> {
                // Persistir localmente para alimentar el readiness en HomeScreen
                dataStore.saveLastEvaScore(
                    score = evaScore,
                    date  = LocalDate.now().toString()
                )
                Resource.Success(
                    PainCheckIn(
                        id         = result.data.id,
                        sessionId  = sessionId,
                        evaScore   = evaScore,
                        hoursAfter = hoursAfter,
                        bodyZones  = bodyZones
                    )
                )
            }
            is Resource.Error   -> result
            is Resource.Loading -> result
        }
    }
}