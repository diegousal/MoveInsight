package com.moveinsight.domain.repository

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Analytics
import com.moveinsight.domain.repository.AnalyticsRepository
import javax.inject.Inject

class GetAnalyticsUseCase @Inject constructor(
    private val repository: AnalyticsRepository
) {
    suspend operator fun invoke(): Resource<Analytics> =
        repository.getAnalytics()
}