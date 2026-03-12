package com.ataraxiagoddess.budgetbrewer.ui.finances

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

class VerticalNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startY = 0f
    private var isScrollingVertical = false
    private val touchSlop = 10 // Minimum movement to be considered a scroll

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                isScrollingVertical = false
                // Let the parent and child handle the down event normally
                super.onInterceptTouchEvent(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)
                if (!isScrollingVertical && dy > dx && dy > touchSlop) {
                    // Vertical movement detected – start intercepting
                    isScrollingVertical = true
                    return super.onInterceptTouchEvent(ev)
                } else if (dx > dy && dx > touchSlop) {
                    // Horizontal movement – don't intercept
                    return false
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}