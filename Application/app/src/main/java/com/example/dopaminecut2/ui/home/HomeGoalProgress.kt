package com.example.dopaminecut2.ui.home

import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.domain.GoalMetric
import com.example.dopaminecut2.domain.GoalStatus
import com.example.dopaminecut2.domain.ManagedGoal

/** Time remains in seconds; count remains an integer. Never turn a failed read into zero. */
data class HomeGoalProgress(val goal: ManagedGoal, val used: Long?, val limit: Long) {
    val progress: Int? get() = used?.let {
        if (limit == 0L) { if (it > 0) 1000 else 0 }
        else (it.toDouble() / limit * 1000).toInt().coerceIn(0, 1000)
    }
    val exceeded: Boolean get() = used?.let { it > limit } ?: false
}

object HomeGoalProgressMapper {
    fun map(goals: List<ManagedGoal>, statistics: DailyStatistics?): List<HomeGoalProgress> = goals
        .filter { it.status == GoalStatus.ACTIVE }.sortedBy { it.metric.ordinal }.map { goal ->
            val count = goal.metric == GoalMetric.DAILY_COUNT
            HomeGoalProgress(goal, statistics?.let { stats -> goal.platforms.sumOf { platform ->
                val usage = stats.appUsage[platform.storageKey]
                when (goal.metric) {
                    GoalMetric.DAILY_TIME -> usage?.shortformTimeSec ?: 0L
                    GoalMetric.DAILY_COUNT -> usage?.shortformCount ?: 0L
                    GoalMetric.APP_TIME -> usage?.runTimeSec ?: 0L
                }.coerceAtLeast(0)
            } }, goal.target.toLong() * if (count) 1 else 60)
        }
}
