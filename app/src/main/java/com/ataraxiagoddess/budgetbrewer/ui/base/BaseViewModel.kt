package com.ataraxiagoddess.budgetbrewer.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.ui.finances.UiEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class BaseViewModel : ViewModel() {

    @Suppress("PropertyName")
    protected val _event = MutableSharedFlow<UiEvent>()
    val event: SharedFlow<UiEvent> = _event.asSharedFlow()

    protected fun emitError(errorResId: Int, throwable: Throwable? = null) {
        viewModelScope.launch {
            throwable?.let { Timber.e(it, "Error: ${it.message}") }
            _event.emit(UiEvent.ShowError(errorResId, throwable?.message))
        }
    }

    @Suppress("unused")
    protected fun emitMessage(messageResId: Int) {
        viewModelScope.launch {
            _event.emit(UiEvent.ShowMessage(messageResId))
        }
    }

    protected fun emitSuccess(successEvent: UiEvent) {
        viewModelScope.launch {
            _event.emit(successEvent)
        }
    }

    protected fun <T> safeLaunch(
        errorResId: Int = R.string.error_unknown,
        block: suspend () -> T
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                emitError(errorResId, e)
            }
        }
    }
}