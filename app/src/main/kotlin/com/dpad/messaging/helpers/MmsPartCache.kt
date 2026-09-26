package com.dpad.messaging.helpers

import android.util.LruCache
import com.dpad.messaging.models.MmsAttachment

/**
 * Tiny in-memory cache for MMS part lookups.
 *
 * Avoids repeated ContentProvider queries for the same message while
 * browsing threads.
 */
object MmsPartCache {

    data class CachedParts(
        val textBody: String,
        val attachments: List<MmsAttachment>
    ) {
        val imagePartUris: List<String>
            get() = attachments.filter { it.mimeType.startsWith("image/") }.map { it.uri }

        val attachmentLabel: String
            get() = attachments.firstOrNull { !it.mimeType.startsWith("image/") }
                ?.mimeType.orEmpty()
    }

    private val cache = LruCache<Long, CachedParts>(256)

    @Synchronized
    fun get(msgId: Long): CachedParts? = cache.get(msgId)

    @Synchronized
    fun put(msgId: Long, value: CachedParts) {
        cache.put(msgId, value)
    }

    @Synchronized
    fun remove(msgId: Long) {
        cache.remove(msgId)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }
}
