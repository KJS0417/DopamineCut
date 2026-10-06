package com.example.dopaminecut2.ui.stats

import com.example.dopaminecut2.data.local.GoalPlan
import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.domain.*

data class StatsGoalComparison(val average: Double?, val baseline: Double?, val achieved: Int,
    val evaluated: Int, val notice: String)

object StatsGoalAnalysis {
    fun compare(goal: ManagedGoal, plan: GoalPlan?, days: List<DailyStatistics>, quality: List<DayQuality>, today: String): StatsGoalComparison {
        if (plan == null || plan.revision != goal.revision || plan.metric != goal.metric ||
            (plan.baseline != null && plan.baseline.platforms != goal.platforms)) {
            return StatsGoalComparison(null, null, 0, 0, "현재 목표와 일치하는 비교 이력이 없어요.")
        }
        val valid = quality.filter { it.valid }.map { it.date }.toSet()
        val values = days.distinctBy { it.date }.filter { it.date < today && it.date in valid && plan.eligible(it.date) }.map { day ->
            goal.platforms.sumOf { platform -> HabitInsightsPolicy.value(day.appUsage[platform.storageKey] ?: AppUsage(), goal.metric) }
        }
        return StatsGoalComparison(values.takeIf { it.isNotEmpty() }?.average(), plan.baseline?.average,
            values.count { it <= goal.target }, values.size,
            if (values.isEmpty()) "평가 가능한 완료일 기록이 없어요." else "목표 적용 앱 전체 · 유효 완료일 기준")
    }
}
