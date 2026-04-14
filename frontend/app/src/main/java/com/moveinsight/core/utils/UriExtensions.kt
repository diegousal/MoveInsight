package com.moveinsight.core.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copia el contenido de un Uri (file picker) a un .mp4 en el directorio
 * de caché de la app. Debe llamarse siempre desde un dispatcher IO.
 */
suspend fun Uri.toTempFile(context: Context): File = withContext(Dispatchers.IO) {
    val destFile = File(context.cacheDir, "mi_upload_${System.currentTimeMillis()}.mp4")
    context.contentResolver.openInputStream(this@toTempFile)?.use { input ->
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    destFile
}

/**
 * Devuelve el nombre de visualización del archivo apuntado por el Uri.
 * Usa el ContentResolver para leer OpenableColumns.DISPLAY_NAME.
 */
fun Uri.getDisplayName(context: Context): String {
    return context.contentResolver
        .query(this, null, null, null, null)
        ?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        } ?: "video_seleccionado.mp4"
}