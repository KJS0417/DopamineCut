package com.example.dopaminecut2

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.RadioButton
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dopaminecut2.main.MainActivity
import com.example.dopaminecut2.service.AppBlockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CriticalConfigurationInstrumentedTest {

    @Test
    fun interventionSetup_hasNoPreselectedModeAndRequiresChoice() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.Theme_Dopaminecut2)
            val view = LayoutInflater.from(themed).inflate(R.layout.fragment_intervention_setup, null)
            assertFalse(view.findViewById<View>(R.id.btn_continue).isEnabled)
            listOf(R.id.mode_record, R.id.mode_notify, R.id.mode_confirm, R.id.mode_restrict).forEach { id ->
                val option = view.findViewById<RadioButton>(id)
                assertNotNull(option)
                assertFalse(option.isChecked)
            }
        }
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mainActivity_cannotBeLaunchedByAnotherApp() {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0
        )

        assertFalse(info.exported)
    }

    @Test
    fun accessibilityService_requiresSystemBindingPermission() {
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, AppBlockService::class.java),
            PackageManager.GET_META_DATA
        )

        assertTrue(info.exported)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, info.permission)
        assertNotNull(info.metaData)
    }


}
