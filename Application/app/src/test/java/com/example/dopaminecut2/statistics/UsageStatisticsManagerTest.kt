package com.example.dopaminecut2.statistics

import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.model.User
import com.example.dopaminecut2.data.remote.RemoteUserDataSource
import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.domain.ContentCategory
import com.example.dopaminecut2.time.WallClock
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatisticsManagerTest {
    @Test
    fun `events are merged locally without a firebase write`() = runBlocking {
        val remote = FakeRemote()
        val store = InMemoryStore()
        val manager = manager(remote, store)

        val first = manager.recordRunTime("uid", "20260812", SupportedPlatform.YOUTUBE, 10)
        val second = manager.recordRunTime("uid", "20260812", SupportedPlatform.YOUTUBE, 5)

        assertEquals(RecordStatus.BUFFERED, first.getOrThrow())
        assertEquals(RecordStatus.BUFFERED, second.getOrThrow())
        assertEquals(0, remote.writeCalls)
        assertEquals(15L, store.values.getValue("uid|20260812").toDailyStatistics().totalAppTimeSec)
    }

    @Test
    fun `retry sends one daily snapshot and marks its revision synced`() = runBlocking {
        val remote = FakeRemote()
        val store = InMemoryStore()
        val manager = manager(remote, store)
        manager.recordRunTime("uid", "20260812", SupportedPlatform.YOUTUBE, 10)
        manager.recordRunTime("uid", "20260812", SupportedPlatform.YOUTUBE, 5)

        val summary = manager.retryPending("uid")

        assertEquals(1, remote.writeCalls)
        assertEquals(1, remote.lastWrite.size)
        assertEquals(1, summary.synced)
        assertEquals(0, summary.remaining)
        assertEquals(false, store.values.getValue("uid|20260812").dirty)
    }

    @Test
    fun `failed sync keeps local snapshot for retry`() = runBlocking {
        val remote = FakeRemote(failWrites = true)
        val store = InMemoryStore()
        val manager = manager(remote, store)
        manager.recordRunTime("uid", "20260812", SupportedPlatform.YOUTUBE, 10)

        val summary = manager.retryPending("uid")

        assertEquals(0, summary.synced)
        assertEquals(1, summary.remaining)
        assertTrue(store.values.getValue("uid|20260812").dirty)
    }

    @Test
    fun `retry summary reports every snapshot still waiting`() = runBlocking {
        val remote = FakeRemote()
        val store = InMemoryStore()
        val manager = manager(remote, store)
        manager.recordRunTime("uid", "20260810", SupportedPlatform.YOUTUBE, 1)
        manager.recordRunTime("uid", "20260811", SupportedPlatform.YOUTUBE, 1)
        manager.recordRunTime("uid", "20260812", SupportedPlatform.YOUTUBE, 1)

        val summary = manager.retryPending("uid", limit = 1)

        assertEquals(1, summary.attempted)
        assertEquals(1, summary.synced)
        assertEquals(2, summary.remaining)
    }

    @Test
    fun `retry only writes snapshots owned by active account`() = runBlocking {
        val remote = FakeRemote()
        val store = InMemoryStore()
        val manager = manager(remote, store)
        manager.recordRunTime("a", "20260812", SupportedPlatform.YOUTUBE, 1)
        manager.recordRunTime("b", "20260812", SupportedPlatform.YOUTUBE, 1)

        manager.retryPending("b")

        assertEquals(listOf("b"), remote.lastWrite.map(UsageSnapshot::userId))
        assertTrue(store.values.getValue("a|20260812").dirty)
    }

    @Test
    fun `live session stores time only even when caller supplies obsolete count`() = runBlocking {
        val store = InMemoryStore()
        val manager = manager(FakeRemote(), store)

        manager.recordShortformSession(
            userId = "uid",
            date = "20260812",
            platform = SupportedPlatform.YOUTUBE,
            durationSec = 89L,
            count = 2L,
            category = ContentCategory.LIVE,
            deductedScore = 2L
        ).getOrThrow()

        val statistics = store.values.getValue("uid|20260812").toDailyStatistics()
        assertEquals(0L, statistics.totalShortformCount)
        assertEquals(89L, statistics.appUsage.getValue("youtube").shortformTimeSec)
        assertEquals(0L, statistics.categoryUsage.getValue("LIVE").count)
        assertEquals(0L, statistics.deductedScore)
    }

    private fun manager(remote: FakeRemote, store: InMemoryStore) = UsageStatisticsManager(
        remoteDataSource = remote,
        snapshotStore = store,
        wallClock = WallClock { Date(1_234L) }
    )

    @Test
    fun `new manager after process restart still sees usage and finalizes pending classification`() = runBlocking {
        val store = InMemoryStore()
        val remote = FakeRemote()
        manager(remote, store).checkpointShortformSession(
            "uid", "20260930", "view-1", SupportedPlatform.YOUTUBE, 12L, 1L, 1_234L
        ).getOrThrow()
        val restarted = manager(remote, store)

        restarted.finalizePendingClassifications("uid", "PROCESS_RESTART").getOrThrow()
        restarted.retryPending("uid")

        assertEquals(1L, remote.lastWrite.single().toDailyStatistics().totalShortformCount)
        assertEquals(12L, remote.lastWrite.single().toDailyStatistics().totalShortformTimeSec)
        assertEquals("PROCESS_RESTART", store.values.getValue("uid|20260930")
            .shortformSessions.getValue("view-1").classification?.reason)
    }

    @Test
    fun `classification and checkpoints remain local until explicit snapshot sync`() = runBlocking {
        val store = InMemoryStore()
        val remote = FakeRemote()
        val manager = manager(remote, store)
        manager.checkpointShortformSession(
            "uid", "20260930", "view-1", SupportedPlatform.YOUTUBE, 5L, 1L, 1_234L
        ).getOrThrow()
        val classification = UsageClassification(ContentCategory.GAME, "CLASSIFIED", "JEV", deductedScore = 1L)
        manager.resolveShortformCategory("uid", "20260930", "view-1", classification).getOrThrow()
        manager.checkpointShortformSession(
            "uid", "20260930", "view-1", SupportedPlatform.YOUTUBE, 15L, 1L, 2_234L
        ).getOrThrow()
        manager.resolveShortformCategory("uid", "20260930", "view-1", classification).getOrThrow()

        assertEquals(0, remote.writeCalls)
        val snapshot = store.values.getValue("uid|20260930")
        assertEquals(15L, snapshot.categoryUsage.getValue("GAME").durationSec)
        assertEquals(1L, snapshot.deductedScore)
        assertEquals(1L, snapshot.toDailyStatistics().totalShortformCount)
    }

    @Test
    fun `cross account or date session reuse returns failure without modifying aggregates`() = runBlocking {
        val store = InMemoryStore()
        val manager = manager(FakeRemote(), store)
        manager.checkpointShortformSession(
            "a", "20260930", "view-1", SupportedPlatform.YOUTUBE, 5L, 1L, 1_234L
        ).getOrThrow()

        assertTrue(manager.checkpointShortformSession(
            "b", "20260930", "view-1", SupportedPlatform.YOUTUBE, 5L, 1L, 1_234L
        ).isFailure)
        assertTrue(manager.checkpointShortformSession(
            "a", "20261001", "view-1", SupportedPlatform.YOUTUBE, 5L, 1L, 1_234L
        ).isFailure)
        assertEquals(1, store.values.size)
    }

    @Test
    fun `classification before checkpoint is an explicit failure`() = runBlocking {
        val manager = manager(FakeRemote(), InMemoryStore())
        assertTrue(manager.resolveShortformCategory(
            "uid", "20260930", "missing", UsageClassification(ContentCategory.GAME, "CLASSIFIED", "JEV")
        ).isFailure)
    }

    private class InMemoryStore : UsageSnapshotStore {
        val values = mutableMapOf<String, UsageSnapshot>()
        override suspend fun accumulate(event: UsageEvent): UsageSnapshot {
            val key = "${event.userId}|${event.date}"
            return (values[key] ?: UsageSnapshot.empty(event.userId, event.date))
                .accumulate(event)
                .also { values[key] = it }
        }
        override suspend fun checkpointShortform(checkpoint: ShortformSessionCheckpoint): UsageSnapshot {
            val key = "${checkpoint.userId}|${checkpoint.date}"
            require(values.values.none {
                checkpoint.viewSessionId in it.shortformSessions &&
                    (it.userId != checkpoint.userId || it.date != checkpoint.date)
            })
            return (values[key] ?: UsageSnapshot.empty(checkpoint.userId, checkpoint.date))
                .checkpointShortform(checkpoint).also { values[key] = it }
        }
        override suspend fun resolveShortformCategory(
            userId: String,
            date: String,
            viewSessionId: String,
            classification: UsageClassification,
            resolvedAtEpochMs: Long
        ): UsageSnapshot {
            val key = "$userId|$date"
            return values.getValue(key).resolveShortformCategory(viewSessionId, classification, resolvedAtEpochMs)
                .also { values[key] = it }
        }
        override suspend fun finalizePendingClassifications(userId: String, reason: String) {
            values.replaceAll { _, snapshot ->
                if (snapshot.userId == userId) snapshot.finalizePendingClassifications(reason) else snapshot
            }
        }
        override suspend fun get(userId: String, date: String) = values["$userId|$date"]
        override suspend fun getRange(userId: String, startDate: String, endDate: String) =
            values.values.filter { it.userId == userId && it.date in startDate..endDate }
        override suspend fun seedIfAbsent(snapshot: UsageSnapshot) {
            values.putIfAbsent("${snapshot.userId}|${snapshot.date}", snapshot)
        }
        override suspend fun pending(userId: String, limit: Int) =
            values.values.filter { it.userId == userId && it.dirty }.take(limit)
        override suspend fun markSynced(userId: String, date: String, revision: Long) {
            val key = "$userId|$date"
            values[key]?.takeIf { it.revision == revision }?.let { values[key] = it.copy(dirty = false) }
        }
    }

    private class FakeRemote(private val failWrites: Boolean = false) : RemoteUserDataSource {
        var writeCalls = 0
        var lastWrite: List<UsageSnapshot> = emptyList()
        override suspend fun writeUsageSnapshots(snapshots: List<UsageSnapshot>) {
            writeCalls++
            if (failWrites) error("offline")
            lastWrite = snapshots
        }
        override suspend fun fetchUser(userId: String): User = error("unused")
        override suspend fun fetchMonthlyStatistics(
            userId: String,
            monthId: String
        ): Map<String, DailyStatistics> = emptyMap()
    }
}
