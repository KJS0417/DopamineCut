package com.example.dopaminecut2

import androidx.test.platform.app.InstrumentationRegistry
import com.example.dopaminecut2.data.local.*
import com.example.dopaminecut2.domain.*
import com.example.dopaminecut2.statistics.UsageSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HabitStoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun fixedBaselineAndSettingsSurviveStoreRecreation() = runBlocking {
        val uid = "habit-test-${UUID.randomUUID()}"
        val store = HabitStore(context)
        val base = GoalBaseline(GoalMetric.DAILY_TIME, setOf(SupportedPlatform.YOUTUBE),
            mapOf(SupportedPlatform.YOUTUBE to 42.5), listOf("20261001", "20261002", "20261003"))
        val plan = GoalPlan(GoalMetric.DAILY_TIME, 3, "20261004", base, setOf("20261005"), null, 20)
        store.setSettings(uid, AnalysisSettings(false, false)); store.savePlan(uid, plan)
        assertEquals(plan, HabitStore(context).plans(uid)[GoalMetric.DAILY_TIME])
        assertEquals(AnalysisSettings(false, false), HabitStore(context).settings(uid).first())
        store.deleteEvidence(uid, null)
        assertTrue(store.plans(uid).isEmpty())
        assertEquals(AnalysisSettings(false, false), store.settings(uid).first())
    }
    @Test fun dateDeletionLeavesOtherDayAndOtherUserUntouched() = runBlocking {
        val local = DataStoreManager(context)
        val a = "delete-test-${UUID.randomUUID()}"; val b = "delete-test-${UUID.randomUUID()}"
        val today = java.time.LocalDate.now().format(HabitInsightsPolicy.format)
        val yesterday = java.time.LocalDate.now().minusDays(1).format(HabitInsightsPolicy.format)
        local.seedIfAbsent(UsageSnapshot.empty(a, today)); local.seedIfAbsent(UsageSnapshot.empty(a, yesterday))
        local.seedIfAbsent(UsageSnapshot.empty(b, today))
        local.upsertPendingRecognition(PendingRecognition(a, today, "s1", SupportedPlatform.YOUTUBE, 7, true, System.currentTimeMillis()))
        local.deleteUsage(a, today)
        assertNull(local.get(a, today)); assertNotNull(local.get(a, yesterday)); assertNotNull(local.get(b, today))
        assertTrue(local.pendingRecognitions(a).isEmpty())
        local.deleteUsage(a, null); local.deleteUsage(b, null)
    }
}
