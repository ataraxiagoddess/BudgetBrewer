package com.ataraxiagoddess.budgetbrewer.data

import android.content.Context
import androidx.core.content.edit

object AuthManager {
    private const val PREFS_NAME = "auth"
    private const val KEY_USER_ID = "user_id"

    fun getUserId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, null)
    }

    fun saveUserId(context: Context, userId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_USER_ID, userId)
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            clear()
        }
    }
}