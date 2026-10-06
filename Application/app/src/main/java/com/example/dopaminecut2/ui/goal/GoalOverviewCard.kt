package com.example.dopaminecut2.ui.goal

import android.content.Context
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.dopaminecut2.R
import com.example.dopaminecut2.ui.common.PrototypeUi

/** Compact overview only: editing and persistence stay in the existing goal flow. */
object GoalOverviewCard {
    fun create(
        context: Context,
        title: String,
        average: String,
        platforms: String,
        status: String,
        selected: Boolean,
        editEnabled: Boolean,
        statusAction: String? = null,
        statusEnabled: Boolean = true,
        onSelection: (Boolean) -> Unit,
        onEdit: () -> Unit,
        onStatus: () -> Unit = {},
        permissionRequired: Boolean = false
    ): LinearLayout {
        val ui = PrototypeUi
        val card = ui.column(context, selected).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(context, 6)
            setPadding(ui.dp(context, 12), ui.dp(context, 2), ui.dp(context, 12), ui.dp(context, 6))
        }
        card.addView(CheckBox(context).apply {
            text = title
            textSize = 14f
            minHeight = ui.dp(context, 48)
            setTextColor(ContextCompat.getColor(context, R.color.ui_text_primary))
            isChecked = selected
            setOnCheckedChangeListener { _, checked ->
                card.isSelected = checked
                onSelection(checked)
            }
        })
        fun line(value: String, size: Float, color: Int) = ui.text(context, value, size, color).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = 0
            setLineSpacing(0f, 1f)
        }
        card.addView(line(average, 16f, if (permissionRequired) R.color.ui_warning else R.color.ui_text_primary))
        card.addView(line(platforms, 11f, R.color.ui_text_secondary).apply {
            visibility = if (platforms.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        })
        card.addView(line(status, 11f, R.color.ui_primary).apply {
            visibility = if (status.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        })
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            fun action(label: String, enabled: Boolean, callback: () -> Unit) = ui.button(context, label, callback).apply {
                textSize = 12f
                isEnabled = enabled
                insetTop = 0
                insetBottom = 0
                minimumWidth = 0
                setPadding(ui.dp(context, 4), 0, ui.dp(context, 4), 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = ui.dp(context, 4) }
            }
            addView(action("세부 설정 ›", editEnabled, onEdit))
            if (statusAction != null) addView(action(statusAction, statusEnabled, onStatus).apply {
                (layoutParams as LinearLayout.LayoutParams).marginStart = ui.dp(context, 6)
            })
        })
        return card
    }
}
