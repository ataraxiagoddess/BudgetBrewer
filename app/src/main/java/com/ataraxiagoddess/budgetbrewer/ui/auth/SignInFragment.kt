package com.ataraxiagoddess.budgetbrewer.ui.auth

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.SupabaseClient
import com.ataraxiagoddess.budgetbrewer.data.SyncManager
import com.ataraxiagoddess.budgetbrewer.databinding.FragmentSignInBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.showBudgetBrewerDialog
import com.ataraxiagoddess.budgetbrewer.util.AppLockManager
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch
import timber.log.Timber

class SignInFragment : Fragment() {

    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!

    private var navigationListener: AuthNavigationListener? = null

    private fun setupPasswordToggle() {
        binding.btnTogglePassword.setOnClickListener {
            val currentInputType = binding.etPassword.inputType
            if (currentInputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) {
                // Switch to hidden
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_off)
            } else {
                // Switch to visible
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_on)
            }
            // Move cursor to end
            binding.etPassword.text?.let { binding.etPassword.setSelection(it.length) }
        }
    }

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
        _binding = FragmentSignInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPasswordToggle()

        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                signIn(email, password)
            } else {
                showError(getString(R.string.fill_all_fields))
            }
        }

        binding.tvSignUpPrompt.setOnClickListener {
            navigationListener?.navigateToSignUp()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.btnBack.setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_single_edittext, null)
        val editText = dialogView.findViewById<EditText>(R.id.etInput)
        editText.hint = getString(R.string.email)

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = requireContext(),
            title = getString(R.string.forgot_password),
            view = dialogView,
            positiveButton = getString(R.string.send),
            negativeButton = getString(R.string.cancel),
            onPositive = {
                val email = editText.text.toString().trim()
                if (email.isNotEmpty()) {
                    // Navigate to reset password fragment with this email
                    (activity as? AuthActivity)?.navigateToResetPassword(email)
                } else {
                    showError(getString(R.string.enter_email))
                }
            }
        )
        dialog.show()
    }

    private fun signIn(email: String, password: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSignIn.isEnabled = false

        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    AuthManager.saveUserId(requireContext(), userId)

                    AppLockManager.unlock()

                    val syncManager = SyncManager(requireContext())
                    val hasData = syncManager.userHasData(userId)
                    Timber.d("userHasData returned $hasData")

                    if (hasData) {
                        syncManager.downloadAllData(userId)
                        (activity as? AuthActivity)?.showSnackbar(getString(R.string.data_restored))
                    } else {
                        syncManager.uploadAllData(userId)
                        (activity as? AuthActivity)?.showSnackbar(getString(R.string.data_backed_up))
                    }
                }

                (activity as? AuthActivity)?.showSnackbar(getString(R.string.signed_in_success))
                requireActivity().finish()
            } catch (e: Exception) {
                val message = when {
                    e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                        getString(R.string.error_invalid_credentials)
                    e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                        getString(R.string.error_email_not_confirmed)
                    else -> getString(R.string.sign_in_failed, e.message)
                }
                (activity as? AuthActivity)?.showSnackbar(message)
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSignIn.isEnabled = true
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}