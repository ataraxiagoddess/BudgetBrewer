package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.core.content.ContextCompat
import com.ataraxiagoddess.budgetbrewer.R

class ColorAdapter(
    private val context: Context,
    private val colors: List<Int>
) : BaseAdapter() {

    var selectedPosition: Int = 0

    override fun getCount(): Int = colors.size
    override fun getItem(position: Int): Int = colors[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_color_swatch, parent, false)

        val swatch: View = view.findViewById(R.id.swatchImage)
        val border: View = view.findViewById(R.id.swatchBorder)

        val colorInt = ContextCompat.getColor(context, colors[position])

        // Create a circular drawable with the selected color
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorInt)
        }
        swatch.background = circle

        border.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

        return view
    }
}