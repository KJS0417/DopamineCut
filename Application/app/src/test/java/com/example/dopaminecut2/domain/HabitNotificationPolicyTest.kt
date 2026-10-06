package com.example.dopaminecut2.domain

import com.example.dopaminecut2.data.model.*
import com.example.dopaminecut2.notifications.*
import com.example.dopaminecut2.statistics.InsightSnapshot
import org.junit.Assert.*
import org.junit.Test

class HabitNotificationPolicyTest {
    private fun snapshot(yesterday: Long = 50): InsightSnapshot {
        val dates = (1..5).map { "2026100$it" }
        return InsightSnapshot(dates.map { date -> DailyStatistics(date = date, appUsage = mapOf("youtube" to
            AppUsage(3600, (if (date == "20261005") yesterday else 70) * 60, if (date == "20261005") 50 else 100))) },
            dates.map { DayQuality(it, 7200, 7200, 0, true) } + DayQuality("20261006", 7200, 7200, 0, false),
            "20261006", emptyMap(), dates.associateWith { 4200L } + ("20261006" to 3600L))
    }
    @Test fun morningUsesPriorDatesNotYesterdayInAverage() {
        val notices = HabitNotificationPolicy.morning(snapshot(), emptyList())
        assertEquals(setOf("N07", "N11"), notices.map { it.id }.toSet())
        assertTrue(notices.first { it.id == "N07" }.text.contains("20분"))
    }
    @Test fun invalidYesterdayProducesNoMorningResult() {
        val s = snapshot(); assertTrue(HabitNotificationPolicy.morning(s.copy(quality = s.quality.map { it.copy(complete = false) }), emptyList()).isEmpty())
    }
    @Test fun lunchNeverRepeatsHandledMorningId() {
        val notices = HabitNotificationPolicy.morning(snapshot(), emptyList())
        assertEquals("N07", HabitNotificationPolicy.pickMorningOrLunch(notices, listOf("N07", "N11"), emptySet())!!.id)
        assertEquals("N11", HabitNotificationPolicy.pickMorningOrLunch(notices, listOf("N07", "N11"), setOf("N07"))!!.id)
        assertNull(HabitNotificationPolicy.pickMorningOrLunch(notices, listOf("N07", "N11"), setOf("N07", "N11")))
    }
    @Test fun exactlyTenMinutesEveningDifferenceIsSkipped() {
        assertNull(HabitNotificationPolicy.evening(snapshot(), true))
    }
    @Test fun eveningDecreaseOverTenMinutesSendsN09() {
        val s = snapshot(); assertEquals("N09", HabitNotificationPolicy.evening(s.copy(time21 = s.time21 + (s.today to 3500L)), false)!!.id)
    }
    @Test fun eveningIncreaseRequiresActualOngoingUse() {
        val s = snapshot(); val increase = s.copy(time21 = s.time21 + (s.today to 4900L))
        assertNull(HabitNotificationPolicy.evening(increase, false))
        assertEquals("N10", HabitNotificationPolicy.evening(increase, true)!!.id)
    }
    @Test fun eveningNeedsThreeMatchingTimeSamples() {
        val s = snapshot(); assertNull(HabitNotificationPolicy.evening(s.copy(time21 = mapOf("20261001" to 4200L, s.today to 3000L)), true))
    }
    @Test fun noInventedCountToTimeConversion() {
        val s = snapshot(); assertNull(HabitNotificationPolicy.evening(s.copy(time21 = emptyMap()), true))
    }
}
