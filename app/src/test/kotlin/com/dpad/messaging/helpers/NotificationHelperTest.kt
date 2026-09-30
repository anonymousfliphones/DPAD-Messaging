package com.dpad.messaging.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHelperTest {
    @Test
    fun threadNotificationIdIsStableAndPositive() {
        val id = NotificationHelper.threadNotificationId(9_876_543_210L)
        assertEquals(id, NotificationHelper.threadNotificationId(9_876_543_210L))
        assertTrue(id > 0)
    }

    @Test
    fun failureNotificationIdUsesSeparateRange() {
        assertTrue(NotificationHelper.failureNotificationId(42L) >= 1_000_000_000)
    }
}
