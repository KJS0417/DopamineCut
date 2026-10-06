package com.example.dopaminecut2.data.local

import com.example.dopaminecut2.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GoalStore {
    fun observeGoals(userId: String): Flow<List<ManagedGoal>>
    suspend fun saveGoals(userId: String, goals: List<ManagedGoal>)
    suspend fun setGoalStatus(userId: String, metric: GoalMetric, revision: Long, status: GoalStatus)
    fun observeSync(userId: String): Flow<GoalSyncStatus> = flowOf(GoalSyncStatus())
    suspend fun sync(userId: String): Result<Unit> = Result.success(Unit)
    suspend fun resolveConflict(userId: String, useLocal: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException())
}

data class GoalSyncStatus(val message: String = "동기화 준비 중", val conflict: Boolean = false)
data class GoalSyncSnapshot(val goals: List<ManagedGoal>, val cloudVersion: Long, val encoded: String?, val dirty: Boolean)
interface GoalSyncLocalStore : GoalStore {
    suspend fun syncSnapshot(userId: String): GoalSyncSnapshot
    suspend fun acknowledgeGoals(userId: String, captured: GoalSyncSnapshot, cloudVersion: Long)
    suspend fun importGoals(userId: String, captured: GoalSyncSnapshot, goals: List<ManagedGoal>, cloudVersion: Long): Boolean
}

/** Versioned encoding contains only enum IDs and integers; no user text or delimiter escaping. */
object GoalStorageCodec {
    fun encode(goals: List<ManagedGoal>): String = "v1\n" + goals.joinToString("\n") {
        it.validate()
        listOf(it.metric.name, it.target, it.platforms.sortedBy { p -> p.name }.joinToString(",") { p -> p.name },
            it.intervention.name, it.status.name, it.revision).joinToString("|")
    }

    fun decode(raw: String?): List<ManagedGoal> {
        if (raw == null) return emptyList()
        val lines = raw.lines()
        require(lines.first() == "v1") { "목표 저장 형식을 읽을 수 없습니다." }
        val goals = lines.drop(1).filter { it.isNotEmpty() }.map { line ->
            val fields = line.split('|')
            require(fields.size == 6) { "목표 데이터가 손상됐습니다." }
            ManagedGoal(GoalMetric.valueOf(fields[0]), fields[1].toInt(),
                fields[2].split(',').map { SupportedPlatform.valueOf(it) }.toSet(),
                InterventionMode.valueOf(fields[3]), GoalStatus.valueOf(fields[4]), fields[5].toLong()).also { it.validate() }
        }
        require(goals.map { it.metric }.distinct().size == goals.size)
        return goals
    }
}
