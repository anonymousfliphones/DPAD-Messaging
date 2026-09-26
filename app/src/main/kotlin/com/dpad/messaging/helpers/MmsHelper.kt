package com.dpad.messaging.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dpad.messaging.BuildConfig
import com.dpad.messaging.models.MmsAttachment

/**
 * Utilities for reading MMS message parts from the system Telephony provider.
 *
 * All functions must be called from a background thread.
 */
object MmsHelper {

    private const val TAG = "DPAD_MSG"

    /**
     * Returns the text/plain body of an MMS message, or an empty string if none.
     */
    fun getMmsTextBody(context: Context, msgId: Long): String {
        return getCachedParts(context, msgId).textBody
    }

    /**
     * Returns a list of content URI strings for all image parts found in the MMS message.
     * Returns empty list if there are no image parts.
     */
    fun getMmsImagePartUris(context: Context, msgId: Long): List<String> {
        return getCachedParts(context, msgId).imagePartUris
    }

    fun getMmsAttachments(context: Context, msgId: Long): List<MmsAttachment> {
        return getCachedParts(context, msgId).attachments
    }

    /**
     * Returns the MIME type of the first non-text, non-image, non-SMIL part, or an
     * empty string if all parts are accounted for by text/images.
     * Useful for showing e.g. "audio/mpeg" or "video/mp4" as a fallback label.
     */
    fun getMmsAttachmentLabel(context: Context, msgId: Long): String {
        return getCachedParts(context, msgId).attachmentLabel
    }

    /**
     * Returns the best available display body for an MMS message:
     *  1. The text/plain part body, if present and non-blank.
     *  2. The subject field, if non-blank.
     *  3. The MIME type of any non-text/non-image attachment (e.g. "audio/mpeg").
     *  4. "MMS" as a last resort.
     */
    fun getMmsDisplayBody(context: Context, msgId: Long, subject: String): String {
        val textBody = getMmsTextBody(context, msgId)
        if (textBody.isNotBlank()) return textBody
        if (subject.isNotBlank()) return subject
        if (getMmsImagePartUris(context, msgId).isNotEmpty()) return ""
        val attachLabel = getMmsAttachmentLabel(context, msgId)
        if (attachLabel.isNotBlank()) return attachLabel
        return "MMS"
    }

    private fun getCachedParts(context: Context, msgId: Long): MmsPartCache.CachedParts {
        val cached = MmsPartCache.get(msgId)
        if (cached != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "getCachedParts($msgId) CACHE HIT body=${cached.textBody.length} imgs=${cached.imagePartUris.size}")
            }
            return cached
        }

        var textBody = ""
        val attachments = mutableListOf<MmsAttachment>()
        var rowCount = 0
        var querySucceeded = false

        val partsUri = Uri.parse("content://mms/$msgId/part")
        try {
            context.contentResolver.query(
                partsUri,
                arrayOf("_id", "ct", "text", "cl", "name"),
                null,
                null,
                null
            )?.use { cursor ->
                querySucceeded = true
                val idxId = cursor.getColumnIndex("_id")
                val idxCt = cursor.getColumnIndex("ct")
                val idxText = cursor.getColumnIndex("text")
                val idxContentLocation = cursor.getColumnIndex("cl")
                val idxName = cursor.getColumnIndex("name")

                if (idxId < 0 || idxCt < 0) {
                    Log.w(TAG, "getCachedParts($msgId) missing required part columns")
                    querySucceeded = false
                    return@use
                }

                while (cursor.moveToNext()) {
                    rowCount++
                    val rawCt = cursor.getString(idxCt)
                    if (rawCt.isNullOrBlank()) {
                        Log.w(TAG, "getCachedParts($msgId) part has no content type")
                        continue
                    }
                    val ct = rawCt.substringBefore(';').trim().lowercase()

                    if (ct == "text/plain") {
                        val partText = cursor.getString(idxText).orEmpty()
                        if (partText.isNotBlank()) {
                            if (textBody.isNotBlank()) textBody += "\n"
                            textBody += partText
                        }
                    }

                    if (ct != "text/plain" && ct != "application/smil") {
                        val partId = cursor.getLong(idxId)
                        val contentLocation = if (idxContentLocation >= 0) {
                            cursor.getString(idxContentLocation).orEmpty()
                        } else {
                            ""
                        }
                        val name = if (idxName >= 0) cursor.getString(idxName).orEmpty() else ""
                        attachments.add(
                            MmsAttachment(
                                uri = "content://mms/part/$partId",
                                mimeType = ct,
                                fileName = bestFileName(name, contentLocation, ct)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCachedParts($msgId) QUERY FAILED", e)
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "getCachedParts($msgId) rows=$rowCount bodyLen=${textBody.length} " +
                    "attachments=${attachments.size}"
            )
        }

        return MmsPartCache.CachedParts(
            textBody = textBody,
            attachments = attachments
        ).also { if (querySucceeded) MmsPartCache.put(msgId, it) }
    }

    private fun bestFileName(name: String, contentLocation: String, mimeType: String): String {
        val candidate = name.ifBlank { contentLocation.substringAfterLast('/').substringBefore('?') }
        if (candidate.isNotBlank() && candidate != ".") return candidate
        return when {
            mimeType.startsWith("image/") -> "image"
            mimeType.startsWith("audio/") -> "audio"
            mimeType.startsWith("video/") -> "video"
            else -> "attachment"
        }
    }

}
