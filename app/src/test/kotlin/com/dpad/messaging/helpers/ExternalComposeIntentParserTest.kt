package com.dpad.messaging.helpers

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalComposeIntentParserTest {
    @Test
    fun parsesAllSmsAndMmsSchemes() {
        assertTrue(ExternalComposeIntentParser.supportsScheme(Uri.parse("sms:123")))
        assertTrue(ExternalComposeIntentParser.supportsScheme(Uri.parse("smsto:123")))
        assertTrue(ExternalComposeIntentParser.supportsScheme(Uri.parse("mms:123")))
        assertTrue(ExternalComposeIntentParser.supportsScheme(Uri.parse("mmsto:123")))
    }

    @Test
    fun parsesMultipleRecipientsBeforeQuery() {
        assertEquals(
            listOf("+15551234567", "15557654321"),
            ExternalComposeIntentParser.recipients(Uri.parse("sms:+15551234567,15557654321?body=hello"))
        )
    }

    @Test
    fun usesFallbackAddressForEmptyUri() {
        assertEquals(
            listOf("+15551234567"),
            ExternalComposeIntentParser.recipients(Uri.parse("smsto:"), "+15551234567")
        )
    }

    @Test
    fun prefersTextExtraAndIncludesSubject() {
        assertEquals(
            "Subject\nmessage",
            ExternalComposeIntentParser.body(
                Uri.parse("sms:123?body=uri"),
                extraText = "message",
                smsBody = "legacy",
                subject = "Subject"
            )
        )
    }

    @Test
    fun joinsRepeatedUriBodies() {
        assertEquals(
            "one\ntwo",
            ExternalComposeIntentParser.body(Uri.parse("sms:123?body=one&body=two"))
        )
    }
}
