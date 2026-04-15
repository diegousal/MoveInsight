package com.moveinsight.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moveinsight.core.notifications.NotificationHelper
import com.moveinsight.core.utils.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker que se ejecuta en background a las 24h y 48h post-sesión
 * para mostrar la notificación de check-in de dolor.
 */
@HiltWorker
class PainCheckInWorker @AssistedInject constructor(
    @Assisted private val context    : Context,
    @Assisted private val workerParams: WorkerParameters,
    private   val notificationHelper  : NotificationHelper
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val sessionId  = inputData.getInt(Constants.WORKER_SESSION_ID_KEY, -1)
        val hoursAfter = inputData.getInt(Constants.WORKER_HOURS_KEY, 24)

        if (sessionId == -1) return Result.failure()

        notificationHelper.showPainCheckInNotification(sessionId, hoursAfter)
        return Result.success()
    }
}