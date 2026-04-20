package com.moveinsight.core.notifications

import android.content.Context
import androidx.work.*
import com.moveinsight.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleWeeklySummaryUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Programa (o reemplaza) un trabajo periódico semanal que se dispara
     * cada domingo a las 19:00. Usa [ExistingPeriodicWorkPolicy.KEEP]
     * para no reiniciar el temporizador si ya está programado.
     */
    operator fun invoke() {
        val delay = calcDelayToNextSunday19h()

        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(Constants.WORK_WEEKLY_SUMMARY)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Constants.WORK_WEEKLY_SUMMARY,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Cancela el resumen semanal (p. ej. al cerrar sesión). */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(Constants.WORK_WEEKLY_SUMMARY)
    }

    /**
     * Dispara el worker en 15 segundos para pruebas de desarrollo.
     * NO llamar en producción.
     */
    fun triggerTest() {
        val request = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
            .setInitialDelay(15L, TimeUnit.SECONDS)
            .addTag("weekly_summary_test")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Calcula los milisegundos hasta el próximo domingo a las 19:00.
     * Si ya es domingo y son las 19:00 o más tarde, apunta al siguiente domingo.
     */
    private fun calcDelayToNextSunday19h(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Si el domingo ya pasó esta semana, ir al siguiente
            if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
