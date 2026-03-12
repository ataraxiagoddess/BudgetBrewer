package com.ataraxiagoddess.budgetbrewer.ui.auth

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SupabaseClient
import com.ataraxiagoddess.budgetbrewer.databinding.FragmentResetPasswordBinding
import com.ataraxiagoddess.budgetbrewer.util.AppLockManager
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private var navigationListener: AuthNavigationListener? = null
    private var email: String? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        navigationListener = context as? AuthNavigationListener
    }

    override fun onDetach() {
        super.onDetach()
        navigationListener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        email = arguments?.getString("email")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvEmailSent.text = getString(R.string.reset_code_sent, email ?: "")

        // Request the reset code as soon as the fragment loads
        requestResetCode()

        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            val newPassword = binding.etNewPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (code.length != 6 || !code.all { it.isDigit() }) {
                (activity as? AuthActivity)?.showSnackbar(getString(R.string.enter_valid_code))
                return@setOnClickListener
            }
            if (newPassword.length < 8) {
                (activity as? AuthActivity)?.showSnackbar(getString(R.string.password_too_short))
                return@setOnClickListener
            }
            if (newPassword != confirmPassword) {
                (activity as? AuthActivity)?.showSnackbar(getString(R.string.passwords_do_not_match))
                return@setOnClickListener
            }

            verifyCodeAndResetPassword(code, newPassword)
        }

        binding.btnResend.setOnClickListener {
            requestResetCode()
        }

        binding.btnBack.setOnClickListener {
            navigationListener?.navigateToSignIn()
        }

        setupPasswordToggle()
    }

    private fun setupPasswordToggle() {
        binding.btnTogglePassword.setOnClickListener {
            val currentInputType = binding.etNewPassword.inputType
            if (currentInputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) {
                binding.etNewPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_off)
            } else {
                binding.etNewPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_on)
            }
            binding.etNewPassword.text?.let { binding.etNewPassword.setSelection(it.length) }
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

    private fun requestResetCode() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnVerify.isEnabled = false
        binding.btnResend.isEnabled = false

        lifecycleScope.launch {
            try {
                val email = email ?: throw Exception("Email missing")
                SupabaseClient.client.auth.resetPasswordForEmail(email)
                binding.progressBar.visibility = View.GONE
                binding.btnVerify.isEnabled = true
                binding.btnResend.isEnabled = true
                (activity as? AuthActivity)?.showSnackbar(getString(R.string.reset_code_sent_toast))
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnVerify.isEnabled = true
                binding.btnResend.isEnabled = true
                val message = when {
                    e.message?.contains("Email not found", ignoreCase = true) == true ->
                        getString(R.string.error_email_not_found)
                    else -> getString(R.string.reset_request_failed, e.message)
                }
                (activity as? AuthActivity)?.showSnackbar(message)
            }
        }
    }

    private fun verifyCodeAndResetPassword(code: String, newPassword: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnVerify.isEnabled = false

        lifecycleScope.launch {
            try {
                val email = email ?: throw Exception("Email missing")

                // Verify the recovery OTP
                SupabaseClient.client.auth.verifyEmailOtp(
                    type = OtpType.Email.RECOVERY,
                    email = email,
                    token = code
                )

                // After verification, update the password
                SupabaseClient.client.auth.updateUser { password = newPassword }
                AppLockManager.unlock()

                // Success
                (activity as? AuthActivity)?.showSnackbar(getString(R.string.password_reset_success))
                navigationListener?.navigateToSignIn()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnVerify.isEnabled = true

                val message = when {
                    e.message?.contains("Cannot reuse previous password", ignoreCase = true) == true ->
                        getString(R.string.password_same_as_old)
                    e.message?.contains("weak", ignoreCase = true) == true ->
                        getString(R.string.password_too_weak)
                    e.message?.contains("Invalid", ignoreCase = true) == true ->
                        getString(R.string.error_invalid_code)
                    else -> getString(R.string.reset_failed, e.message)
                }
                (activity as? AuthActivity)?.showSnackbar(message)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(email: String): ResetPasswordFragment {
            val fragment = ResetPasswordFragment()
            val args = Bundle()
            args.putString("email", email)
            fragment.arguments = args
            return fragment
        }
    }
}