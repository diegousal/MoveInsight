package com.moveinsight.domain.repository


import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.Analytics
import com.moveinsight.domain.model.SessionDetail
import java.io.File

interface AnalyticsRepository {
    suspend fun getSessions()                      : Resource<List<SessionDetail>>
    suspend fun getSessionDetail(id: Int)          : Resource<SessionDetail>
    suspend fun getAnalytics()                     : Resource<Analytics>
    suspend fun exportPdf(destFile: File)          : Resource<File>
}