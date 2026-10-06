package com.example.dopaminecut2

import android.view.LayoutInflater
import android.widget.CheckBox
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dopaminecut2.data.local.DataStoreManager
import com.example.dopaminecut2.data.local.OnboardingFlowPolicy
import com.example.dopaminecut2.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GoalStoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun goalEditor_hasIndependentMetricCheckboxes() {
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_goal_editor, null)
        listOf(R.id.metric_time, R.id.metric_count, R.id.metric_app_time).forEach {
            assertNotNull(view.findViewById<CheckBox>(it))
        }
    }

    @Test fun goals_surviveStoreRecreationAndRemainAccountIsolated() = runBlocking {
        val owner = "goal-test-${UUID.randomUUID()}"
        val other = "goal-test-${UUID.randomUUID()}"
        val store = DataStoreManager(context)
        val goals = GoalMetric.entries.map { ManagedGoal(it, 30, setOf(SupportedPlatform.YOUTUBE), InterventionMode.RECORD_ONLY) }
        store.saveGoals(owner, goals)
        val recreated = DataStoreManager(context)
        assertEquals(3, recreated.observeGoals(owner).first().size)
        assertTrue(recreated.observeGoals(other).first().isEmpty())
        recreated.setGoalStatus(owner, GoalMetric.DAILY_TIME, 1, GoalStatus.PAUSED)
        assertEquals(GoalStatus.PAUSED, store.observeGoals(owner).first().first().status)
        store.setGoalStatus(owner, GoalMetric.DAILY_TIME, 2, GoalStatus.ACTIVE)
        assertEquals(30, recreated.observeGoals(owner).first().first().target)
    }

    @Test fun invalidBatch_hasNoPartialPersistence() = runBlocking {
        val owner = "goal-test-${UUID.randomUUID()}"
        val store = DataStoreManager(context)
        val valid = ManagedGoal(GoalMetric.DAILY_TIME, 30, setOf(SupportedPlatform.YOUTUBE), InterventionMode.RECORD_ONLY)
        try {
            store.saveGoals(owner, listOf(valid, valid.copy(metric = GoalMetric.DAILY_COUNT, target = 1000)))
            fail("Invalid batch must fail")
        } catch (_: IllegalArgumentException) {
            assertTrue(store.observeGoals(owner).first().isEmpty())
            assertFalse(OnboardingFlowPolicy.MANAGED_GOALS in store.observeOnboarding(owner).first().answers[OnboardingFlowPolicy.GOAL_MODE_KEY].orEmpty())
        }
    }
}
