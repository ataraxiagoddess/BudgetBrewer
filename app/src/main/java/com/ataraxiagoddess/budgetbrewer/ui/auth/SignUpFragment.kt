package com.ataraxiagoddess.budgetbrewer.ui.auth

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SupabaseClient
import com.ataraxiagoddess.budgetbrewer.databinding.FragmentSignUpBinding
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SignUpFragment : Fragment() {

    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    private var navigationListener: AuthNavigationListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        navigationListener = context as? AuthNavigationListener
    }

    override fun onDetach() {
        super.onDetach()
        navigationListener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPasswordToggles()
        setupValidation()
        setupListeners()
    }

    private fun setupPasswordToggles() {
        binding.btnTogglePassword.setOnClickListener {
            val currentInputType = binding.etPassword.inputType
            if (currentInputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_off)
            } else {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_on)
            }
            binding.etPassword.text?.let { binding.etPassword.setSelection(it.length) }
        }

        binding.btnToggleConfirmPassword.setOnClickListener {
            val currentInputType = binding.etConfirmPassword.inputType
            if (currentInputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) {
                binding.etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnToggleConfirmPassword.setImageResource(R.drawable.ic_visibility_off)
            } else {
                binding.etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnToggleConfirmPassword.setImageResource(R.drawable.ic_visibility_on)
            }
            binding.etConfirmPassword.text?.let { binding.etConfirmPassword.setSelection(it.length) }
        }
    }

    private fun setupValidation() {
        binding.etPassword.addTextChangedListener {
            val strength = getPasswordStrength(it.toString())
            binding.tvPasswordStrength.text = strength
            binding.tvPasswordStrength.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        binding.btnSignUp.setOnClickListener {
            if (validateInputs()) {
                signUp()
            }
        }

        binding.tvSignInPrompt.setOnClickListener {
            navigationListener?.navigateToSignIn()
        }

        binding.btnBack.setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun validateInputs(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val confirmEmail = binding.etConfirmEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (!isValidEmail(email)) {
            (activity as? AuthActivity)?.showSnackbar(getString(R.string.enter_valid_email))
            return false
        }
        if (email != confirmEmail) {
            (activity as? AuthActivity)?.showSnackbar(getString(R.string.emails_do_not_match))
            return false
        }
        if (password.length < 8) {
            (activity as? AuthActivity)?.showSnackbar(getString(R.string.password_too_short))
            return false
        }
        if (!password.matches(Regex(".*[A-Z].*"))) {
            (activity as? AuthActivity)?.showSnackbar(getString(R.string.password_need_uppercase))
            return false
        }
        if (!password.matches(Regex(".*[a-z].*"))) {
            (activity as? AuthActivity)?.showSnackbar(getString(R.string.password_need_lowercase))
            return false
        }
        if (!password.matches(Regex(".*[0-9].*"))) {
            (activity as? AuthActivity)?.showSnackbar(getString(R.string.password_need_number))
            return false
        }
        if (!password.matches(Regex(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*"))) {
            (activity as? AuthActivity)?.showSnackbar(getString(R.string.password_need_special))
            return false
        }
        if (password != confirmPassword) {
            (activity as? AuthActivity)?.showSnackbar(getString(R.string.passwords_do_not_match))
            return false
        }
        return true
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return Pattern.compile(emailPattern).matcher(email).matches()
    }

    private fun getPasswordStrength(password: String): String {
        return when {
            password.length < 8 -> getString(R.string.password_strength_weak)
            !password.matches(Regex(".*[A-Z].*")) -> getString(R.string.password_strength_weak)
            !password.matches(Regex(".*[a-z].*")) -> getString(R.string.password_strength_weak)
            !password.matches(Regex(".*[0-9].*")) -> getString(R.string.password_strength_weak)
            !password.matches(Regex(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) -> getString(R.string.password_strength_weak)
            password.length >= 10 -> getString(R.string.password_strength_strong)
            else -> getString(R.string.password_strength_medium)
        }
    }

    private fun signUp() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSignUp.isEnabled = false

        lifecycleScope.launch {
            try {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()

                SupabaseClient.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                (activity as? AuthActivity)?.navigateToVerifyEmail(email)
            } catch (e: Exception) {
                val message = when {
                    e.message?.contains("User already registered", ignoreCase = true) == true ->
                        getString(R.string.error_user_exists)
                    else -> getString(R.string.sign_up_failed, e.message)
                }
                (activity as? AuthActivity)?.showSnackbar(message)
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSignUp.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}