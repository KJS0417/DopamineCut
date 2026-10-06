package com.example.dopaminecut2

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class TabTitleInstrumentedTest {
    @Test fun allMainTabsShareBoldTitleTypography() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Dopaminecut2)
            val titles = listOf(R.layout.fragment_home, R.layout.fragment_goal,
                R.layout.fragment_stats, R.layout.fragment_settings).map { resource ->
                LayoutInflater.from(context).inflate(resource, null).findViewById<TextView>(R.id.tab_title)
            }
            titles.forEach { title ->
                assertTrue(title.typeface.isBold)
                assertEquals(titles.first().typeface, title.typeface)
                assertEquals(titles.first().textSize, title.textSize, 0.01f)
                assertEquals(titles.first().currentTextColor, title.currentTextColor)
            }
        }
    }
}
