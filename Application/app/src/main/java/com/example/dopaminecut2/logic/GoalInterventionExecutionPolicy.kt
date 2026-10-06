package com.example.dopaminecut2.logic

import com.example.dopaminecut2.domain.GoalStatus
import com.example.dopaminecut2.domain.InterventionMode

enum class GoalInterventionAction { NONE, NOTIFY, CONFIRM, RESTRICT }

/** 권한·알림 이력·연장 상태를 적용한다. 알림 설정은 확인·제한 방식에 영향을 주지 않는다. */
object GoalInterventionExecutionPolicy {
    fun action(
        request: GoalInterventionRequest,
        notificationsEnabled: Boolean,
        alreadyNotified: Boolean,
        snoozedUntilElapsedMs: Long,
        nowElapsedMs: Long
    ): GoalInterventionAction {
        if (request.goal.status != GoalStatus.ACTIVE) return GoalInterventionAction.NONE
        return when (request.goal.intervention) {
            InterventionMode.RECORD_ONLY -> GoalInterventionAction.NONE
            InterventionMode.NOTIFY -> if (notificationsEnabled && !alreadyNotified) {
                GoalInterventionAction.NOTIFY
            } else GoalInterventionAction.NONE
            InterventionMode.CONFIRM -> if (request.threshold == GoalThreshold.REACHED &&
                nowElapsedMs >= snoozedUntilElapsedMs) GoalInterventionAction.CONFIRM else GoalInterventionAction.NONE
            InterventionMode.RESTRICT -> if (request.threshold == GoalThreshold.REACHED) {
                GoalInterventionAction.RESTRICT
            } else GoalInterventionAction.NONE
        }
    }
}
