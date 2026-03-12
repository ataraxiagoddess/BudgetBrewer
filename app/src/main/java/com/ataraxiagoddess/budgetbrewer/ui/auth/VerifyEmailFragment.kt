package com.ataraxiagoddess.budgetbrewer.ui.auth

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.SupabaseClient
import com.ataraxiagoddess.budgetbrewer.databinding.FragmentVerifyEmailBinding
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class VerifyEmailFragment : Fragment() {

    private var _binding: FragmentVerifyEmailBinding? = null
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
        _binding = FragmentVerifyEmailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvEmailSent.text = getString(R.string.verification_code_sent, email ?: "")

        // Set font for hint and sign‑in link
        val exoRegular = ResourcesCompat.getFont(requireContext(), R.font.exo_regular)
        binding.tvHint.typeface = exoRegular
        binding.tvSignInInstead.typeface = exoRegular

        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            if (code.length == 6 && code.all { it.isDigit() }) {
                verifyCode(code)
            } else {
                (activity as? AuthActivity)?.showSnackbar(getString(R.string.enter_valid_code))
            }
        }

        binding.btnResend.setOnClickListener {
            resendCode()
        }

        binding.btnBack.setOnClickListener {
            navigationListener?.navigateToSignIn()
        }

        binding.tvSignInInstead.setOnClickListener {
            navigationListener?.navigateToSignIn()
        }
    }

    private fun verifyCode(code: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnVerify.isEnabled = false

        lifecycleScope.launch {
            try {
                val email = email ?: throw Exception("Email missing")
                SupabaseClient.client.auth.verifyEmailOtp(
                    type = OtpType.Email.SIGNUP,
                    email = email,
                    token = code
                )

                (activity as? AuthActivity)?.showSnackbar(getString(R.string.email_verified))
                navigationListener?.navigateToSignIn()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnVerify.isEnabled = true
                val message = when {
                    e.message?.contains("Invalid", ignoreCase = true) == true ->
                        getString(R.string.error_invalid_code)
                    else -> getString(R.string.verification_failed, e.message)
                }
                (activity as? AuthActivity)?.showSnackbar(message)
            }
        }
    }

    private fun resendCode() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnResend.isEnabled = false

        lifecycleScope.launch {
            try {
                val email = email ?: return@launch
                SupabaseClient.client.auth.resendEmail(OtpType.Email.SIGNUP, email)
                (activity as? AuthActivity)?.showSnackbar(getString(R.string.code_resent))
            } catch (e: Exception) {
                (activity as? AuthActivity)?.showSnackbar(getString(R.string.resend_failed, e.message))
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnResend.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(email: String): VerifyEmailFragment {
            val fragment = VerifyEmailFragment()
            val args = Bundle()
            args.putString("email", email)
            fragment.arguments = args
            return fragment
        }
    }
}