package com.moveinsight.domain.repository

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.ReadinessBreakdown
import javax.inject.Inject

class GetReadinessBreakdownUseCase @Inject constructor(
    private val repository: AnalyticsRepository
) {
    suspend operator fun invoke(): Resource<ReadinessBreakdown> =
        repository.getReadinessBreakdown()
}
