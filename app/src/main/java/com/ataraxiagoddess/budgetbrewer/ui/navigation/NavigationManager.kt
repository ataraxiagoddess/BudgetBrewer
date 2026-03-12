package com.ataraxiagoddess.budgetbrewer.ui.navigation

import android.view.View
import com.ataraxiagoddess.budgetbrewer.databinding.LayoutBottomNavBinding
import timber.log.Timber

class NavigationManager(
    @Suppress("unused") private val binding: LayoutBottomNavBinding,
    private val onNavigate: (NavDestination) -> Unit
) {
    private var currentDestination: NavDestination = NavDestination.HOME

    // Map of button views to their destinations
    private val buttonMap = mapOf(
        binding.navButtonHome to NavDestination.HOME,
        binding.navButtonFinances to NavDestination.FINANCES,
        binding.navButtonExpenses to NavDestination.EXPENSES,
        binding.navButtonSpending to NavDestination.SPENDING,
        binding.navButtonCalendar to NavDestination.CALENDAR,
        binding.navButtonSettings to NavDestination.SETTINGS
    )

    init {
        setupClickListeners()
    }

    private fun setupClickListeners() {
        buttonMap.forEach { (button, destination) ->
            button.setOnClickListener {
                Timber.d("Button clicked: ${button.text} (destination=$destination, current=$currentDestination)")
                if (currentDestination != destination) {
                    Timber.d("Navigating to $destination")
                    onNavigate(destination)
                } else {
                    Timber.d("Ignoring click – already at $destination")
                }
            }
        }
    }

    fun updateForDestination(destination: NavDestination) {
        Timber.d("updateForDestination: $destination (was $currentDestination)")
        currentDestination = destination
        buttonMap.forEach { (button, dest) ->
            val visibility = if (dest == destination) View.GONE else View.VISIBLE
            button.visibility = visibility
            Timber.d("Button ${button.text} visibility: ${if (visibility == View.VISIBLE) "VISIBLE" else "GONE"}")
        }
    }
}