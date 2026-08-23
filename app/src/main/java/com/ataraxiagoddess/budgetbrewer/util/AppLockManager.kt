/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLockManager {
    private const val PREFS_NAME = "app_lock"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_KDF_VERSION = "pin_kdf_version"
    private const val KEY_PIN_ENABLED = "pin_enabled"
    private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
    private const val KEY_LAST_BACKGROUND_AT = "last_background_at"

    private const val PIN_KDF_VERSION_PBKDF2 = 2
    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_KEY_LENGTH_BITS = 256

    @Volatile
    var isUnlocked = false
        private set

    private lateinit var aead: Aead
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    fun init(context: Context) {
        AeadConfig.register()
        val keysetManager =
            AndroidKeysetManager
                .Builder()
                .withSharedPref(context, "tink_keyset", PREFS_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://tink_master_key_$PREFS_NAME")
                .build()
        aead =
            keysetManager.keysetHandle.getPrimitive(
                RegistryConfiguration.get(),
                Aead::class.java,
            )

        dataStore =
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("encrypted_$PREFS_NAME") },
            )
    }

    // Helper to read/write encrypted values
    private suspend fun putEncryptedString(
        key: String,
        value: String?,
    ) {
        dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(stringPreferencesKey(key))
            } else {
                val encrypted = aead.encrypt(value.toByteArray(), null)
                prefs[stringPreferencesKey(key)] = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT)
            }
        }
    }

    private suspend fun getEncryptedString(key: String): String? {
        val encryptedBase64 = dataStore.data.first()[stringPreferencesKey(key)]
        return if (encryptedBase64 == null) {
            null
        } else {
            val decrypted = aead.decrypt(android.util.Base64.decode(encryptedBase64, android.util.Base64.DEFAULT), null)
            String(decrypted)
        }
    }

    fun isPinEnabled(): Boolean = runBlocking { getEncryptedString(KEY_PIN_ENABLED) == "true" }

    fun setPinEnabled(enabled: Boolean) {
        runBlocking { putEncryptedString(KEY_PIN_ENABLED, enabled.toString()) }
    }

    fun isBiometricsEnabled(): Boolean = runBlocking { getEncryptedString(KEY_BIOMETRICS_ENABLED) == "true" }

    fun setBiometricsEnabled(enabled: Boolean) {
        runBlocking { putEncryptedString(KEY_BIOMETRICS_ENABLED, enabled.toString()) }
    }

    fun hasPin(): Boolean = runBlocking { getEncryptedString(KEY_PIN_HASH) != null }

    fun setPin(pin: String) {
        val salt = generateSaltHex()
        val hash = hashPinPbkdf2(pin, salt)
        runBlocking {
            putEncryptedString(KEY_PIN_HASH, hash)
            putEncryptedString(KEY_PIN_SALT, salt)
            putEncryptedString(KEY_PIN_KDF_VERSION, PIN_KDF_VERSION_PBKDF2.toString())
        }
    }

    fun verifyPin(pin: String): Boolean {
        val hash = runBlocking { getEncryptedString(KEY_PIN_HASH) } ?: return false
        val salt = runBlocking { getEncryptedString(KEY_PIN_SALT) } ?: return false
        val version = runBlocking { getEncryptedString(KEY_PIN_KDF_VERSION) }?.toIntOrNull() ?: 1

        return if (version >= PIN_KDF_VERSION_PBKDF2) {
            hashPinPbkdf2(pin, salt) == hash
        } else {
            val legacyMatch = hashPinLegacy(pin, salt) == hash
            if (legacyMatch) {
                // Upgrade to PBKDF2 on successful legacy verification
                setPin(pin)
            }
            legacyMatch
        }
    }

    fun clearPin() {
        runBlocking {
            putEncryptedString(KEY_PIN_HASH, null)
            putEncryptedString(KEY_PIN_SALT, null)
            putEncryptedString(KEY_PIN_KDF_VERSION, null)
        }
    }

    fun unlock() {
        isUnlocked = true
        runBlocking { putEncryptedString(KEY_LAST_BACKGROUND_AT, "0") }
    }

    fun lock() {
        isUnlocked = false
    }

    fun markBackgrounded() {
        runBlocking {
            putEncryptedString(KEY_LAST_BACKGROUND_AT, System.currentTimeMillis().toString())
        }
    }

    fun shouldRequireUnlock(graceMs: Long): Boolean {
        if (!isPinEnabled()) return false
        if (!isUnlocked) return true

        val lastBgStr = runBlocking { getEncryptedString(KEY_LAST_BACKGROUND_AT) }
        val lastBg = lastBgStr?.toLongOrNull() ?: 0L
        if (lastBg <= 0L) return false

        val elapsed = System.currentTimeMillis() - lastBg
        return elapsed > graceMs
    }

    private fun hashPinLegacy(
        pin: String,
        salt: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = pin + salt
        val bytes = digest.digest(salted.toByteArray())
        return bytesToHex(bytes)
    }

    private fun hashPinPbkdf2(
        pin: String,
        saltHex: String,
    ): String {
        val salt = hexToBytes(saltHex)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val factory =
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            } catch (_: Exception) {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            }
        val bytes = factory.generateSecret(spec).encoded
        return bytesToHex(bytes)
    }

    private fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytesToHex(bytes)
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val result = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val hi = Character.digit(hex[i], 16)
            val lo = Character.digit(hex[i + 1], 16)
            result[i / 2] = ((hi shl 4) + lo).toByte()
            i += 2
        }
        return result
    }
}
