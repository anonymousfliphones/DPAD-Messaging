package com.dpad.messaging.models

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.encodeToString

@Serializable
data class MmsAttachment(
    val uri: String,
    val mimeType: String = "",
    val fileName: String = ""
) {
    val contentUri: Uri get() = Uri.parse(uri)
}

object MmsAttachmentJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(attachments: List<MmsAttachment>): String {
        return json.encodeToString(attachments)
    }

    fun decode(raw: String): List<MmsAttachment> {
        val value = raw.trim()
        if (value.isBlank() || value == "[]") return emptyList()
        return runCatching {
            val array = json.parseToJsonElement(value) as? JsonArray ?: return emptyList()
            array.mapNotNull { item ->
                when (item) {
                    is JsonObject -> json.decodeFromJsonElement<MmsAttachment>(item)
                    is JsonPrimitive -> item.content.trim().takeIf { it.isNotBlank() }?.let {
                        MmsAttachment(it, "")
                    }
                    else -> null
                }
            }
        }.getOrDefault(emptyList())
    }
}
