package com.dpad.messaging.models

import org.junit.Assert.assertEquals
import org.junit.Test

class MmsAttachmentJsonTest {
    @Test
    fun roundTripPreservesMultipleAttachments() {
        val attachments = listOf(
            MmsAttachment("content://media/external/images/1", "image/jpeg", "one.jpg"),
            MmsAttachment("content://media/external/audio/2", "audio/ogg", "two.ogg")
        )

        assertEquals(attachments, MmsAttachmentJson.decode(MmsAttachmentJson.encode(attachments)))
    }

    @Test
    fun legacyStringArrayRemainsReadable() {
        assertEquals(
            listOf("content://media/external/images/1"),
            MmsAttachmentJson.decode("[\"content://media/external/images/1\"]").map { it.uri }
        )
    }
}
