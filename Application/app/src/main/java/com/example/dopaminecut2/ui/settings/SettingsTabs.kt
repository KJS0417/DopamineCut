package com.example.dopaminecut2.ui.settings

import androidx.core.view.isVisible
import com.example.dopaminecut2.databinding.FragmentSettingsBinding

enum class SettingsSection(val key: String, val label: String) {
    NOTIFICATIONS("notifications", "알림 설정"), ACCOUNT("account", "계정"),
    MEASUREMENT("measurement", "측정 상태"), ANALYSIS("analysis", "수집·분석 범위"),
    DATA("data", "데이터 및 연결정보"), APP_INFO("app_info", "앱 정보");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: NOTIFICATIONS
    }
}

/** Presentation-only tabs. Switching panels never saves preferences or calls Firebase. */
class SettingsTabs(private val binding: FragmentSettingsBinding, initial: SettingsSection) {
    private val panels = listOf(binding.panelNotifications, binding.panelAccount, binding.panelMeasurement,
        binding.panelAnalysis, binding.panelData, binding.panelAppInfo)
    private val buttons = listOf(binding.tabNotifications, binding.tabAccount, binding.tabMeasurement,
        binding.tabAnalysis, binding.tabData, binding.tabAppInfo)
    private val scrollPositions = mutableMapOf<SettingsSection, Int>()
    var selected = initial
        private set

    init {
        buttons.forEachIndexed { index, button ->
            button.isCheckable = true
            button.setOnClickListener { select(SettingsSection.entries[index]) }
        }
        show()
    }

    fun select(section: SettingsSection) {
        if (selected != section) scrollPositions[selected] = binding.settingsScroll.scrollY
        selected = section
        show()
        binding.settingsScroll.post {
            if (selected == section) binding.settingsScroll.scrollTo(0, scrollPositions[section] ?: 0)
        }
    }

    private fun show() {
        panels.forEachIndexed { index, panel -> panel.isVisible = index == selected.ordinal }
        buttons.forEachIndexed { index, button -> button.isChecked = index == selected.ordinal }
        binding.tvSettingsSectionTitle.text = selected.label
    }
}
