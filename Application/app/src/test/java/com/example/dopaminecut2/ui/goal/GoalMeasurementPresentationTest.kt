package com.example.dopaminecut2.ui.goal

import com.example.dopaminecut2.ui.common.MeasurementPermissionState
import org.junit.Assert.*
import org.junit.Test

class GoalMeasurementPresentationTest {
    @Test fun eitherMissingPermissionPreventsCollectingLabel() {
        listOf(false to false, false to true, true to false).forEach { (accessibility, usage) ->
            val state = GoalMeasurementPresentation.from(MeasurementPermissionState(accessibility, usage), null, "")
            assertTrue(state.permissionRequired)
            assertEquals("권한 필요", state.primary)
        }
    }
    @Test fun namesOnlyMissingPermissions() {
        assertEquals("접근성", GoalMeasurementPresentation.from(MeasurementPermissionState(false, true), null, "").missingPermissions)
        assertEquals("사용정보 접근", GoalMeasurementPresentation.from(MeasurementPermissionState(true, false), null, "").missingPermissions)
    }
    @Test fun grantedPermissionsShowCollectingOrRealAverage() {
        val permissions = MeasurementPermissionState(true, true)
        assertEquals("기록 수집 중", GoalMeasurementPresentation.from(permissions, null, "").primary)
        val state = GoalMeasurementPresentation.from(permissions, "42분", "YouTube 42분")
        assertEquals("42분", state.primary)
        assertEquals("YouTube 42분", state.detail)
        assertFalse(state.permissionRequired)
    }
    @Test fun revokedPermissionDoesNotPresentOldAverageAsCurrentCollection() {
        val state = GoalMeasurementPresentation.from(MeasurementPermissionState(true, false), "42분", "YouTube")
        assertEquals("권한 필요", state.primary)
        assertEquals("기존 평균: 42분", state.detail)
    }
}
