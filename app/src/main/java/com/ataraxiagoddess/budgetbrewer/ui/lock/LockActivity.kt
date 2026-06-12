package com.ataraxiagoddess.budgetbrewer.ui.lock

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.ataraxiagoddess.budgetbrewer.MainActivity
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.databinding.ActivityLockBinding
import com.ataraxiagoddess.budgetbrewer.util.AppLockManager
import com.google.android.material.snackbar.Snackbar

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
                    showSnackbar(getString(R.string.incorrect_pin))
                }
            } else {
                showSnackbar(getString(R.string.enter_valid_pin))
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
                binding.btnTogglePassword.contentDescription = getString(R.string.show_password)
            } else {
                et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_off)
                binding.btnTogglePassword.contentDescription = getString(R.string.hide_password)
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
                    showSnackbar(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                showSnackbar(getString(R.string.biometric_failed))
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

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), "", duration)
        snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE
        val snackbarView = snackbar.view

        val params = snackbarView.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        params.bottomMargin = resources.getDimensionPixelSize(R.dimen.snackbar_bottom_offset)
        params.leftMargin = 0
        params.rightMargin = 0
        snackbarView.layoutParams = params

        snackbarView.background = ContextCompat.getDrawable(this, R.drawable.snackbar_background)

        val defaultText = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        defaultText.text = message
        defaultText.visibility = View.GONE

        val customText = layoutInflater.inflate(R.layout.snackbar_custom, snackbarView as ViewGroup, false) as TextView
        customText.text = message
        customText.typeface = ResourcesCompat.getFont(this, R.font.blkchcry)
        customText.setTextColor(ContextCompat.getColor(this, R.color.text_on_container))
        customText.textSize = 18f
        customText.textAlignment = View.TEXT_ALIGNMENT_CENTER
        snackbarView.addView(customText)

        snackbar.show()
    }
}
