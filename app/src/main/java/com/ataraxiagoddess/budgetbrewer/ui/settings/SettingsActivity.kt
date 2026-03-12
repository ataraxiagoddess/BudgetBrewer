package com.ataraxiagoddess.budgetbrewer.ui.settings

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.ataraxiagoddess.budgetbrewer.MainActivity
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SupabaseClient
import com.ataraxiagoddess.budgetbrewer.data.SyncManager
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.ActivitySettingsBinding
import com.ataraxiagoddess.budgetbrewer.ui.auth.AuthActivity
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.base.showBudgetBrewerDialog
import com.ataraxiagoddess.budgetbrewer.ui.calendar.MonthlyCalendarActivity
import com.ataraxiagoddess.budgetbrewer.ui.expenses.MonthlyExpenseListActivity
import com.ataraxiagoddess.budgetbrewer.ui.finances.IncomeExpensesActivity
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.spending.SpendingActivity
import com.ataraxiagoddess.budgetbrewer.util.AppLockManager
import com.ataraxiagoddess.budgetbrewer.util.CurrencyPrefs
import com.ataraxiagoddess.budgetbrewer.util.ExportHelper
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

@Serializable
private data class DeleteAccountResponse(
    val success: Boolean? = null,
    val error: String? = null,
    val code: Int? = null,
    val message: String? = null
)

class SettingsActivity : BaseActivity() {

    override val currentNavDestination: NavDestination
        get() = NavDestination.SETTINGS
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: BudgetRepository
    private val settingsPrefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var isAccountExpanded = false
    private var isAppearanceExpanded = false
    private var isCurrencyExpanded = false
    companion object {
        const val EXTRA_START_FRAGMENT = "start_fragment"
        const val FRAGMENT_SIGN_IN = "sign_in"
        const val FRAGMENT_SIGN_UP = "sign_up"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("account_expanded", isAccountExpanded)
        outState.putBoolean("appearance_expanded", isAppearanceExpanded)
        outState.putBoolean("currency_expanded", isCurrencyExpanded)
    }

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize repository
        val db = AppDatabase.getDatabase(this)
        repository = BudgetRepository(db)

        // Hide month spinner
        if (!isRailMode) {
            monthSelectorBinding.root.visibility = View.GONE
            navBinding.navButtonHome.visibility = View.VISIBLE
            navBinding.navButtonFinances.visibility = View.VISIBLE
            navBinding.navButtonExpenses.visibility = View.VISIBLE
            navBinding.navButtonSpending.visibility = View.VISIBLE
            navBinding.navButtonCalendar.visibility = View.VISIBLE
        }

        if (savedInstanceState != null) {
            isAccountExpanded = savedInstanceState.getBoolean("account_expanded", false)
            isAppearanceExpanded = savedInstanceState.getBoolean("appearance_expanded", false)
            isCurrencyExpanded = savedInstanceState.getBoolean("currency_expanded", false)
        }

        setupSectionToggles()
        setupAppearance()
        setupCurrency()
        setupKoFi()
        setupVersion()
        updateAccountSection()

