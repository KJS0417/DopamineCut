package com.example.dopaminecut2.ui.goal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalEditorViewModelTest {
    @Test fun `each card keeps independent apps and intervention`() {
        val vm = GoalEditorViewModel()
        vm.onAction(GoalEditorAction.TogglePlatform("YOUTUBE"))
        vm.onAction(GoalEditorAction.SelectIntervention(GoalIntervention.NOTIFY))
        vm.onAction(GoalEditorAction.EditCard(GoalMetric.APP_TIME))
        vm.onAction(GoalEditorAction.TogglePlatform("KAKAOTALK"))
        vm.onAction(GoalEditorAction.SelectIntervention(GoalIntervention.RECORD_ONLY))
        vm.onAction(GoalEditorAction.EditCard(GoalMetric.DAILY_TIME))
        assertEquals(setOf("YOUTUBE"), vm.uiState.value.selectedPlatforms)
        assertEquals(GoalIntervention.NOTIFY, vm.uiState.value.intervention)
        assertEquals(setOf("KAKAOTALK"), vm.uiState.value.platformsByMetric[GoalMetric.APP_TIME])
    }
    @Test
    fun `multiple selected goals retain separate input values`() {
        val viewModel = GoalEditorViewModel()
        GoalMetric.entries.forEachIndexed { index, metric ->
            viewModel.onAction(GoalEditorAction.SetMetricEnabled(metric, true))
            viewModel.onAction(GoalEditorAction.UpdateMetricValue(metric, (index + 10).toString()))
        }
        assertEquals(GoalMetric.entries.toSet(), viewModel.uiState.value.selectedMetrics)
        assertEquals(listOf("10", "11", "12"), GoalMetric.entries.map { viewModel.uiState.value.values[it] })
    }

    @Test
    fun `deselecting a goal preserves draft for selecting again`() {
        val viewModel = GoalEditorViewModel()
        viewModel.onAction(GoalEditorAction.UpdateMetricValue(GoalMetric.DAILY_TIME, "30"))
        viewModel.onAction(GoalEditorAction.SetMetricEnabled(GoalMetric.DAILY_TIME, false))
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.onAction(GoalEditorAction.SetMetricEnabled(GoalMetric.DAILY_TIME, true))
        assertEquals("30", viewModel.uiState.value.values[GoalMetric.DAILY_TIME])
    }

    @Test
    fun `zero target is valid and is not a stop action`() {
        val viewModel = GoalEditorViewModel()
        viewModel.onAction(GoalEditorAction.UpdateValue("0"))
        viewModel.onAction(GoalEditorAction.TogglePlatform("YOUTUBE"))
        assertNull(viewModel.uiState.value.validationMessage)
        assertFalse(viewModel.uiState.value.canSubmit)
    }
    @Test
    fun `every intervention selection preserves target and platform without activating a goal`() {
        val viewModel = GoalEditorViewModel()
        viewModel.onAction(GoalEditorAction.UpdateValue("60"))
        viewModel.onAction(GoalEditorAction.TogglePlatform("YOUTUBE"))
        GoalIntervention.entries.forEach { intervention ->
            viewModel.onAction(GoalEditorAction.SelectIntervention(intervention))
            assertEquals(intervention, viewModel.uiState.value.intervention)
            assertEquals("60", viewModel.uiState.value.valueText)
            assertEquals(setOf("YOUTUBE"), viewModel.uiState.value.selectedPlatforms)
            assertFalse(viewModel.uiState.value.canSubmit)
        }
    }

    @Test
    fun `app time is a target limit in minutes and validates its range`() {
        val viewModel = GoalEditorViewModel()
        viewModel.onAction(GoalEditorAction.SelectMetric(GoalMetric.APP_TIME))
        viewModel.onAction(GoalEditorAction.TogglePlatform("YOUTUBE"))
        viewModel.onAction(GoalEditorAction.UpdateValue("1440"))
        assertNull(viewModel.uiState.value.validationMessage)
        viewModel.onAction(GoalEditorAction.UpdateValue("1441"))
        assertNotNull(viewModel.uiState.value.validationMessage)
    }

    @Test
    fun `changing intervention keeps target input and remains a draft`() {
        val viewModel = GoalEditorViewModel()
        viewModel.onAction(GoalEditorAction.UpdateValue("60"))
        viewModel.onAction(GoalEditorAction.SelectIntervention(GoalIntervention.CONFIRM))
        assertEquals("60", viewModel.uiState.value.valueText)
        assertEquals(GoalIntervention.CONFIRM, viewModel.uiState.value.intervention)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `valid draft is previewable but cannot save before integration`() {
        val viewModel = GoalEditorViewModel()

        viewModel.onAction(GoalEditorAction.UpdateValue("50"))
        viewModel.onAction(GoalEditorAction.TogglePlatform("YOUTUBE"))

        assertNull(viewModel.uiState.value.validationMessage)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `daily count rejects values outside documented range`() {
        val viewModel = GoalEditorViewModel()

        viewModel.onAction(GoalEditorAction.SelectMetric(GoalMetric.DAILY_COUNT))
        viewModel.onAction(GoalEditorAction.UpdateValue("1000"))
        viewModel.onAction(GoalEditorAction.TogglePlatform("YOUTUBE"))

        assertNotNull(viewModel.uiState.value.validationMessage)
        assertFalse(viewModel.uiState.value.canSubmit)
    }
}
