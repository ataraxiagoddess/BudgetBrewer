package com.ataraxiagoddess.budgetbrewer.ui.lock

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.ataraxiagoddess.budgetbrewer.MainActivity
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.databinding.ActivityLockBinding
import com.ataraxiagoddess.budgetbrewer.util.AppLockManager

class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBiometricPrompt()
        updateUi()

        binding.btnUnlock.setOnClickListener {
            val pin = binding.etPin.text.toString()
            if (pin.length == 4 && pin.all { it.isDigit() }) {
                if (AppLockManager.verifyPin(pin)) {
                    unlockAndProceed()
                } else {
                    Toast.makeText(this, R.string.incorrect_pin, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.enter_valid_pin, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBiometric.setOnClickListener {
            showBiometricPrompt()
        }

        binding.btnCancel.setOnClickListener {
            finishAffinity()
        }

        binding.btnTogglePassword.setOnClickListener {
            val et = binding.etPin
            val current = et.inputType
            if (current == (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)) {
                et.inputType = InputType.TYPE_CLASS_NUMBER
                binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_on)
            } else {
                et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_off)
            }
            // Re‑apply custom font
            et.typeface = ResourcesCompat.getFont(this, R.font.exo_regular)
            et.text?.let { et.setSelection(it.length) }
        }
    }

    private fun updateUi() {
        if (AppLockManager.isBiometricsEnabled() && isBiometricAvailable()) {
            binding.btnBiometric.visibility = View.VISIBLE
            // Auto‑show biometric prompt
            showBiometricPrompt()
        } else {
            binding.btnBiometric.visibility = View.GONE
        }
    }

    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlockAndProceed()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    Toast.makeText(this@LockActivity, errString, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                Toast.makeText(this@LockActivity, R.string.biometric_failed, Toast.LENGTH_SHORT).show()
            }
        }
        biometricPrompt = BiometricPrompt(this, executor, callback)
    }

    private fun showBiometricPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(getString(R.string.cancel))
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun unlockAndProceed() {
        AppLockManager.unlock()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}