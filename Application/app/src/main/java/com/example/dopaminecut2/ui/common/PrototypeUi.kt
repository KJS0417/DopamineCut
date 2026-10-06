package com.example.dopaminecut2.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.dopaminecut2.R
import com.google.android.material.button.MaterialButton

/** Shared presentation only; goal decisions and calculations remain in domain/ViewModel. */
object PrototypeUi {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun column(context: Context, selected: Boolean = false) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12) }
        setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
        setBackgroundResource(R.drawable.ui_card_background)
        isSelected = selected
    }
    fun text(context: Context, value: String, size: Float = 14f, color: Int = R.color.ui_text_secondary) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(ContextCompat.getColor(context, color))
        setLineSpacing(dp(context, 4).toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8) }
    }
    fun title(context: Context, value: String) = text(context, value, 16f, R.color.ui_text_primary).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    fun pill(context: Context, value: String) = text(context, value, 12f, R.color.ui_primary).apply {
        layoutParams = LinearLayout.LayoutParams(-2, -2)
        setBackgroundResource(R.drawable.ui_pill_background)
    }
    fun divider(context: Context) = View(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.ui_outline))
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, 1)).apply { topMargin = dp(context, 12) }
    }
    fun row(context: Context, label: String, value: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(text(context, label, 13f).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        addView(text(context, value, 13f, R.color.ui_text_primary).apply { layoutParams = LinearLayout.LayoutParams(-2, -2) })
    }
    fun button(context: Context, label: String, action: () -> Unit) = MaterialButton(
        context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = label
        textSize = 13f
        minHeight = dp(context, 48)
        cornerRadius = dp(context, 12)
        strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ui_outline))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8) }
        setOnClickListener { action() }
    }
}
