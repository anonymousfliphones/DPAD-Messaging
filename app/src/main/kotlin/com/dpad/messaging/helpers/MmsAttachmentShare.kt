package com.dpad.messaging.helpers

import android.content.Context
import androidx.core.content.FileProvider
import com.dpad.messaging.models.MmsAttachment
import java.io.File

object MmsAttachmentShare {
    fun createUri(context: Context, attachment: MmsAttachment): android.net.Uri? {
        return runCatching {
            val directory = File(context.cacheDir, "mms_share").apply { mkdirs() }
            directory.listFiles()?.forEach { it.delete() }
            val name = attachment.fileName
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "attachment" }
            val file = File(directory, "${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(attachment.contentUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
}
