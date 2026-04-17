package com.moveinsight.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moveinsight.MainActivity
import com.moveinsight.R
import com.moveinsight.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Crea el canal de notificación (obligatorio en Android 8+).
     * Llamar desde Application.onCreate().
     */
    fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Recordatorios para el seguimiento del dolor post-entrenamiento"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * Muestra la notificación de check-in.
     * El tap abre la app con deep link a la pantalla EVA.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showPainCheckInNotification(sessionId: Int, hoursAfter: Int) {
        val deepLinkUri = Uri.parse(
            "${Constants.DEEP_LINK_SCHEME}://${Constants.DEEP_LINK_HOST_CHECKIN}/$sessionId?hours=$hoursAfter"
        )

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, deepLinkUri, context, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }

        val pendingIntent = PendingIntent.getActivity(
            context,
            sessionId + hoursAfter,       // requestCode único
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, body) = when (hoursAfter) {
            24   -> "¿Cómo estás a las 24h?" to "Registra tu nivel de dolor para optimizar tu readiness"
            else -> "Seguimiento 48h post-entrenamiento" to "¿Persiste alguna molestia? Registra cómo te sientes"
        }

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = if (hoursAfter == 24)
            Constants.PAIN_NOTIFICATION_ID_24H
        else
            Constants.PAIN_NOTIFICATION_ID_48H

        // POST_NOTIFICATIONS permission se pide en runtime (Android 13+)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) { /* Permiso no concedido */ }
    }
}