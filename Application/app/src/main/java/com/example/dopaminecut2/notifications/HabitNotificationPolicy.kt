package com.example.dopaminecut2.notifications

import com.example.dopaminecut2.data.local.GoalPlan
import com.example.dopaminecut2.domain.*
import com.example.dopaminecut2.statistics.InsightSnapshot
import java.time.LocalDate

data class HabitNotice(val id: String, val text: String, val destination: String,
    val onceKey: String? = null)

object HabitNotificationPolicy {
    private fun amount(snapshot: InsightSnapshot, date: String, metric: GoalMetric, apps: Set<SupportedPlatform>): Double? =
        snapshot.days.firstOrNull { it.date == date }?.let { day -> apps.sumOf { p ->
            HabitInsightsPolicy.value(day.appUsage[p.storageKey] ?: com.example.dopaminecut2.data.model.AppUsage(), metric)
        } }
    private fun validPlan(snapshot: InsightSnapshot, goal: ManagedGoal): GoalPlan? = snapshot.plans[goal.metric]
        ?.takeIf { it.revision == goal.revision }

    fun morning(snapshot: InsightSnapshot, goals: List<ManagedGoal>): List<HabitNotice> {
        val yesterday = LocalDate.parse(snapshot.today, HabitInsightsPolicy.format).minusDays(1).format(HabitInsightsPolicy.format)
        if (!snapshot.valid(yesterday)) return emptyList()
        val prior = snapshot.copy(days = snapshot.days.filter { it.date < yesterday }, today = yesterday)
        val apps = SupportedPlatform.entries.filter { it in ManagedGoal.SUPPORTED }.toSet()
        val notices = mutableListOf<HabitNotice>()
        val base = prior.baseline(GoalMetric.DAILY_TIME, apps)
        val value = amount(snapshot, yesterday, GoalMetric.DAILY_TIME, apps)
        if (base != null && value != null) {
            val difference = base.average - value
            if (difference >= 5) notices += HabitNotice("N07", "어제 숏폼은 ${value.toInt()}분. 최근 유효 ${base.dates.size}일 평균보다 ${difference.toInt()}분 적게 봤어요!", "change")
            if (difference <= -10) notices += HabitNotice("N08", "어제 숏폼은 최근 유효 ${base.dates.size}일 평균보다 ${(-difference).toInt()}분 많았어요. 오늘은 잠깐씩 쉬어볼까요?", "change")
        }
        val countBase = prior.baseline(GoalMetric.DAILY_COUNT, apps)
        val count = amount(snapshot, yesterday, GoalMetric.DAILY_COUNT, apps)
        if (countBase != null && count != null && countBase.average - count >= 5)
            notices += HabitNotice("N11", "어제 숏폼은 ${count.toInt()}회. 최근 평균보다 ${(countBase.average - count).toInt()}회 적게 봤어요.", "stats")
        val evaluated = goals.filter { g -> g.status == GoalStatus.ACTIVE && validPlan(snapshot, g)?.eligible(yesterday) == true }
        if (evaluated.isNotEmpty()) {
            val passed = evaluated.count { g -> amount(snapshot, yesterday, g.metric, g.platforms)?.let { it <= g.target } == true }
            notices += HabitNotice("N12", "어제 선택한 목표 ${evaluated.size}개 중 ${passed}개를 지켰어요. 변화를 확인해 보세요.", "change")
        }
        return notices.distinctBy { it.id }
    }

    fun milestones(snapshot: InsightSnapshot, goals: List<ManagedGoal>): List<HabitNotice> = goals.flatMap { goal ->
        if (goal.status != GoalStatus.ACTIVE) return@flatMap emptyList()
        val plan = validPlan(snapshot, goal) ?: return@flatMap emptyList()
        val dates = snapshot.days.filter { snapshot.valid(it.date) && plan.eligible(it.date) }.map { it.date }.sorted().takeLast(7)
        val values = dates.mapNotNull { amount(snapshot, it, goal.metric, goal.platforms) }
        val result = mutableListOf<HabitNotice>()
        val key = "${goal.metric}_${goal.revision}"
        val elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(plan.startedDate, HabitInsightsPolicy.format),
            LocalDate.parse(snapshot.today, HabitInsightsPolicy.format))
        val week = elapsedDays / 7
        if (goal.metric != GoalMetric.DAILY_COUNT && dates.takeLast(3).size == 3 &&
            HabitInsightsPolicy.consecutiveDates(dates.takeLast(3)) && values.takeLast(3).all { it <= goal.target }) {
            result += HabitNotice("N14", "최근 3일 동안 평가 가능한 ${if (goal.metric == GoalMetric.APP_TIME) "앱 시간" else "숏폼 시간"} 목표를 모두 지켰어요. 지금의 페이스를 이어가 보세요.", "change", "N14_$key")
        }
        if (dates.size == 7 && HabitInsightsPolicy.consecutiveDates(dates)) {
            val base = plan.baseline
            if (base != null && goal.metric != GoalMetric.DAILY_COUNT && base.average > values.average())
                result += HabitNotice("N13", "이번 7일은 하루 평균 ${(base.average - values.average()).toInt()}분 적게 사용했어요. 다음 목표도 정해볼까요?", "change", "N13_${key}_$week")
        }
        if (elapsedDays >= 7 && values.size >= 3 && values.count { it > goal.target } * 2 >= values.size)
            result += HabitNotice("N15", "최근 목표가 조금 빡빡했나요? 유지하거나 조금 조정할 수 있어요.", "goals", "N15_${key}_$week")
        result
    }.distinctBy { it.id }

    fun evening(snapshot: InsightSnapshot, activelyUsing: Boolean): HabitNotice? {
        val valid = snapshot.quality.filter { it.valid && it.date < snapshot.today }.map { it.date }.sortedDescending().take(7)
        val past = valid.mapNotNull { snapshot.time21[it] }
        val today = snapshot.time21[snapshot.today] ?: return null
        if (past.size < 3 || snapshot.quality.firstOrNull { it.date == snapshot.today }?.copy(complete = true)?.valid != true) return null
        val difference = today - past.average()
        if (kotlin.math.abs(difference) <= 600) return null
        return if (difference < 0) HabitNotice("N09", "오늘 오후 9시까지 숏폼 ${today / 60}분. 이전 유효 ${past.size}일 같은 시각 평균보다 ${(-difference / 60).toInt()}분 적어요.", "stats")
        else if (activelyUsing) HabitNotice("N10", "오늘 오후 9시까지 평소 같은 시각보다 ${(difference / 60).toInt()}분 많이 봤어요. 잠깐 쉴까요?", "stats") else null
    }

    fun pickMorningOrLunch(candidates: List<HabitNotice>, savedOrder: List<String>, handled: Set<String>): HabitNotice? =
        savedOrder.asSequence().filterNot { it in handled }.mapNotNull { id -> candidates.firstOrNull { it.id == id } }.firstOrNull()
}
