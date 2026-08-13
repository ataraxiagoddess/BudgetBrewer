/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.util

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Ties biometric unlock to a hardware-backed Keystore key.
 * The key requires per-use biometric auth, so a cipher built from it
 * can only complete an operation after a genuine BiometricPrompt.
 */
object BiometricCryptoManager {
    private const val KEY_ALIAS = "budget_brewer_biometric_unlock"

    /** A cipher bound to the Keystore key, or null if unavailable (fall back to PIN). */
    fun createCipher(): Cipher? =
        runCatching {
            newCipher(getOrCreateKey())
        }.recoverCatching { e ->
            if (e is KeyPermanentlyInvalidatedException) {
                // Biometric enrollment changed since the key was created: start fresh.
                deleteKey()
                newCipher(getOrCreateKey())
            } else {
                throw e
            }
        }.getOrNull()

    /** True only if the Keystore released the key — i.e. real biometric auth happened. */
    fun probe(cipher: Cipher): Boolean =
        runCatching {
            cipher.doFinal(byteArrayOf(0))
            true
        }.getOrDefault(false)

    private fun newCipher(key: SecretKey): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val spec =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                buildStrongBiometricKeySpec()
            } else {
                buildLegacyKeySpec()
            }

        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildStrongBiometricKeySpec(): KeyGenParameterSpec =
        /**
         * SonarLint S6291 false positive: setUserAuthenticationParameters(0,
         * AUTH_BIOMETRIC_STRONG) is the API-30 replacement for
         * setUserAuthenticationRequired(true), which this rule doesn't recognize.
         * We intentionally keep STRONG-only binding so the key is never released on
         * device-credential auth alone.
         */
        KeyGenParameterSpec
            .Builder( // NOSONAR
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setInvalidatedByBiometricEnrollment(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .build()

    @Suppress("DEPRECATION")
    private fun buildLegacyKeySpec(): KeyGenParameterSpec =
        KeyGenParameterSpec
            .Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setInvalidatedByBiometricEnrollment(true)
            .setUserAuthenticationRequired(true)
            .build()

    private fun deleteKey() =
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
}
