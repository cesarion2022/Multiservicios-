package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.tqmDataStore: DataStore<Preferences> by preferencesDataStore(name = "ibague_tqm_prefs")

class PreferencesManager(private val context: Context) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val compactDensityKey = booleanPreferencesKey("compact_density")
    private val e2eEncryptionKey = stringPreferencesKey("e2e_key_hex")

    val themeModeFlow: Flow<String> = context.tqmDataStore.data.map { prefs ->
        prefs[themeModeKey] ?: "DARK"
    }

    val compactDensityFlow: Flow<Boolean> = context.tqmDataStore.data.map { prefs ->
        prefs[compactDensityKey] ?: false
    }

    suspend fun setThemeMode(mode: String) {
        context.tqmDataStore.edit { prefs ->
            prefs[themeModeKey] = mode
        }
    }

    suspend fun setCompactDensity(compact: Boolean) {
        context.tqmDataStore.edit { prefs ->
            prefs[compactDensityKey] = compact
        }
    }

    suspend fun getOrCreateWorkspaceKeyHex(): String {
        var existingHex = ""
        context.tqmDataStore.edit { prefs ->
            val current = prefs[e2eEncryptionKey]
            if (current.isNullOrBlank()) {
                val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
                val keyBytes = keyGen.generateKey().encoded
                val hex = keyBytes.joinToString("") { "%02x".format(it) }
                prefs[e2eEncryptionKey] = hex
                existingHex = hex
            } else {
                existingHex = current
            }
        }
        return existingHex
    }

    companion object {
        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun generateOneTimeCode(): String {
            val random = SecureRandom()
            val num = 100000 + random.nextInt(900000)
            return num.toString()
        }

        fun encryptAesGcm(plainText: String, keyHex: String): String {
            if (plainText.isBlank() || keyHex.length < 32) return plainText
            return try {
                val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val secretKey: SecretKey = SecretKeySpec(keyBytes.copyOf(32), "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val ivHex = iv.joinToString("") { "%02x".format(it) }
                val dataHex = encrypted.joinToString("") { "%02x".format(it) }
                "ENC:$ivHex:$dataHex"
            } catch (_: Exception) {
                plainText
            }
        }
    }
}
