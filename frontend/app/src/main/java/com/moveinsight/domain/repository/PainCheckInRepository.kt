package com.moveinsight.domain.repository

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.PainCheckIn

interface PainCheckInRepository {
    suspend fun submitCheckIn(
        sessionId  : Int,
        evaScore   : Int,
        hoursAfter : Int,
        bodyZones  : List<String>
    ): Resource<PainCheckIn>
}