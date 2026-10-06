package com.example.dopaminecut2

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dopaminecut2.databinding.FragmentSettingsBinding
import com.example.dopaminecut2.ui.common.PrototypeUi
import com.example.dopaminecut2.ui.settings.SettingsSection
import com.example.dopaminecut2.ui.settings.SettingsTabs
import org.junit.Assert.*
import org.junit.Test

class SettingsTabsInstrumentedTest {
    @Test fun tabsShowOnlyOnePanelAndKeepHabitAlertsVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Dopaminecut2)
            val binding = FragmentSettingsBinding.inflate(LayoutInflater.from(context))
            val tabs = SettingsTabs(binding, SettingsSection.ACCOUNT)
            val panels = listOf(binding.panelNotifications, binding.panelAccount, binding.panelMeasurement,
                binding.panelAnalysis, binding.panelData, binding.panelAppInfo)
            val buttons = listOf(binding.tabNotifications, binding.tabAccount, binding.tabMeasurement,
                binding.tabAnalysis, binding.tabData, binding.tabAppInfo)
            buttons.forEachIndexed { index, button ->
                button.performClick()
                assertEquals(SettingsSection.entries[index], tabs.selected)
                assertEquals(1, panels.count { it.visibility == View.VISIBLE })
                assertEquals(View.VISIBLE, panels[index].visibility)
                assertTrue(button.isChecked)
                assertEquals(View.VISIBLE, binding.settingsOverview.visibility)
            }
        }
    }

    @Test fun allTabsFitWithHabitAlertsOnCompactPhone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val configuration = Configuration(instrumentation.targetContext.resources.configuration).apply { fontScale = 1.3f }
            val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(configuration), R.style.Theme_Dopaminecut2)
            val binding = FragmentSettingsBinding.inflate(LayoutInflater.from(context))
            SettingsTabs(binding, SettingsSection.NOTIFICATIONS)
            val width = PrototypeUi.dp(context, 320)
            val height = PrototypeUi.dp(context, 640)
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, width, height)
            assertTrue("Overview and all six tabs must fit before the scrolling detail panel",
                binding.settingsOverview.bottom < height)
            assertTrue("Details need usable space", binding.settingsScroll.height >= PrototypeUi.dp(context, 48))
            val tabs = listOf(binding.tabNotifications, binding.tabAccount, binding.tabMeasurement,
                binding.tabAnalysis, binding.tabData, binding.tabAppInfo)
            tabs.forEach { button -> assertTrue(button.bottom <= binding.settingsTabGrid.height) }
        }
    }
}
