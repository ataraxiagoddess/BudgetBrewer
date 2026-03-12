package com.ataraxiagoddess.budgetbrewer.ui.auth

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.commit
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.databinding.ActivityAuthBinding
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity.Companion.EXTRA_START_FRAGMENT
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity.Companion.FRAGMENT_SIGN_UP
import com.google.android.material.snackbar.Snackbar

class AuthActivity : AppCompatActivity(), AuthNavigationListener {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            val startFragment = intent.getStringExtra(EXTRA_START_FRAGMENT)
            when (startFragment) {
                FRAGMENT_SIGN_UP -> navigateToSignUp()
                else -> navigateToSignIn() // default to sign in
            }
        }
    }

    override fun navigateToSignIn() {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, SignInFragment())
            // Clear back stack to avoid piling up fragments
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    override fun navigateToSignUp() {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, SignUpFragment())
            addToBackStack(null)
        }
    }

    // New method to show the verification screen after sign-up
    fun navigateToVerifyEmail(email: String) {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, VerifyEmailFragment.newInstance(email))
            addToBackStack(null)
        }
    }

    fun navigateToResetPassword(email: String) {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, ResetPasswordFragment.newInstance(email))
            addToBackStack(null)
        }
    }

    fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
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

        // Hide default text
        val defaultText = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        defaultText.visibility = View.GONE

        // Add custom text
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