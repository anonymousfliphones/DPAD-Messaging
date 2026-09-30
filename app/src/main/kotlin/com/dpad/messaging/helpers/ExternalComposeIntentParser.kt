package com.dpad.messaging.helpers

import android.net.Uri

/** Shared parsing rules for SMS/MMS intents sent by Contacts and other launchers. */
object ExternalComposeIntentParser {
    private val supportedSchemes = setOf("sms", "smsto", "mms", "mmsto")

    fun supportsScheme(data: Uri): Boolean = data.scheme?.lowercase() in supportedSchemes

    fun recipients(data: Uri, fallbackAddress: String? = null): List<String> {
        val raw = data.schemeSpecificPart
            ?.removePrefix("//")
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }
            ?: fallbackAddress.orEmpty()

        return raw.split(',', ';')
            .map { Uri.decode(it).trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun body(
        data: Uri,
        extraText: String? = null,
        smsBody: String? = null,
        subject: String? = null
    ): String {
        val message = extraText?.takeIf { it.isNotBlank() }
            ?: smsBody?.takeIf { it.isNotBlank() }
            ?: queryParameters(data, "body").joinToString("\n").takeIf { it.isNotBlank() }
            ?: ""
        val title = subject?.takeIf { it.isNotBlank() } ?: return message.trim()
        return if (message.isBlank()) title.trim() else "$title\n${message.trim()}"
    }

    private fun queryParameters(data: Uri, key: String): List<String> {
        return data.encodedQuery.orEmpty()
            .split('&')
            .mapNotNull { parameter ->
                val separator = parameter.indexOf('=')
                val encodedKey = if (separator >= 0) parameter.substring(0, separator) else parameter
                if (Uri.decode(encodedKey) != key) return@mapNotNull null
                val encodedValue = if (separator >= 0) parameter.substring(separator + 1) else ""
                Uri.decode(encodedValue.replace('+', ' '))
            }
    }
}
