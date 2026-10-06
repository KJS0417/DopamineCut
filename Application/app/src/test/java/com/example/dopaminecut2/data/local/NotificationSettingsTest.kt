package com.example.dopaminecut2.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsTest {
    @Test
    fun `new account defaults to all three slots and independent notifications`() {
        assertEquals(NotificationOption.entries.toSet(), NotificationSettings.fromStorageIds(null).enabledOptions)
    }

    @Test
    fun `disabling lunch does not change other slots or notification types`() {
        val settings = NotificationSettings().withEnabled(NotificationOption.LUNCH, false)
        assertFalse(settings.isEnabled(NotificationOption.LUNCH))
        assertTrue(settings.isEnabled(NotificationOption.MORNING))
        assertTrue(settings.isEnabled(NotificationOption.EVENING))
        assertTrue(settings.isEnabled(NotificationOption.GOAL_PROGRESS))
        assertTrue(settings.isEnabled(NotificationOption.MEASUREMENT_INTERRUPTION))
    }

    @Test
    fun `empty stored selection remains empty rather than restoring defaults`() {
        assertTrue(NotificationSettings.fromStorageIds(emptySet()).enabledOptions.isEmpty())
    }

    @Test
    fun `selection round trips and unknown identifiers are ignored`() {
        val settings = NotificationSettings().withEnabled(NotificationOption.LUNCH, false)
        assertEquals(settings, NotificationSettings.fromStorageIds(settings.storageIds() + "unknown"))
    }

    @Test
    fun `disabling all motivation slots leaves other notification types enabled`() {
        val settings = listOf(NotificationOption.MORNING, NotificationOption.LUNCH, NotificationOption.EVENING)
            .fold(NotificationSettings()) { current, slot -> current.withEnabled(slot, false) }
        assertEquals(setOf(NotificationOption.GOAL_PROGRESS, NotificationOption.MEASUREMENT_INTERRUPTION), settings.enabledOptions)
    }
}
