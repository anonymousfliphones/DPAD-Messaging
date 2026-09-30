package com.dpad.messaging.helpers

import android.telephony.PhoneNumberUtils

/** Centralized phone-number equivalence for blocking and policy decisions. */
object PhoneNumberMatcher {
    fun equivalent(left: String, right: String): Boolean {
        if (left.isBlank() || right.isBlank()) return false
        if (left.trim() == "*" || right.trim() == "*") return left.trim() == right.trim()
        if (runCatching { PhoneNumberUtils.compare(left, right) }.getOrDefault(false)) return true

        val leftDigits = normalizeDigits(left)
        val rightDigits = normalizeDigits(right)
        if (leftDigits.isBlank() || rightDigits.isBlank()) return false
        if (leftDigits == rightDigits) return true

        // Preserve the existing NANP behavior without applying it to short codes.
        return leftDigits.length >= 10 && rightDigits.length >= 10 &&
            leftDigits.takeLast(10) == rightDigits.takeLast(10)
    }

    private fun normalizeDigits(number: String): String {
        return runCatching { PhoneNumberUtils.normalizeNumber(number) }
            .getOrElse { number.filter(Char::isDigit) }
    }
}
