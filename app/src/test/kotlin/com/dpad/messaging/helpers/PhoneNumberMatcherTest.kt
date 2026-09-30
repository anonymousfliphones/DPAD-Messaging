package com.dpad.messaging.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberMatcherTest {
    @Test
    fun `formatted and country-prefixed NANP numbers match`() {
        assertTrue(PhoneNumberMatcher.equivalent("+1 (555) 123-4567", "5551234567"))
    }

    @Test
    fun `different numbers do not match`() {
        assertFalse(PhoneNumberMatcher.equivalent("+1 555 123 4567", "+1 555 765 4321"))
    }

    @Test
    fun `blank numbers do not match`() {
        assertFalse(PhoneNumberMatcher.equivalent("", "5551234567"))
    }
}
