package com.ataraxiagoddess.budgetbrewer.ui.base

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.databinding.LayoutBottomNavBinding
import com.ataraxiagoddess.budgetbrewer.databinding.MonthSelectorBinding
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavigationManager
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.snackbar.Snackbar
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import timber.log.Timber
import java.util.Calendar

abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var navBinding: LayoutBottomNavBinding
    protected lateinit var monthSelectorBinding: MonthSelectorBinding
    protected lateinit var navigationManager: NavigationManager
    protected val prefs: SharedPreferences by lazy {
        getSharedPreferences("budget_prefs", MODE_PRIVATE)!!
    }

    abstract val currentNavDestination: NavDestination

    // Month selection with programmatic guard
    private var isSettingMonthProgrammatically = false
    private var isSettingUpSpinner = false
    private var outerRoot: FrameLayout? = null

    protected val isRailMode: Boolean
        get() = useRail

    protected var selectedMonth: Month = Month.current()
        set(value) {
            if (field != value) {
                field = value
                if (!isSettingMonthProgrammatically) {
                    onMonthSelected(value)
                }
            }
        }

    private lateinit var monthAdapter: ArrayAdapter<String>
    private val allMonths = mutableListOf<Month>()

    // Navigation rail support
    private val useRail: Boolean
        get() {
            val isTablet = resources.getBoolean(R.bool.is_tablet)
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            return !isTablet && isLandscape // phones in landscape
        }

    private val railButtons = mutableMapOf<NavDestination, MaterialButton>()

    // ----------------------------------------------------------------------
    // Snackbar helper (fixed width on tablets, no setMaxWidth)
    // ----------------------------------------------------------------------
    protected fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val snackbar = Snackbar.make(outerRoot!!, "", duration)
        snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE
        val snackbarView = snackbar.view

        val params = snackbarView.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        if (resources.getBoolean(R.bool.is_tablet)) {
            params.width = resources.getDimensionPixelSize(R.dimen.snackbar_max_width_tablet)
        } else {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        params.bottomMargin = resources.getDimensionPixelSize(R.dimen.snackbar_bottom_offset)
        params.leftMargin = 0
        params.rightMargin = 0
        snackbarView.layoutParams = params

        snackbarView.background = ContextCompat.getDrawable(this, R.drawable.snackbar_background)

        val defaultText = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        defaultText.visibility = View.GONE

        val customText = layoutInflater.inflate(R.layout.snackbar_custom, snackbarView as ViewGroup, false) as TextView
        customText.text = message
        customText.typeface = ResourcesCompat.getFont(this, R.font.blkchcry)
        customText.setTextColor(ContextCompat.getColor(this, R.color.text_on_container))
        customText.textSize = 18f
        customText.textAlignment = View.TEXT_ALIGNMENT_CENTER

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.CENTER
        customText.layoutParams = lp

        snackbarView.addView(customText)

        snackbar.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        @Suppress("DEPRECATION")
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_main)
        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_main)
    }

    // Helper to build the root layout
    private fun createRootLayout(contentView: View): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            fitsSystemWindows = true

            // 1. BlurTarget – hosts the main content
            val blurTarget = BlurTarget(this@BaseActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(contentView, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                id = View.generateViewId()
            }
            addView(blurTarget)

            if (useRail) {
                // Scrollable container for custom button bar
                val railContainer = NestedScrollView(this@BaseActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        resources.getDimensionPixelSize(R.dimen.navigation_rail_width),
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.START
                    )
                    isFillViewport = true
                    isScrollContainer = true
                    isNestedScrollingEnabled = true
                }

                val buttonBar = LinearLayout(this@BaseActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(ContextCompat.getColor(context, R.color.bg_main))
                }

                // Inflate and add month selector header
                val header = layoutInflater.inflate(R.layout.nav_rail_header, buttonBar, false)
                buttonBar.addView(header)

                // Helper to create an icon‑only nav button
                fun createNavButton(destination: NavDestination, iconRes: Int, bgRes: Int): MaterialButton {
                    return MaterialButton(this@BaseActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            resources.getDimensionPixelSize(R.dimen.button_height)
                        ).apply {
                            topMargin = 4
                            bottomMargin = 4
                        }
                        setIconResource(iconRes)
                        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                        setBackgroundResource(bgRes)
                        backgroundTintList = null
                        iconTint = ContextCompat.getColorStateList(context, R.color.text_on_main)
                        setOnClickListener {
                            when (destination) {
                                NavDestination.HOME -> navigateToHome()
                                NavDestination.FINANCES -> navigateToFinances()
                                NavDestination.EXPENSES -> navigateToExpenses()
                                NavDestination.SPENDING -> navigateToSpending()
                                NavDestination.CALENDAR -> navigateToCalendar()
                                NavDestination.SETTINGS -> navigateToSettings()
                            }
                            // Update rail visibility after navigation
                            updateRailSelection(destination)
                        }
                    }
                }

                railButtons[NavDestination.HOME] = createNavButton(
                    NavDestination.HOME,
                    R.drawable.ic_cottage,
                    R.drawable.bg_nav_button_home
                )
                buttonBar.addView(railButtons[NavDestination.HOME])

                railButtons[NavDestination.FINANCES] = createNavButton(
                    NavDestination.FINANCES,
                    R.drawable.ic_account_balance,
                    R.drawable.bg_nav_button_finances
                )
                buttonBar.addView(railButtons[NavDestination.FINANCES])

                railButtons[NavDestination.EXPENSES] = createNavButton(
                    NavDestination.EXPENSES,
                    R.drawable.ic_receipt,
                    R.drawable.bg_nav_button_expenses
                )
                buttonBar.addView(railButtons[NavDestination.EXPENSES])

                railButtons[NavDestination.SPENDING] = createNavButton(
                    NavDestination.SPENDING,
                    R.drawable.ic_paid,
                    R.drawable.bg_nav_button_spending
                )
                buttonBar.addView(railButtons[NavDestination.SPENDING])

                railButtons[NavDestination.CALENDAR] = createNavButton(
                    NavDestination.CALENDAR,
                    R.drawable.ic_calendar_today,
                    R.drawable.bg_nav_button_calendar
                )
                buttonBar.addView(railButtons[NavDestination.CALENDAR])

                railButtons[NavDestination.SETTINGS] = createNavButton(
                    NavDestination.SETTINGS,
                    R.drawable.ic_settings,
                    R.drawable.bg_nav_button_settings
                )
                buttonBar.addView(railButtons[NavDestination.SETTINGS])

                railContainer.addView(buttonBar)
                addView(railContainer)

                // Adjust blurTarget margin to start after rail container
                (blurTarget.layoutParams as FrameLayout.LayoutParams).marginStart =
                    resources.getDimensionPixelSize(R.dimen.navigation_rail_width)

                // Set up month spinner in header
                val spinner = header.findViewById<Spinner>(R.id.monthSpinnerRail)
                spinner?.let { setupMonthSpinner(it) }

                // Initialize rail visibility
                updateRailSelection(currentNavDestination)
            } else {
                // 2. Month selector – aligned to top (with blur)
                val monthSelectorBlurView = BlurView(this@BaseActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.TOP }
                }
                monthSelectorBinding = MonthSelectorBinding.inflate(layoutInflater, monthSelectorBlurView, true)
                addView(monthSelectorBlurView)

                // 3. Bottom navigation – aligned to bottom (with blur)
                navBinding = LayoutBottomNavBinding.inflate(layoutInflater, this, true)
                (navBinding.root.layoutParams as? FrameLayout.LayoutParams)?.gravity = Gravity.BOTTOM
                val bottomBlurView = navBinding.root

                // Configure blur for both BlurViews
                bottomBlurView.setupWith(blurTarget)
                    .setBlurRadius(10f)
                    .setBlurEnabled(true)
                monthSelectorBlurView.setupWith(blurTarget)
                    .setBlurRadius(10f)
                    .setBlurEnabled(true)

                // Set up month selector in the usual place
                setupMonthSelector()
            }
        }.also { outerRoot = it }
    }

    // ----------------------------------------------------------------------
    // setContentView overloads
    // ----------------------------------------------------------------------
    override fun setContentView(layoutResID: Int) {
        val contentView = LayoutInflater.from(this).inflate(layoutResID, null, false)
        val rootLayout = createRootLayout(contentView)
        super.setContentView(rootLayout)

        if (!useRail) {
            setupBottomNavigation()
        }
    }

    override fun setContentView(view: View?) {
        val rootLayout = createRootLayout(view!!)
        super.setContentView(rootLayout)

        if (!useRail) {
            setupBottomNavigation()
        }
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        val rootLayout = createRootLayout(view!!)
        super.setContentView(rootLayout, params)

        if (!useRail) {
            setupBottomNavigation()
        }
    }

    private fun setupBottomNavigation() {
        navigationManager = NavigationManager(navBinding) { destination ->
            when (destination) {
                NavDestination.HOME -> navigateToHome()
                NavDestination.FINANCES -> navigateToFinances()
                NavDestination.EXPENSES -> navigateToExpenses()
                NavDestination.SPENDING -> navigateToSpending()
                NavDestination.CALENDAR -> navigateToCalendar()
                NavDestination.SETTINGS -> navigateToSettings()
            }
        }
        navigationManager.updateForDestination(currentNavDestination)
    }

    // ----------------------------------------------------------------------
    // Month selector logic (refactored)
    // ----------------------------------------------------------------------
    private fun generateAllMonths() {
        allMonths.clear()
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        val savedMonth = prefs.getInt("selected_month", currentMonth)
        val savedYear = prefs.getInt("selected_year", currentYear)

        repeat(12) { i ->
            calendar.set(currentYear, currentMonth - 1, 1)
            calendar.add(Calendar.MONTH, -i - 1)
            allMonths.add(0, Month(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1
            ))
        }

        allMonths.add(Month(currentYear, currentMonth))

        repeat(12) { i ->
            calendar.set(currentYear, currentMonth - 1, 1)
            calendar.add(Calendar.MONTH, i + 1)
            allMonths.add(Month(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1
            ))
        }

        isSettingMonthProgrammatically = true
        selectedMonth = Month(savedYear, savedMonth)
        isSettingMonthProgrammatically = false
    }

    private fun setupMonthSpinner(spinner: Spinner) {
        generateAllMonths()
        val monthNames = allMonths.map { it.getDisplayName(this) }

        monthAdapter = object : ArrayAdapter<String>(
            this,
            R.layout.spinner_closed_month,
            android.R.id.text1,
            monthNames
        ) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val divider = view.findViewById<View>(R.id.divider)
                divider?.visibility = if (position == count - 1) View.GONE else View.VISIBLE
                return view
            }
        }
        monthAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_month)

        spinner.adapter = monthAdapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isSettingUpSpinner) return
                if (position < allMonths.size) {
                    val selected = allMonths[position]
                    if (selected != selectedMonth) {
                        selectedMonth = selected
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val currentIndex = allMonths.indexOfFirst {
            it.year == selectedMonth.year && it.month == selectedMonth.month
        }
        if (currentIndex >= 0) {
            isSettingUpSpinner = true
            spinner.setSelection(currentIndex)
            isSettingUpSpinner = false
        }
    }

    private fun setupMonthSelector() {
        generateAllMonths()
        setupMonthSpinner(monthSelectorBinding.monthSpinner)
    }

    // ----------------------------------------------------------------------
    // Month change listeners (unchanged)
    // ----------------------------------------------------------------------
    private val monthChangeListeners = mutableListOf<MonthChangeListener>()

    fun addMonthChangeListener(listener: MonthChangeListener) {
        monthChangeListeners.add(listener)
        listener.onMonthChanged(selectedMonth)
    }

    fun removeMonthChangeListener(listener: MonthChangeListener) {
        monthChangeListeners.remove(listener)
    }

    protected open fun onMonthChanged(month: Month) {
        Timber.d("Month changed to: ${month.getDisplayName(this)}")
    }

    protected open fun onMonthSelected(month: Month) {
        prefs.edit {
            putInt("selected_month", month.month)
            putInt("selected_year", month.year)
        }

        val currentIndex = allMonths.indexOfFirst {
            it.year == month.year && it.month == month.month
        }
        if (currentIndex >= 0) {
            isSettingMonthProgrammatically = true
            // update both possible spinners
            if (!useRail && this::monthSelectorBinding.isInitialized) {
                monthSelectorBinding.monthSpinner.setSelection(currentIndex)
            } else {
                val rail = findViewById<NavigationRailView>(R.id.navigationRail)
                rail?.headerView?.findViewById<Spinner>(R.id.monthSpinnerRail)?.setSelection(currentIndex)
            }
            isSettingMonthProgrammatically = false
        }

        monthChangeListeners.toList().forEach { it.onMonthChanged(month) }
        onMonthChanged(month)
    }

    override fun onResume() {
        super.onResume()
        val savedMonth = prefs.getInt("selected_month", Calendar.getInstance().get(Calendar.MONTH) + 1)
        val savedYear = prefs.getInt("selected_year", Calendar.getInstance().get(Calendar.YEAR))
        if (selectedMonth.month != savedMonth || selectedMonth.year != savedYear) {
            isSettingMonthProgrammatically = true
            selectedMonth = Month(savedYear, savedMonth)
            isSettingMonthProgrammatically = false
            // update spinners
            if (!useRail && this::monthSelectorBinding.isInitialized) {
                updateMonthSelector()
            } else {
                val rail = findViewById<NavigationRailView>(R.id.navigationRail)
                rail?.headerView?.findViewById<Spinner>(R.id.monthSpinnerRail)?.let {
                    setupMonthSpinner(it) // re‑setup to reflect new selection
                }
            }
        }
        // Update rail selection
        if (useRail) {
            updateRailSelection(currentNavDestination)
        }
    }

    private fun updateMonthSelector() {
        // Only used in non‑rail mode
        if (!useRail && this::monthSelectorBinding.isInitialized) {
            val currentIndex = allMonths.indexOfFirst {
                it.year == selectedMonth.year && it.month == selectedMonth.month
            }
            if (currentIndex >= 0) {
                monthSelectorBinding.monthSpinner.setSelection(currentIndex)
            }
        }
    }

    private fun updateRailSelection(destination: NavDestination) {
        // Only used in rail mode
        if (!useRail) return

        railButtons.forEach { (dest, button) ->
            button.visibility = if (dest == destination) View.GONE else View.VISIBLE
        }
    }

    // ----------------------------------------------------------------------
    // Navigation methods (to be overridden by subclasses)
    // ----------------------------------------------------------------------
    protected open fun navigateToHome() { }
    protected open fun navigateToFinances() { }
    protected open fun navigateToExpenses() { }
    protected open fun navigateToSpending() { }
    protected open fun navigateToCalendar() { }
    protected open fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}