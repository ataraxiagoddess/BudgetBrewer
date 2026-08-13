/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ataraxiagoddess.budgetbrewer.R

class ColorAdapter(
    private val context: Context,
    private val colors: List<Int>,
    private val onItemClicked: (Int) -> Unit,
) : RecyclerView.Adapter<ColorAdapter.ViewHolder>() {
    var selectedPosition: Int = 0

    class ViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        val swatch: View = itemView.findViewById(R.id.swatchImage)
        val border: View = itemView.findViewById(R.id.swatchBorder)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_color_swatch, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val colorInt = ContextCompat.getColor(context, colors[position])
        val circle =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorInt)
            }
        holder.swatch.background = circle
        holder.border.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onItemClicked(position) }

        val colorName = context.resources.getResourceEntryName(colors[position]).replaceFirstChar { it.uppercase() }
        holder.itemView.contentDescription = colorName
    }

    override fun getItemCount(): Int = colors.size
}
