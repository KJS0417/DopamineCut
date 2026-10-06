package com.example.dopaminecut2.domain

enum class GoalMetric { DAILY_TIME, DAILY_COUNT, APP_TIME }
enum class GoalStatus { ACTIVE, PAUSED }

data class ManagedGoal(
    val metric: GoalMetric,
    val target: Int,
    val platforms: Set<SupportedPlatform>,
    val intervention: InterventionMode,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val revision: Long = 0
) {
    fun validate() {
        require(target in 0..if (metric == GoalMetric.DAILY_COUNT) 999 else 1440) { "목표는 영상 수 0~999개, 시간 0~1440분으로 입력해 주세요." }
        require(platforms.isNotEmpty() && platforms.all { it in SUPPORTED }) { "적용할 앱을 선택해 주세요." }
        require(revision in 0 until Long.MAX_VALUE) { "목표 버전이 올바르지 않습니다." }
    }

    companion object {
        val SUPPORTED = setOf(SupportedPlatform.YOUTUBE, SupportedPlatform.INSTAGRAM, SupportedPlatform.KAKAOTALK)
    }
}

/** One goal per metric. A batch validates completely before changing any existing goal. */
object GoalCollection {
    /** Keep a local revision monotonic even when two devices produced the same numeric revision. */
    fun importRemote(current: List<ManagedGoal>, remote: List<ManagedGoal>): List<ManagedGoal> = remote.map { goal ->
        val old = current.firstOrNull { it.metric == goal.metric }
        when {
            old == null -> goal
            old.copy(revision = 0) == goal.copy(revision = 0) -> old
            else -> goal.copy(revision = maxOf(old.revision, goal.revision) + 1)
        }.also { it.validate() }
    }
    fun save(current: List<ManagedGoal>, drafts: List<ManagedGoal>): List<ManagedGoal> {
        require(drafts.isNotEmpty() && drafts.map { it.metric }.distinct().size == drafts.size)
        drafts.forEach { draft ->
            draft.validate()
            val existing = current.firstOrNull { it.metric == draft.metric }
            require(draft.revision == (existing?.revision ?: 0L)) { "목표가 다른 화면에서 변경됐습니다. 다시 불러와 주세요." }
        }
        val replacements = drafts.associate { it.metric to it.copy(revision = it.revision + 1) }
        return GoalMetric.entries.mapNotNull { metric -> replacements[metric] ?: current.firstOrNull { it.metric == metric } }
    }

    fun setStatus(current: List<ManagedGoal>, metric: GoalMetric, revision: Long, status: GoalStatus): List<ManagedGoal> {
        val goal = requireNotNull(current.firstOrNull { it.metric == metric }) { "저장된 목표가 없습니다." }
        require(goal.revision == revision) { "목표가 변경됐습니다. 다시 확인해 주세요." }
        return save(current, listOf(goal.copy(status = status)))
    }
}
