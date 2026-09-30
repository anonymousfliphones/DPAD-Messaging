// app/src/main/kotlin/com/dpad/messaging/helpers/SmsWhitelistManager.kt
package com.dpad.messaging.helpers

import android.content.Context
import android.content.RestrictionsManager

/**
 * Reads SMS/MMS whitelist and blocklist from MDM-pushed Application Restrictions.
 *
 * The launcher pushes these via DevicePolicyManager.setApplicationRestrictions().
 * Keys:
 *   "sms_allowed_numbers"  - CSV of allowed sender numbers (whitelist mode)
 *   "sms_blocked_numbers"  - CSV of blocked sender numbers (blocklist mode)
 *   "sms_filter_mode"      - "whitelist" | "blocklist" | "off" (default: "off")
 *
 * Whitelist mode: ONLY numbers in the list can send messages through.
 * Blocklist mode: numbers in the list are silently dropped.
 * Off: no filtering (default).
 */
object SmsWhitelistManager {
    const val KEY_ALLOWED = "sms_allowed_numbers"
    const val KEY_BLOCKED = "sms_blocked_numbers"
    const val KEY_MODE = "sms_filter_mode"

    enum class FilterMode { OFF, WHITELIST, BLOCKLIST }

    data class FilterResult(val allowed: Boolean, val reason: String)

    fun check(context: Context, address: String): FilterResult {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return FilterResult(true, "no restrictions manager")
        val bundle = rm.applicationRestrictions ?: return FilterResult(true, "no restrictions bundle")
        val mode = when (bundle.getString(KEY_MODE, "off")?.lowercase()) {
            "whitelist" -> FilterMode.WHITELIST
            "blocklist" -> FilterMode.BLOCKLIST
            else -> FilterMode.OFF
        }

        if (mode == FilterMode.OFF) return FilterResult(true, "filtering off")

        return when (mode) {
            FilterMode.WHITELIST -> {
                val allowed = parseNumbers(bundle.getString(KEY_ALLOWED, ""))
                if (allowed.any { it == "*" || PhoneNumberMatcher.equivalent(it, address) })
                    FilterResult(true, "whitelist pass")
                else
                    FilterResult(false, "not in whitelist")
            }

            FilterMode.BLOCKLIST -> {
                val blocked = parseNumbers(bundle.getString(KEY_BLOCKED, ""))
                if (blocked.any { PhoneNumberMatcher.equivalent(it, address) })
                    FilterResult(false, "blocklist hit")
                else
                    FilterResult(true, "not in blocklist")
            }

            FilterMode.OFF -> error("unreachable") // handled by early return above
        }
    }

    private fun parseNumbers(csv: String?): List<String> =
        csv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
