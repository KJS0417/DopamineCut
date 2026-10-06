package com.example.dopaminecut2.ui.goal

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalEditorSelectionTest {
    @Test fun selectedCardsAreCarriedIntoEditor() {
        val selected = setOf(GoalMetric.DAILY_COUNT, GoalMetric.APP_TIME)
        val model = GoalEditorViewModel(initialMetrics = selected)
        assertEquals(selected, model.uiState.value.selectedMetrics)
        assertEquals(GoalMetric.DAILY_COUNT, model.uiState.value.metric)
    }
    @Test fun emptyOrAbsentSelectionUsesSafeDefault() {
        assertEquals(setOf(GoalMetric.DAILY_TIME), GoalEditorViewModel(initialMetrics = emptySet()).uiState.value.selectedMetrics)
        assertEquals(setOf(GoalMetric.DAILY_TIME), GoalEditorViewModel().uiState.value.selectedMetrics)
    }
    @Test fun disablingFocusedCardSelectsRemainingCardWithoutDroppingDraft() {
        val model = GoalEditorViewModel(initialMetrics = setOf(GoalMetric.DAILY_TIME, GoalMetric.DAILY_COUNT))
        model.onAction(GoalEditorAction.UpdateMetricValue(GoalMetric.DAILY_COUNT, "40"))
        model.onAction(GoalEditorAction.SetMetricEnabled(GoalMetric.DAILY_TIME, false))
        assertEquals(GoalMetric.DAILY_COUNT, model.uiState.value.metric)
        assertEquals("40", model.uiState.value.values[GoalMetric.DAILY_COUNT])
    }
}
