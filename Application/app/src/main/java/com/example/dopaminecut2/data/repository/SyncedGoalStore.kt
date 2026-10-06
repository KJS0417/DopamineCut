package com.example.dopaminecut2.data.repository

import com.example.dopaminecut2.auth.AuthRepository
import com.example.dopaminecut2.data.local.*
import com.example.dopaminecut2.data.remote.*
import com.example.dopaminecut2.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local writes succeed offline. Persistent dirty snapshots are retried while the app/service runs. */
class SyncedGoalStore(
    private val local: GoalSyncLocalStore,
    private val remote: RemoteGoalSource,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
    startMonitoring: Boolean = true
) : GoalStore {
    private val mutex = Mutex()
    private val statuses = MutableStateFlow<Map<String, GoalSyncStatus>>(emptyMap())
    init {
        if (startMonitoring) scope.launch {
            auth.authState().distinctUntilChanged().collectLatest { uid ->
                if (uid != null) while (currentCoroutineContext().isActive) {
                    sync(uid)
                    do {
                        delay(5 * 60_000L)
                    } while (!local.syncSnapshot(uid).dirty && auth.currentUserId() == uid)
                }
            }
        }
    }
    override fun observeGoals(userId: String) = local.observeGoals(userId)
    override fun observeSync(userId: String) = statuses.map { it[userId] ?: GoalSyncStatus() }.distinctUntilChanged()
    override suspend fun saveGoals(userId: String, goals: List<ManagedGoal>) {
        checkOwner(userId)
        local.saveGoals(userId, goals)
        publish(userId, GoalSyncStatus("기기에 저장됨 · 동기화 대기"))
        scope.launch { sync(userId) }
    }
    override suspend fun setGoalStatus(userId: String, metric: GoalMetric, revision: Long, status: GoalStatus) {
        checkOwner(userId)
        local.setGoalStatus(userId, metric, revision, status)
        publish(userId, GoalSyncStatus("기기에 저장됨 · 동기화 대기"))
        scope.launch { sync(userId) }
    }
    override suspend fun sync(userId: String): Result<Unit> = guarded(userId) {
        val snapshot = local.syncSnapshot(userId)
        if (snapshot.dirty) {
            val saved = remote.save(userId, snapshot.cloudVersion, snapshot.goals)
            checkOwner(userId)
            local.acknowledgeGoals(userId, snapshot, saved.version)
            publish(userId, GoalSyncStatus(if (local.syncSnapshot(userId).dirty) "새 변경 동기화 대기" else "Firebase 동기화 완료"))
        } else {
            val cloud = remote.fetch(userId)
            checkOwner(userId)
            if (cloud != null) {
                val imported = local.importGoals(userId, snapshot, cloud.goals, cloud.version)
                publish(userId, GoalSyncStatus(if (imported) "Firebase 동기화 완료" else "새 변경 동기화 대기"))
            } else publish(userId, GoalSyncStatus("저장된 서버 목표 없음"))
        }
    }
    override suspend fun resolveConflict(userId: String, useLocal: Boolean): Result<Unit> = guarded(userId) {
        val snapshot = local.syncSnapshot(userId)
        val cloud = remote.fetch(userId) ?: CloudGoals(0, emptyList())
        checkOwner(userId)
        if (useLocal) {
            val saved = remote.save(userId, cloud.version, snapshot.goals)
            checkOwner(userId)
            local.acknowledgeGoals(userId, snapshot, saved.version)
        } else {
            require(local.importGoals(userId, snapshot, cloud.goals, cloud.version)) { "설정이 다시 변경됐습니다. 재시도해 주세요." }
        }
        publish(userId, GoalSyncStatus("선택한 설정으로 동기화 완료"))
    }
    private suspend fun guarded(uid: String, operation: suspend () -> Unit): Result<Unit> = mutex.withLock {
        try {
            checkOwner(uid)
            check(withTimeoutOrNull(30_000L) { operation(); true } == true) { "동기화 응답이 지연됐습니다. 기기 변경을 유지하고 재시도합니다." }
            Result.success(Unit)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            if (auth.currentUserId() == uid) publish(uid, GoalSyncStatus(
                "기기 설정 유지 · 동기화 실패: ${error.message ?: "네트워크를 확인해 주세요."}",
                error is GoalConflictException || generateSequence(error.cause) { it.cause }.any { it is GoalConflictException }))
            Result.failure(error)
        }
    }
    private fun checkOwner(uid: String) { require(uid.isNotBlank() && auth.currentUserId() == uid) { "로그인 계정이 변경됐습니다." } }
    private fun publish(uid: String, status: GoalSyncStatus) { statuses.value = statuses.value + (uid to status) }
}
