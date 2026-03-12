package com.ataraxiagoddess.budgetbrewer.util

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import java.security.SecureRandom

object AppLockManager {
    private const val PREFS_NAME = "app_lock"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_ENABLED = "pin_enabled"
    private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"

    @Volatile
    var isUnlocked = false
        private set

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
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
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        prefs.edit {
            putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, salt)
        }
    }

    fun verifyPin(pin: String): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        return hashPin(pin, salt) == hash
    }

    fun clearPin() {
        prefs.edit {
            remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
        }
    }

    fun unlock() { isUnlocked = true }
    fun lock() { isUnlocked = false }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = pin + salt
        val bytes = digest.digest(salted.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}