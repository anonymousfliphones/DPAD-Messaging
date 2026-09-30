package com.dpad.messaging.helpers

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dpad.messaging.App
import com.dpad.messaging.R

/** Durable, user-visible backup/restore work that survives Activity death. */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_URI)?.let(Uri::parse) ?: return Result.failure()
        val operation = inputData.getString(KEY_OPERATION) ?: return Result.failure()
        setForeground(createForegroundInfo(0, operation))

        return try {
            if (operation == OP_BACKUP) {
                BackupManager.backupToUri(applicationContext, uri) { progress ->
                    setForeground(createForegroundInfo(progress, operation))
                    setProgressAsync(androidx.work.workDataOf(KEY_PROGRESS to progress))
                }
            } else {
                val result = BackupManager.restoreFromUri(applicationContext, uri) { progress ->
                    setForeground(createForegroundInfo(progress, operation))
                    setProgressAsync(androidx.work.workDataOf(KEY_PROGRESS to progress))
                }
                if (!result.success) {
                    releasePermission(uri, operation)
                    showCompletion(operation, false)
                    return Result.failure(androidx.work.workDataOf(KEY_ERROR to result.message))
                }
            }
            showCompletion(operation, true)
            releasePermission(uri, operation)
            Result.success()
        } catch (e: Exception) {
            showCompletion(operation, false)
            Result.failure(androidx.work.workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    private fun createForegroundInfo(progress: Int, operation: String): ForegroundInfo {
        val title = if (operation == OP_BACKUP) R.string.backup_running else R.string.restore_running
        val notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_new_message)
            .setContentTitle(applicationContext.getString(title))
            .setContentText(applicationContext.getString(R.string.backup_scope_summary))
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun showCompletion(operation: String, success: Boolean) {
        val title = if (operation == OP_BACKUP) R.string.backup_complete else R.string.restore_complete
        val notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_new_message)
            .setContentTitle(applicationContext.getString(title))
            .setContentText(applicationContext.getString(if (success) R.string.backup_finished else R.string.backup_failed))
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun releasePermission(uri: Uri, operation: String) {
        val flags = if (operation == OP_BACKUP) {
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        } else {
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching { applicationContext.contentResolver.releasePersistableUriPermission(uri, flags) }
    }

    companion object {
        const val OP_BACKUP = "backup"
        const val OP_RESTORE = "restore"
        const val KEY_URI = "uri"
        const val KEY_OPERATION = "operation"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val NOTIFICATION_ID = 40_006

        fun enqueue(context: Context, uri: Uri, operation: String) {
            val request = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(androidx.work.workDataOf(KEY_URI to uri.toString(), KEY_OPERATION to operation))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "dpad-backup-$operation",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
