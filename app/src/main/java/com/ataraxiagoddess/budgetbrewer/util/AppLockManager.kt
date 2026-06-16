package com.ataraxiagoddess.budgetbrewer.util

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPinEnabled(): Boolean = prefs.getBoolean(KEY_PIN_ENABLED, false)
    fun setPinEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_PIN_ENABLED, enabled) }

    fun isBiometricsEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRICS_ENABLED, false)
    fun setBiometricsEnabled(enabled: Boolean) = prefs.edit {
        putBoolean(
            KEY_BIOMETRICS_ENABLED,
            enabled
        )
    }

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = generateSaltHex()
        val hash = hashPinPbkdf2(pin, salt)
        prefs.edit {
            putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, salt)
                .putInt(KEY_PIN_KDF_VERSION, PIN_KDF_VERSION_PBKDF2)
        }
    }

    fun verifyPin(pin: String): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val version = prefs.getInt(KEY_PIN_KDF_VERSION, 1)

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
        prefs.edit {
            remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .remove(KEY_PIN_KDF_VERSION)
        }
    }

    fun unlock() {
        isUnlocked = true
        prefs.edit { putLong(KEY_LAST_BACKGROUND_AT, 0L) }
    }

    fun lock() { isUnlocked = false }

    fun markBackgrounded() {
        prefs.edit { putLong(KEY_LAST_BACKGROUND_AT, System.currentTimeMillis()) }
    }

    fun shouldRequireUnlock(graceMs: Long): Boolean {
        if (!isPinEnabled()) return false
        if (!isUnlocked) return true

        val lastBg = prefs.getLong(KEY_LAST_BACKGROUND_AT, 0L)
        if (lastBg <= 0L) return false

        val elapsed = System.currentTimeMillis() - lastBg
        return elapsed > graceMs
    }

    private fun hashPinLegacy(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = pin + salt
        val bytes = digest.digest(salted.toByteArray())
        return bytesToHex(bytes)
    }

    private fun hashPinPbkdf2(pin: String, saltHex: String): String {
        val salt = hexToBytes(saltHex)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val factory = try {
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

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

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
