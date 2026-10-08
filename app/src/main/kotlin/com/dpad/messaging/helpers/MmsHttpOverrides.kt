package com.dpad.messaging.helpers

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import com.dpad.messaging.BuildConfig
import com.klinker.android.send_message.MmsRequestOverrides

/**
 * Per-request MMSC HTTP header overrides for both send and download.
 *
 * The platform MmsService applies the caller's configOverrides (via Bundle.putAll)
 * AFTER the carrier config and TelephonyManager UA/UAProf. Overriding
 * MMS_CONFIG_USER_AGENT and MMS_CONFIG_UA_PROF_URL is therefore sufficient —
 * no custom httpParams or Accept header needed.
 *
 * WHY this matters on Verizon: the MMSC transcodes voice notes based on the
 * RECEIVER's UAProf. Generic Android-Mms/1.0 + Google's kila UAProf → audio/vnd.qcelp
 * (unplayable). A real Verizon handset UAProf → audio/x-mpeg3 MP3 (plays). Proven
 * empirically on a TCL Flip T408DL via controlled A/B test (2026-10-08).
 *
 * Presets mirror Handcent Next SMS 11.10.0's decoded UA table, verified against the
 * Verizon MMSC live test.
 */
object MmsHttpOverrides {

    private const val TAG = "DPAD_MSG"

    const val PRESET_SYSTEM  = "system"
    const val PRESET_VERIZON = "verizon"
    const val PRESET_SAMSUNG = "samsung"
    const val PRESET_IPHONE  = "iphone"
    const val PRESET_MOTO    = "moto"

    // Proven Verizon identity: TCL Flip T408DL on TracFone/Verizon (empirical test).
    // On Verizon this profile is served by uaprof.vtext.com and triggers MP3 transcoding.
    private const val UA_VERIZON     = "T408DL-MMS/2.0"
    private const val UAPROF_VERIZON = "http://uaprof.vtext.com/alcatel/wst408dl/wst408dl.xml"

    // Samsung Galaxy S10 on Verizon (Handcent's "samsung" preset for Verizon carrier).
    // Also uses uaprof.vtext.com so it gets the same MP3 transcoding.
    private const val UA_SAMSUNG     = "samg970u"
    private const val UAPROF_SAMSUNG = "http://uaprof.vtext.com/sam/samg970u/samg970u.xml"

    // iPhone (Handcent's "iphone" preset).
    private const val UA_IPHONE      = "iPhoneOS/4.2.1 (8C148)"
    private const val UAPROF_IPHONE  = "http://iphonemms.apple.com/iphone/uaprof-2MB.rdf"

    // Motorola G7 Power (Handcent's "moto" preset).
    private const val UA_MOTO        = "motog7power"
    private const val UAPROF_MOTO    = "http://uaprof.motorola.com/phoneconfig/motov1/Profile/motov1.rdf"

    private const val VERIZON_CARRIER_ID = 1839
    private val VERIZON_MCC_MNC = setOf(
        "310004", "310010", "310012", "310013",
        "311480", "311481", "311482", "311483", "311484",
        "311485", "311486", "311487", "311488", "311489"
    )

    /** Registers the receive-side hook with the vendored library. Call once from App.onCreate(). */
    fun install() {
        MmsRequestOverrides.setProvider(object : MmsRequestOverrides.Provider {
            override fun apply(context: Context, subId: Int, configOverrides: Bundle) {
                this@MmsHttpOverrides.apply(context, subId, configOverrides)
            }

            override fun appendTransactionId(context: Context, subId: Int): Boolean = false
        })
    }

    /**
     * Writes the configured UA/UAProf overrides into [configOverrides].
     * Auto-enables when [PRESET_SYSTEM] is selected and the SIM is Verizon —
     * the stock UA is generic and breaks voice-note transcoding.
     */
    fun apply(context: Context, subId: Int, configOverrides: Bundle) {
        val prefs = Prefs.get()
        val preset = prefs.mmsUaPreset

        val (ua, uaProf) = when {
            preset == PRESET_VERIZON || (preset == PRESET_SYSTEM && isVerizonSim(context, subId)) ->
                UA_VERIZON to UAPROF_VERIZON
            preset == PRESET_SAMSUNG -> UA_SAMSUNG to UAPROF_SAMSUNG
            preset == PRESET_IPHONE  -> UA_IPHONE  to UAPROF_IPHONE
            preset == PRESET_MOTO    -> UA_MOTO    to UAPROF_MOTO
            else -> return  // PRESET_SYSTEM on non-Verizon — leave carrier values
        }

        configOverrides.putString(SmsManager.MMS_CONFIG_USER_AGENT, ua)
        configOverrides.putString(SmsManager.MMS_CONFIG_UA_PROF_URL, uaProf)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "MmsHttpOverrides: subId=$subId preset=$preset ua=$ua uaProf=$uaProf")
        }
    }

    /** True when the SIM behind [subId] is Verizon (or a Verizon MVNO like TracFone). */
    fun isVerizonSim(context: Context, subId: Int): Boolean {
        val base = context.getSystemService(TelephonyManager::class.java) ?: return false
        val tm = if (subId >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { base.createForSubscriptionId(subId) }.getOrDefault(base)
        } else base
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            runCatching { tm.simCarrierId }.getOrDefault(-1) == VERIZON_CARRIER_ID
        ) return true
        return runCatching { tm.simOperator }.getOrNull().orEmpty() in VERIZON_MCC_MNC
    }
}
