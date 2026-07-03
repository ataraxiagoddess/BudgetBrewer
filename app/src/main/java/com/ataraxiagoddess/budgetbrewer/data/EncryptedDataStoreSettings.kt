package com.ataraxiagoddess.budgetbrewer.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.RegistryConfiguration
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class EncryptedDataStoreSettings private constructor(
    private val context: Context,
    private val name: String
) : Settings {

    private val aead: Aead by lazy { getOrCreateAead() }

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("encrypted_$name") }
    )

    private fun getOrCreateAead(): Aead {
        AeadConfig.register()

        val keysetManager = AndroidKeysetManager.Builder()
            .withSharedPref(context, "tink_keyset", name)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://tink_master_key_$name")
            .build()

        return keysetManager.keysetHandle.getPrimitive(
            RegistryConfiguration.get(),
            Aead::class.java)
    }

    // ----- Helper to read encrypted values -----

    private suspend fun getEncryptedString(key: String): String? {
        val encryptedBase64 = dataStore.data.first()[stringPreferencesKey(key)]
        return if (encryptedBase64 == null) null
        else {
            val decrypted = aead.decrypt(android.util.Base64.decode(encryptedBase64, android.util.Base64.DEFAULT), null)
            String(decrypted)
        }
    }

    private suspend fun putEncryptedString(key: String, value: String?) {
        dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(stringPreferencesKey(key))
            } else {
                val encrypted = aead.encrypt(value.toByteArray(), null)
                prefs[stringPreferencesKey(key)] = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT)
            }
        }
    }

    // ----- Settings interface implementations -----

    override val keys: Set<String>
        get() = runBlocking {
            dataStore.data.first().asMap().keys.map { it.name }.toSet()
        }

    override val size: Int
        get() = keys.size

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return runBlocking {
            val raw = getEncryptedString(key)
            raw?.toBooleanStrictOrNull() ?: defaultValue
        }
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return runBlocking {
            val raw = getEncryptedString(key)
            raw?.toFloatOrNull() ?: defaultValue
        }
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return runBlocking {
            val raw = getEncryptedString(key)
            raw?.toIntOrNull() ?: defaultValue
        }
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return runBlocking {
            val raw = getEncryptedString(key)
            raw?.toLongOrNull() ?: defaultValue
        }
    }

    override fun getDouble(key: String, defaultValue: Double): Double {
        return runBlocking {
            val raw = getEncryptedString(key)
            raw?.toDoubleOrNull() ?: defaultValue
        }
    }

    override fun getString(key: String, defaultValue: String): String {
        return runBlocking {
            getEncryptedString(key) ?: defaultValue
        }
    }

    // ----- Nullable getters -----

    override fun getBooleanOrNull(key: String): Boolean? {
        return runBlocking {
            getEncryptedString(key)?.toBooleanStrictOrNull()
        }
    }

    override fun getFloatOrNull(key: String): Float? {
        return runBlocking {
            getEncryptedString(key)?.toFloatOrNull()
        }
    }

    override fun getIntOrNull(key: String): Int? {
        return runBlocking {
            getEncryptedString(key)?.toIntOrNull()
        }
    }

    override fun getLongOrNull(key: String): Long? {
        return runBlocking {
            getEncryptedString(key)?.toLongOrNull()
        }
    }

    override fun getDoubleOrNull(key: String): Double? {
        return runBlocking {
            getEncryptedString(key)?.toDoubleOrNull()
        }
    }

    override fun getStringOrNull(key: String): String? {
        return runBlocking {
            getEncryptedString(key)
        }
    }

    // ----- Put methods -----

    override fun putBoolean(key: String, value: Boolean) {
        runBlocking { putEncryptedString(key, value.toString()) }
    }

    override fun putFloat(key: String, value: Float) {
        runBlocking { putEncryptedString(key, value.toString()) }
    }

    override fun putInt(key: String, value: Int) {
        runBlocking { putEncryptedString(key, value.toString()) }
    }

    override fun putLong(key: String, value: Long) {
        runBlocking { putEncryptedString(key, value.toString()) }
    }

    override fun putDouble(key: String, value: Double) {
        runBlocking { putEncryptedString(key, value.toString()) }
    }

    override fun putString(key: String, value: String) {
        runBlocking { putEncryptedString(key, value) }
    }

    // ----- Remove & clear -----

    override fun remove(key: String) {
        runBlocking { putEncryptedString(key, null) }
    }

    override fun clear() {
        runBlocking { dataStore.edit { it.clear() } }
    }

    override fun hasKey(key: String): Boolean {
        return runBlocking {
            dataStore.data.first().contains(stringPreferencesKey(key))
        }
    }

    companion object {
        fun getInstance(context: Context, name: String): EncryptedDataStoreSettings {
            return EncryptedDataStoreSettings(context.applicationContext, name)
        }
    }
}