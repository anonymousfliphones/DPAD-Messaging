package com.dpad.messaging

import android.provider.Telephony
import android.app.role.RoleManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase7ProviderTest {
    @Test
    fun defaultSmsRoleHasProviderAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val isDefaultSms = context.getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_SMS) == true
        assumeTrue("DPAD must be default SMS app for provider tests", isDefaultSms)
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), null, null, null).use {
            assertNotNull(it)
        }
    }
}
