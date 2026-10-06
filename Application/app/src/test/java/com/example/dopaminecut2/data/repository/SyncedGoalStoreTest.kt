package com.example.dopaminecut2.data.repository

import com.example.dopaminecut2.auth.AuthRepository
import com.example.dopaminecut2.data.local.*
import com.example.dopaminecut2.data.remote.*
import com.example.dopaminecut2.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SyncedGoalStoreTest {
    private val goal = ManagedGoal(GoalMetric.DAILY_TIME, 30, setOf(SupportedPlatform.YOUTUBE), InterventionMode.CONFIRM)
    private class Auth : AuthRepository {
        var uid: String? = "owner"
        override fun currentUserId() = uid
        override suspend fun login(email: String, password: String) = Result.success(Unit)
        override suspend fun signup(email: String, password: String, nickname: String) = Result.success(Unit)
        override fun logout() = Result.success(Unit).also { uid = null }
    }
    private class Local : GoalSyncLocalStore {
        val goals = MutableStateFlow<List<ManagedGoal>>(emptyList())
        var acknowledged: String? = null
        var raw: String? = null
        var version = 0L
        override fun observeGoals(userId: String) = goals
        override suspend fun saveGoals(userId: String, goals: List<ManagedGoal>) {
            this.goals.value = GoalCollection.save(this.goals.value, goals)
            raw = GoalStorageCodec.encode(this.goals.value)
        }
        override suspend fun setGoalStatus(userId: String, metric: GoalMetric, revision: Long, status: GoalStatus) {
            saveGoals(userId, listOf(goals.value.single().copy(status = status)))
        }
        override suspend fun syncSnapshot(userId: String) = GoalSyncSnapshot(goals.value, version, raw, raw != acknowledged)
        override suspend fun acknowledgeGoals(userId: String, captured: GoalSyncSnapshot, cloudVersion: Long) {
            acknowledged = captured.encoded; version = cloudVersion
        }
        override suspend fun importGoals(userId: String, captured: GoalSyncSnapshot, goals: List<ManagedGoal>, cloudVersion: Long): Boolean {
            if (raw != captured.encoded) return false
            this.goals.value = GoalCollection.importRemote(captured.goals, goals); raw = GoalStorageCodec.encode(this.goals.value); acknowledged = raw; version = cloudVersion
            return true
        }
    }
    private class Remote : RemoteGoalSource {
        var cloud: CloudGoals? = null
        var offline = false
        var writes = 0
        var onSave: (suspend () -> Unit)? = null
        override suspend fun fetch(userId: String): CloudGoals? { check(!offline); return cloud }
        override suspend fun save(userId: String, expectedVersion: Long, goals: List<ManagedGoal>): CloudGoals {
            check(!offline)
            if (cloud?.goals == goals) return requireNotNull(cloud)
            if ((cloud?.version ?: 0L) != expectedVersion) throw GoalConflictException()
            writes++
            val result = CloudGoals(expectedVersion + 1, goals).also { cloud = it }
            onSave?.invoke()
            return result
        }
    }
    private fun repository(local: Local, remote: Remote, auth: Auth = Auth()) =
        SyncedGoalStore(local, remote, auth, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), false)

    @Test fun `offline edits persist and retry succeeds`() = runBlocking {
        val local = Local(); val remote = Remote().apply { offline = true }; val repo = repository(local, remote)
        local.saveGoals("owner", listOf(goal))
        assertTrue(repo.sync("owner").isFailure)
        assertEquals(30, local.goals.value.single().target)
        assertTrue(local.syncSnapshot("owner").dirty)
        remote.offline = false
        assertTrue(repo.sync("owner").isSuccess)
        assertFalse(local.syncSnapshot("owner").dirty)
    }
    @Test fun `new device restores remote paused goals`() = runBlocking {
        val local = Local(); val remote = Remote().apply { cloud = CloudGoals(5, listOf(goal.copy(status = GoalStatus.PAUSED, revision = 7))) }
        repository(local, remote).sync("owner").getOrThrow()
        assertEquals(remote.cloud!!.goals, local.goals.value)
        assertEquals(5L, local.version)
        assertEquals(0, remote.writes)
    }
    @Test fun `conflict preserves both sides until user chooses`() = runBlocking {
        val local = Local(); val remote = Remote().apply { cloud = CloudGoals(2, listOf(goal.copy(target = 10, revision = 2))) }
        local.saveGoals("owner", listOf(goal))
        val repo = repository(local, remote)
        assertTrue(repo.sync("owner").isFailure)
        assertTrue(repo.observeSync("owner").first().conflict)
        assertEquals(30, local.goals.value.single().target)
        assertEquals(10, remote.cloud!!.goals.single().target)
        repo.resolveConflict("owner", useLocal = false).getOrThrow()
        assertEquals(10, local.goals.value.single().target)
    }
    @Test fun `local conflict choice is an explicit versioned overwrite`() = runBlocking {
        val local = Local(); val remote = Remote().apply { cloud = CloudGoals(2, listOf(goal.copy(target = 10, revision = 2))) }
        local.saveGoals("owner", listOf(goal))
        repository(local, remote).resolveConflict("owner", true).getOrThrow()
        assertEquals(3L, remote.cloud!!.version)
        assertEquals(30, remote.cloud!!.goals.single().target)
    }
    @Test fun `edit during upload is still pending after old upload ack`() = runBlocking {
        val local = Local(); val remote = Remote()
        local.saveGoals("owner", listOf(goal))
        remote.onSave = { local.saveGoals("owner", listOf(local.goals.value.single().copy(target = 20))) }
        val repo = repository(local, remote)
        repo.sync("owner").getOrThrow()
        assertTrue(local.syncSnapshot("owner").dirty)
        assertEquals(1L, local.version)
        remote.onSave = null
        repo.sync("owner").getOrThrow()
        assertFalse(local.syncSnapshot("owner").dirty)
        assertEquals(20, remote.cloud!!.goals.single().target)
    }
    @Test fun `account change blocks sync to another owner`() = runBlocking {
        val local = Local(); val remote = Remote(); val auth = Auth().apply { uid = "other" }
        local.saveGoals("owner", listOf(goal))
        assertTrue(repository(local, remote, auth).sync("owner").isFailure)
        assertNull(remote.cloud)
    }
    @Test fun `unchanged settings are never uploaded repeatedly`() = runBlocking {
        val local = Local(); val remote = Remote(); local.saveGoals("owner", listOf(goal))
        val repo = repository(local, remote)
        repo.sync("owner").getOrThrow(); repo.sync("owner").getOrThrow()
        assertEquals(1, remote.writes)
    }
}
