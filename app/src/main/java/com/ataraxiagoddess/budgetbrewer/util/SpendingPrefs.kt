/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object SpendingPrefs {
    private const val PREFS_NAME = "spending_prefs"
    private const val KEY_TAGS_ENABLED = "tags_enabled"
    private const val DEFAULT_TAGS_ENABLED = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isTagsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TAGS_ENABLED, DEFAULT_TAGS_ENABLED)
    }

    fun setTagsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_TAGS_ENABLED, enabled) }
    }
}
