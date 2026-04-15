package com.moveinsight.domain.repository

import android.content.Context
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.repository.AnalyticsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class ExportReportUseCase @Inject constructor(
    private val repository: AnalyticsRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(): Resource<File> {
        val dest = File(context.cacheDir, "moveinsight_report_${System.currentTimeMillis()}.pdf")
        return repository.exportPdf(dest)
    }
}