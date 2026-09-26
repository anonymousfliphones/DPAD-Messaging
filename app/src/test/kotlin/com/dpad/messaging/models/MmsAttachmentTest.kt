package com.dpad.messaging.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsAttachmentTest {
    @Test
    fun encodeAndDecodePreservesAttachmentMetadata() {
        val original = listOf(
            MmsAttachment(
                uri = "content://mms/part/12",
                mimeType = "audio/mpeg",
                fileName = "voice.mp3"
            ),
            MmsAttachment(
                uri = "content://mms/part/13",
                mimeType = "image/jpeg",
                fileName = "photo.jpg"
            )
        )

        assertEquals(original, MmsAttachmentJson.decode(MmsAttachmentJson.encode(original)))
    }

    @Test
    fun decodeAcceptsLegacyUriArrays() {
        val attachments = MmsAttachmentJson.decode("[\"content://mms/part/7\"]")

        assertEquals(1, attachments.size)
        assertEquals("content://mms/part/7", attachments.single().uri)
        assertTrue(attachments.single().mimeType.isBlank())
    }
}
