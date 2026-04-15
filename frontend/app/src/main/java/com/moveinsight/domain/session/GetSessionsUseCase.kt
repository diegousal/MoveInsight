package com.moveinsight.domain.session

import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.SessionDetail
import com.moveinsight.domain.repository.SessionRepository
import javax.inject.Inject

class GetSessionsUseCase @Inject constructor(
    private val repository: SessionRepository
) {
    suspend operator fun invoke(): Resource<List<SessionDetail>> =
        repository.getSessions()
}