        lifecycleScope.launch {
            SupabaseClient.client.auth.sessionStatus
                .debounce(200)
                .collect { _ ->
                    updateAccountSection()
                }
        }

    }

    override fun onResume() {
        super.onResume()
        updateAccountSection()
    }

    private fun setupSectionToggles() {
        // Apply saved expanded states
        binding.accountContent.visibility = if (isAccountExpanded) View.VISIBLE else View.GONE
        binding.themeButtonContainer.visibility = if (isAppearanceExpanded) View.VISIBLE else View.GONE
        binding.spinnerCurrency.visibility = if (isCurrencyExpanded) View.VISIBLE else View.GONE

        binding.btnAccount.setIconResource(if (isAccountExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right)
        binding.btnAppearance.setIconResource(if (isAppearanceExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right)
        binding.btnCurrency.setIconResource(if (isCurrencyExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right)

        // Set click listeners
        binding.btnAccount.setOnClickListener {
            isAccountExpanded = !isAccountExpanded
            binding.accountContent.visibility = if (isAccountExpanded) View.VISIBLE else View.GONE
            binding.btnAccount.setIconResource(if (isAccountExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right)
        }
        binding.btnAppearance.setOnClickListener {
            isAppearanceExpanded = !isAppearanceExpanded
            binding.themeButtonContainer.visibility = if (isAppearanceExpanded) View.VISIBLE else View.GONE
            binding.btnAppearance.setIconResource(if (isAppearanceExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right)
        }
        binding.btnCurrency.setOnClickListener {
            isCurrencyExpanded = !isCurrencyExpanded
            binding.spinnerCurrency.visibility = if (isCurrencyExpanded) View.VISIBLE else View.GONE
            binding.btnCurrency.setIconResource(if (isCurrencyExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right)
        }
    }

    private fun setupAppearance() {
        val currentMode = settingsPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // Set initial checked state
        updateThemeButtons(currentMode)

        // Set click listeners
        binding.btnThemeLight.setOnClickListener {
            saveAndApplyTheme(AppCompatDelegate.MODE_NIGHT_NO)
        }
        binding.btnThemeDark.setOnClickListener {
            saveAndApplyTheme(AppCompatDelegate.MODE_NIGHT_YES)
        }
        binding.btnThemeSystem.setOnClickListener {
            saveAndApplyTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun saveAndApplyTheme(mode: Int) {
        // Avoid reapplying if already in that mode
        if (mode == AppCompatDelegate.getDefaultNightMode()) return

        settingsPrefs.edit { putInt("theme_mode", mode) }
        AppCompatDelegate.setDefaultNightMode(mode)
        // Update button states immediately
        updateThemeButtons(mode)
    }

    private fun updateThemeButtons(mode: Int) {
        binding.btnThemeLight.isChecked = mode == AppCompatDelegate.MODE_NIGHT_NO
        binding.btnThemeDark.isChecked = mode == AppCompatDelegate.MODE_NIGHT_YES
        binding.btnThemeSystem.isChecked = mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    private fun setupCurrency() {
        val currencies = resources.getStringArray(R.array.currencies)

        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            currencies
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.typeface = ResourcesCompat.getFont(this@SettingsActivity, R.font.exo_regular)
                view.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_on_dialog))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.spinner_dropdown_item, parent, false)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.text = getItem(position)
                textView.typeface = ResourcesCompat.getFont(this@SettingsActivity, R.font.exo_regular)
                textView.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_on_dialog))
                return view
            }
        }
        binding.spinnerCurrency.adapter = adapter

        val defaultCurrency = currencies[0]  // first item, e.g., "$ (USD)"
        val savedCurrency = settingsPrefs.getString("currency", defaultCurrency)
        val position = currencies.indexOf(savedCurrency).takeIf { it >= 0 } ?: 0
        binding.spinnerCurrency.setSelection(position)

        binding.spinnerCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = currencies[position]
                settingsPrefs.edit { putString("currency", selected) }
                CurrencyPrefs.updateSymbol(selected)

                lifecycleScope.launch {
                    repository.updateAllIncomesCurrency(CurrencyPrefs.currentSymbol)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupKoFi() {
        binding.btnKoFi.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://ko-fi.com/ataraxiagoddess".toUri())
            startActivity(intent)
        }
    }

    private fun setupVersion() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        binding.tvVersion.text = getString(R.string.version_format, version)
    }

    private fun updateAccountSection() {
        val currentUser = SupabaseClient.client.auth.currentUserOrNull()
        if (currentUser != null) {
            // Signed in
            binding.tvAccountStatus.visibility = View.GONE
            binding.btnSignIn.visibility = View.GONE
            binding.btnSignUp.visibility = View.GONE
            binding.tvUserEmail.visibility = View.VISIBLE
            binding.btnSignOut.visibility = View.VISIBLE
            binding.btnExportCSV.visibility = View.VISIBLE
            binding.btnExportPDF.visibility = View.VISIBLE
            binding.switchPinLock.visibility = View.VISIBLE
            binding.switchBiometrics.visibility = View.VISIBLE
            binding.btnChangePin.visibility = View.VISIBLE
            binding.btnChangePin.visibility = if (AppLockManager.isPinEnabled()) View.VISIBLE else View.GONE

            binding.switchPinLock.isChecked = AppLockManager.isPinEnabled()
            binding.switchBiometrics.isChecked = AppLockManager.isBiometricsEnabled()
            binding.switchBiometrics.isEnabled = AppLockManager.isPinEnabled() && isBiometricAvailable()

            binding.switchPinLock.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (!AppLockManager.hasPin()) {
                        showSetPinDialog { pin ->
                            AppLockManager.setPin(pin)
                            AppLockManager.setPinEnabled(true)
                            AppLockManager.unlock()                     // <-- keep unlocked
                            binding.switchBiometrics.isEnabled = isBiometricAvailable()
                            binding.btnChangePin.visibility = View.VISIBLE
                            binding.btnDeletePin.visibility = View.VISIBLE   // <-- show delete
                            showSnackbar(getString(R.string.pin_set))        // optional feedback
                        }
                    } else {
                        AppLockManager.setPinEnabled(true)
                        binding.switchBiometrics.isEnabled = isBiometricAvailable()
                        binding.btnChangePin.visibility = View.VISIBLE
                        binding.btnDeletePin.visibility = View.VISIBLE   // <-- show delete
                    }
                } else {
                    AppLockManager.setPinEnabled(false)
                    AppLockManager.setBiometricsEnabled(false)
                    binding.switchBiometrics.isChecked = false
                    binding.switchBiometrics.isEnabled = false
                    binding.btnChangePin.visibility = View.GONE
                    binding.btnDeletePin.visibility = View.GONE
                }
            }

            binding.switchBiometrics.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (isBiometricAvailable()) {
                        AppLockManager.setBiometricsEnabled(true)
                    } else {
                        binding.switchBiometrics.isChecked = false
                        showSnackbar(getString(R.string.biometric_not_available))
                    }
                } else {
                    AppLockManager.setBiometricsEnabled(false)
                }
            }

            binding.btnChangePin.setOnClickListener {
                showSetPinDialog { newPin ->
                    AppLockManager.setPin(newPin)
                    AppLockManager.unlock()                     // <-- keep unlocked
                    binding.btnDeletePin.visibility = View.VISIBLE
                    showSnackbar(getString(R.string.pin_set))
                }
            }

            val hasPin = AppLockManager.hasPin()
            binding.btnChangePin.visibility = if (hasPin) View.VISIBLE else View.GONE
            binding.btnDeletePin.visibility = if (hasPin) View.VISIBLE else View.GONE

            binding.btnDeletePin.setOnClickListener {
                showBudgetBrewerDialog(
                    inflater = layoutInflater,
                    context = this,
                    title = getString(R.string.delete_pin_title),
                    message = getString(R.string.delete_pin_message),
                    positiveButton = getString(R.string.delete),
                    negativeButton = getString(R.string.cancel),
                    onPositive = {
                        AppLockManager.clearPin()
                        AppLockManager.setPinEnabled(false)
                        binding.switchPinLock.isChecked = false
                        updateAccountSection()
                        showSnackbar(getString(R.string.pin_deleted))
                    }
                ).show()
            }

            binding.btnDeleteAccount.visibility = View.VISIBLE
            binding.btnDeleteAccount.setOnClickListener {
                showDeleteAccountDialog()
            }

            binding.tvUserEmail.text = currentUser.email ?: getString(R.string.no_email)
        } else {
            // Not signed in
            binding.tvAccountStatus.visibility = View.VISIBLE
            binding.btnSignIn.visibility = View.VISIBLE
            binding.btnSignUp.visibility = View.VISIBLE
            binding.tvUserEmail.visibility = View.GONE
            binding.btnSignOut.visibility = View.GONE
            binding.btnExportCSV.visibility = View.GONE
            binding.btnExportPDF.visibility = View.GONE
            binding.switchPinLock.visibility = View.GONE
            binding.switchBiometrics.visibility = View.GONE
            binding.btnChangePin.visibility = View.GONE
            binding.btnDeletePin.visibility = View.GONE
        }

        binding.btnSignIn.setOnClickListener {
            val intent = Intent(this, AuthActivity::class.java).apply {
                putExtra(EXTRA_START_FRAGMENT, FRAGMENT_SIGN_IN)
            }
            startActivity(intent)
        }

        binding.btnExportCSV.setOnClickListener {
            lifecycleScope.launch {
                showSnackbar(getString(R.string.preparing_csv_export))
                val uri = ExportHelper.exportToCSV(this@SettingsActivity)
                if (uri != null) {
                    showExportSuccessSnackbar(uri, true)
                } else {
                    showSnackbar(getString(R.string.export_failed))
                }
            }
        }

        binding.btnExportPDF.setOnClickListener {
            lifecycleScope.launch {
                showSnackbar(getString(R.string.preparing_pdf_export))
                val uri = ExportHelper.exportToPDF(this@SettingsActivity)
                if (uri != null) {
                    showExportSuccessSnackbar(uri, false)
                } else {
                    showSnackbar(getString(R.string.export_failed))
                }
            }
        }

        binding.btnSignUp.setOnClickListener {
            val intent = Intent(this, AuthActivity::class.java).apply {
                putExtra(EXTRA_START_FRAGMENT, FRAGMENT_SIGN_UP)
            }
            startActivity(intent)
        }
        binding.btnSignOut.setOnClickListener {
            lifecycleScope.launch {
                // Clear all pending sync items (no user context needed)
                val db = AppDatabase.getDatabase(this@SettingsActivity)
                db.pendingSyncDao().deleteAll()

                // Sign out from Supabase
                SupabaseClient.client.auth.signOut()

                // Clear local data
                SyncManager(this@SettingsActivity).clearLocalData()

                // Clear user ID from prefs
                AuthManager.clear(this@SettingsActivity)

                AppLockManager.lock()

                // Update UI
                updateAccountSection()
                showSnackbar("Signed out")
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showDeleteAccountDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_password_confirmation, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val btnToggle = dialogView.findViewById<ImageButton>(R.id.btnTogglePassword)

        // Toggle password visibility
        btnToggle.setOnClickListener {
            val currentInputType = etPassword.inputType
            if (currentInputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                btnToggle.setImageResource(R.drawable.ic_visibility_off)
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                btnToggle.setImageResource(R.drawable.ic_visibility_on)
            }
            // Reapply custom font
            etPassword.typeface = ResourcesCompat.getFont(this, R.font.exo_regular)
            etPassword.text?.let { etPassword.setSelection(it.length) }
        }

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.delete_account),
            view = dialogView,
            positiveButton = getString(R.string.delete),
            negativeButton = getString(R.string.cancel)
        )

        dialog.setOnShowListener {
            val deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            deleteButton.isEnabled = false

            etPassword.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    deleteButton.isEnabled = !s.isNullOrBlank()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            deleteButton.setOnClickListener {
                val password = etPassword.text.toString()
                dialog.dismiss()
                deleteAccount(password)
            }
        }

        dialog.show()
    }

    private fun deleteAccount(password: String) {
        lifecycleScope.launch {
            val currentUser = SupabaseClient.client.auth.currentUserOrNull()
            val email = currentUser?.email ?: run {
                showSnackbar("No signed‑in user found")
                return@launch
            }

            try {
                // 1. Re‑authenticate
                SupabaseClient.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User ID not found after re‑auth")

                // 2. Delete all user data from Supabase tables (RLS will restrict)
                val db = AppDatabase.getDatabase(this@SettingsActivity)
                db.pendingSyncDao().deleteForUser(userId)

                SupabaseClient.client.postgrest["daily_income_assignments"].delete { filter { eq("user_id", userId) } }
                SupabaseClient.client.postgrest["spending_entries"].delete { filter { eq("user_id", userId) } }
                SupabaseClient.client.postgrest["daily_checklist"].delete { filter { eq("user_id", userId) } }
                SupabaseClient.client.postgrest["expenses"].delete { filter { eq("user_id", userId) } }
                SupabaseClient.client.postgrest["expense_categories"].delete { filter { eq("user_id", userId) } }
                SupabaseClient.client.postgrest["incomes"].delete { filter { eq("user_id", userId) } }
                SupabaseClient.client.postgrest["allocations"].delete { filter { eq("user_id", userId) } }
                SupabaseClient.client.postgrest["month_settings"].delete { filter { eq("user_id", userId) } }
                SupabaseClient.client.postgrest["budgets"].delete { filter { eq("user_id", userId) } }

                // 3. Delete the user's auth account via Edge Function
                withContext(Dispatchers.IO) {
                    val session = SupabaseClient.client.auth.currentSessionOrNull()
                    val accessToken = session?.accessToken
                        ?: throw Exception("No access token available")

                    // Stop auto‑refresh so the SDK doesn't try to use the soon‑to‑be‑deleted session
                    SupabaseClient.client.auth.stopAutoRefreshForCurrentSession()

                    val functionUrl = "${SupabaseClient.SUPABASE_URL}/functions/v1/delete-user"
                    val response: HttpResponse = SupabaseClient.client.httpClient.post(functionUrl) {
                        contentType(ContentType.Application.Json)
                        headers {
                            append("Authorization", "Bearer $accessToken")
                        }
                    }

                    val responseBody = response.bodyAsText()
                    Timber.d("Edge function response: ${response.status} - $responseBody")

                    val json = Json { ignoreUnknownKeys = true }
                    val result = try {
                        json.decodeFromString<DeleteAccountResponse>(responseBody)
                    } catch (e: Exception) {
                        throw Exception("Invalid response: $responseBody")
                    }

                    if (response.status != HttpStatusCode.OK || result.error != null) {
                        throw Exception(result.error ?: "Unknown error (HTTP ${response.status})")
                    }
                }

                // 4. Clear the local session – this tells the SDK the user is gone
                SupabaseClient.client.auth.clearSession()

                // 5. Clear local Room data and auth prefs
                SyncManager(this@SettingsActivity).clearLocalData()
                AuthManager.clear(this@SettingsActivity)

                // 6. Navigate to AuthActivity with a fresh task
                val intent = Intent(this@SettingsActivity, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()

                showSnackbar(getString(R.string.delete_account_success))
            } catch (e: Exception) {
                Timber.e(e, "Account deletion failed")
                val message = if (e.message?.contains("Invalid login credentials") == true) {
                    getString(R.string.delete_account_wrong_password)
                } else {
                    getString(R.string.delete_account_failed, e.message)
                }
                showSnackbar(message)
            }
        }
    }

    // Helper to check biometric availability
    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Dialog to set/change PIN
    @SuppressLint("InflateParams")
    private fun showSetPinDialog(onPinSet: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_pin, null)
        val etPin = dialogView.findViewById<EditText>(R.id.etPin)
        val etConfirmPin = dialogView.findViewById<EditText>(R.id.etConfirmPin)
        val btnTogglePin = dialogView.findViewById<ImageButton>(R.id.btnTogglePin)
        val btnToggleConfirmPin = dialogView.findViewById<ImageButton>(R.id.btnToggleConfirmPin)
        val tvError = dialogView.findViewById<TextView>(R.id.tvError)

        // Toggle for PIN field
        btnTogglePin.setOnClickListener {
            val current = etPin.inputType
            if (current == (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)) {
                etPin.inputType = InputType.TYPE_CLASS_NUMBER
                btnTogglePin.setImageResource(R.drawable.ic_visibility_on)
            } else {
                etPin.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                btnTogglePin.setImageResource(R.drawable.ic_visibility_off)
            }
            etPin.typeface = ResourcesCompat.getFont(this@SettingsActivity, R.font.exo_regular)
            etPin.text?.let { etPin.setSelection(it.length) }
        }

        // Toggle for confirm PIN field
        btnToggleConfirmPin.setOnClickListener {
            val current = etConfirmPin.inputType
            if (current == (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)) {
                etConfirmPin.inputType = InputType.TYPE_CLASS_NUMBER
                btnToggleConfirmPin.setImageResource(R.drawable.ic_visibility_on)
            } else {
                etConfirmPin.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                btnToggleConfirmPin.setImageResource(R.drawable.ic_visibility_off)
            }
            etConfirmPin.typeface = ResourcesCompat.getFont(this@SettingsActivity, R.font.exo_regular)
            etConfirmPin.text?.let { etConfirmPin.setSelection(it.length) }
        }

        val dialog = showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.set_pin),
            view = dialogView,
            positiveButton = getString(R.string.save),
            negativeButton = getString(R.string.cancel)
        )

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.isEnabled = false

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val pin = etPin.text.toString()
                    val confirm = etConfirmPin.text.toString()
                    saveButton.isEnabled = pin.length == 4 && pin.all { it.isDigit() } &&
                            confirm.length == 4 && confirm.all { it.isDigit() } &&
                            pin == confirm
                    tvError.visibility = View.GONE
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etPin.addTextChangedListener(textWatcher)
            etConfirmPin.addTextChangedListener(textWatcher)

            saveButton.setOnClickListener {
                val pin = etPin.text.toString()
                val confirm = etConfirmPin.text.toString()
                if (pin == confirm && pin.length == 4 && pin.all { it.isDigit() }) {
                    onPinSet(pin)
                    dialog.dismiss()
                } else {
                    tvError.text = getString(R.string.pins_do_not_match)
                    tvError.visibility = View.VISIBLE
                }
            }
        }
        dialog.show()
    }

    override fun navigateToHome() {
        startActivity(Intent(this, MainActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToFinances() {
        startActivity(Intent(this, IncomeExpensesActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToExpenses() {
        startActivity(Intent(this, MonthlyExpenseListActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToSpending() {
        startActivity(Intent(this, SpendingActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToCalendar() {
        startActivity(Intent(this, MonthlyCalendarActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    private fun showExportSuccessSnackbar(uri: Uri, isCsv: Boolean) {
        // Custom span to apply typeface (works on all API levels)
        class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
            override fun updateMeasureState(textPaint: TextPaint) {
                textPaint.typeface = typeface
            }
            override fun updateDrawState(textPaint: TextPaint) {
                textPaint.typeface = typeface
            }
        }

        // Create styled "Share" text
        val shareText = SpannableString(getString(R.string.share)).apply {
            val exoBold = ResourcesCompat.getFont(this@SettingsActivity, R.font.exo_semi_bold)
                ?: ResourcesCompat.getFont(this@SettingsActivity, R.font.exo_bold)
            if (exoBold != null) {
                setSpan(CustomTypefaceSpan(exoBold), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            setSpan(ForegroundColorSpan(ContextCompat.getColor(this@SettingsActivity, R.color.text_on_main)),
                0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val snackbar = Snackbar.make(binding.root, R.string.export_saved, Snackbar.LENGTH_LONG)
        snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE
        val snackbarView = snackbar.view

        // Apply custom background
        snackbarView.background = ContextCompat.getDrawable(this, R.drawable.snackbar_background)

        // Style the default text
        val defaultText = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        defaultText.typeface = ResourcesCompat.getFont(this, R.font.blkchcry)
        defaultText.setTextColor(ContextCompat.getColor(this, R.color.text_on_container))
        defaultText.textSize = 18f
        defaultText.textAlignment = View.TEXT_ALIGNMENT_CENTER

        // Set action with styled text
        snackbar.setAction(shareText) {
            ExportHelper.shareFile(this, uri, getString(if (isCsv) R.string.share_csv else R.string.share_pdf))
        }

        // Position above bottom navigation
        val params = snackbarView.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        params.bottomMargin = resources.getDimensionPixelSize(R.dimen.snackbar_bottom_offset)
        params.leftMargin = 0
        params.rightMargin = 0
        snackbarView.layoutParams = params

        snackbar.show()
    }
}