package com.dpad.messaging.helpers

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.withTransaction
import com.dpad.messaging.App
import com.dpad.messaging.models.BackupData
import com.dpad.messaging.models.BackupPreferences
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Base64
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BackupManager {

    private const val KEYSTORE_ALIAS = "dpad_backup_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    /**
     * Exports the app's local data as an encrypted JSON string.
     *
     * Scope: this ONLY backs up data stored in the app's own Room database —
     * scheduled messages, drafts, recycle-bin items, attachments, blocked
     * keyword/number lists, conversation metadata, and app preferences.
     * The on-device SMS/MMS history lives in the system Telephony provider and
     * is NOT exported (thread contents are re-read from the provider at runtime).
     *
     * The encryption key is stored in Android KeyStore and is device-backed.
     * The returned string is base64(IV + ciphertext) and can be safely written
     * to external storage.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun backup(context: Context): String {
        val data = collectData()

        val plaintext = json.encodeToString(data)
        return encrypt(plaintext)
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun backupToUri(context: Context, uri: Uri, onProgress: suspend (Int) -> Unit) {
        val data = collectData()
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("Unable to open backup destination")
        output.use { raw ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            raw.write(cipher.iv)
            onProgress(20)
            CipherOutputStream(raw, cipher).use { encrypted ->
                encrypted.write(json.encodeToString(data).toByteArray(Charsets.UTF_8))
            }
        }
        onProgress(100)
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreFromUri(context: Context, uri: Uri, onProgress: suspend (Int) -> Unit): BackupResult {
        val input = context.contentResolver.openInputStream(uri)
            ?: return BackupResult(false, "Unable to open backup file")
        return input.use { raw ->
            try {
                val iv = raw.readExactBytes(GCM_IV_LENGTH)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
                onProgress(20)
                val data = CipherInputStream(raw, cipher).use { encrypted ->
                    json.decodeFromString<BackupData>(encrypted.bufferedReader().use { it.readText() })
                }
                if (data.version != 1) {
                    return@use BackupResult(false, "Unsupported backup version: ${data.version}")
                }
                onProgress(60)
                restoreData(data)
            } catch (e: Exception) {
                BackupResult(false, "Could not restore backup: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun preview(context: Context, uri: Uri): BackupPreview {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open backup file")
        return input.use { raw ->
            val iv = raw.readExactBytes(GCM_IV_LENGTH)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            CipherInputStream(raw, cipher).use { encrypted ->
                val data = json.decodeFromString<BackupData>(encrypted.bufferedReader().use { it.readText() })
                BackupPreview(data.timestamp, data.messages.size, data.conversations.size, data.attachments.size)
            }
        }
    }

    private suspend fun collectData(): BackupData {
        val db = App.get().database
        val prefs = Prefs.get()
        return BackupData(
            timestamp = System.currentTimeMillis(),
            conversations = db.conversationsDao().getConversations(),
            messages = db.messagesDao().getAllMessages(),
            attachments = db.attachmentsDao().getAllAttachments(),
            drafts = db.draftsDao().getAllDrafts(),
            recycleBinMessages = db.messagesDao().getRecycleBinMessages(),
            blockedKeywords = db.blockedKeywordsDao().getAll(),
            blockedNumbers = db.blockedNumbersDao().getAll(),
            preferences = BackupPreferences(
                deliveryReports = prefs.deliveryReports,
                sendOnEnter = prefs.sendOnEnter,
                characterCounter = prefs.characterCounter,
                sendGroupMessageMms = prefs.sendGroupMessageMms,
                useLibrarySmsSending = prefs.useLibrarySmsSending,
                recycleBinEnabled = prefs.recycleBinEnabled,
                lockScreenPrivacy = prefs.lockScreenPrivacy,
                appThemeMode = prefs.appThemeMode,
                appAccent = prefs.appAccent,
                dateFormat = prefs.dateFormat,
                timeFormat = prefs.timeFormat,
                uiScale = prefs.uiScale,
                mutedThreads = prefs.getMutedThreadIds().map { it.toString() }.toSet(),
                pinnedThreads = prefs.getPinnedThreadIds().map { it.toString() }.toSet(),
                archivedThreads = prefs.getArchivedThreadIds().map { it.toString() }.toSet()
            )
        )
    }

    /**
     * Restores app-local data (same scope as [backup]) from an encrypted backup
     * file, replacing current data.  Room writes are applied atomically inside a
     * single database transaction; the preferences are applied afterwards.
     * Backups are device-bound (AndroidKeyStore key), so they can only be
     * restored on the device that created them.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun restore(context: Context, backupJson: String): BackupResult {
        val decrypted = try {
            decrypt(backupJson)
        } catch (e: Exception) {
            return BackupResult(false, "Could not decrypt backup: ${e.message}")
        }

        val data = try {
            json.decodeFromString<BackupData>(decrypted)
        } catch (e: Exception) {
            return BackupResult(false, "Invalid backup file: ${e.message}")
        }

        if (data.version != 1) {
            return BackupResult(false, "Unsupported backup version: ${data.version}")
        }

        return restoreData(data)
    }

    private suspend fun restoreData(data: BackupData): BackupResult {
        val db = App.get().database
        val prefs = Prefs.get()
        try {
            // All Room writes happen inside a single transaction so a failure
            // midway leaves the database unchanged rather than half-restored.
            db.withTransaction {
                db.conversationsDao().deleteAllConversations()
                db.messagesDao().deleteAllMessages()
                db.attachmentsDao().deleteAllAttachments()
                db.draftsDao().deleteAllDrafts()
                db.messagesDao().emptyRecycleBin()
                db.blockedKeywordsDao().deleteAll()
                db.blockedNumbersDao().deleteAll()

                if (data.conversations.isNotEmpty()) {
                    db.conversationsDao().insertConversations(data.conversations)
                }
                if (data.messages.isNotEmpty()) {
                    db.messagesDao().insertMessages(data.messages)
                }
                if (data.attachments.isNotEmpty()) {
                    db.attachmentsDao().insertAttachments(data.attachments)
                }
                for (draft in data.drafts) {
                    db.draftsDao().insertDraft(draft)
                }
                for (msg in data.recycleBinMessages) {
                    db.messagesDao().insertRecycleBinMessage(msg)
                }
                for (kw in data.blockedKeywords) {
                    db.blockedKeywordsDao().insert(kw)
                }
                for (num in data.blockedNumbers) {
                    db.blockedNumbersDao().insert(num)
                }
            }

            val p = data.preferences
            prefs.deliveryReports = p.deliveryReports
            prefs.sendOnEnter = p.sendOnEnter
            prefs.characterCounter = p.characterCounter
            prefs.sendGroupMessageMms = p.sendGroupMessageMms
            prefs.useLibrarySmsSending = p.useLibrarySmsSending
            prefs.recycleBinEnabled = p.recycleBinEnabled
            prefs.lockScreenPrivacy = p.lockScreenPrivacy
            prefs.appThemeMode = p.appThemeMode
            prefs.appAccent = p.appAccent
            prefs.dateFormat = p.dateFormat
            prefs.timeFormat = p.timeFormat
            prefs.uiScale = p.uiScale
            for (id in p.mutedThreads) {
                id.toLongOrNull()?.let { prefs.setThreadMuted(it, true) }
            }
            for (id in p.pinnedThreads) {
                id.toLongOrNull()?.let { prefs.setThreadPinned(it, true) }
            }
            for (id in p.archivedThreads) {
                id.toLongOrNull()?.let { prefs.setThreadArchived(it, true) }
            }

            return BackupResult(true, "Restored ${data.messages.size} messages, ${data.conversations.size} conversations")
        } catch (e: Exception) {
            return BackupResult(false, "Restore failed: ${e.message}")
        }
    }

    // ── Encryption helpers ────────────────────────────────────────────────────

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(ciphertextB64: String): String {
        val key = getOrCreateKey()
        val raw = Base64.decode(ciphertextB64, Base64.DEFAULT)
        val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
        val ct = raw.copyOfRange(GCM_IV_LENGTH, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}

private fun InputStream.readExactBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(bytes, offset, count - offset)
        if (read < 0) error("Invalid backup header")
        offset += read
    }
    return bytes
}

data class BackupPreview(
    val timestamp: Long,
    val messageCount: Int,
    val conversationCount: Int,
    val attachmentCount: Int
)

data class BackupResult(
    val success: Boolean,
    val message: String
)
