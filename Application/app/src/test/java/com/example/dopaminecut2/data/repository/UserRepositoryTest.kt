package com.example.dopaminecut2.data.repository

import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.model.User
import com.example.dopaminecut2.data.remote.RemoteUserDataSource
import com.example.dopaminecut2.statistics.UsageEvent
import com.example.dopaminecut2.statistics.ShortformSessionCheckpoint
import com.example.dopaminecut2.statistics.UsageClassification
import com.example.dopaminecut2.statistics.UsageSnapshot
import com.example.dopaminecut2.statistics.UsageSnapshotStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryTest {
    @Test
    fun `one month is read once and local value overrides cloud value`() = runBlocking {
        val remote = FakeRemote(
            months = mapOf(
                "202609" to mapOf(
                    "20260906" to DailyStatistics(
                        date = "20260906",
                        appUsage = mapOf("youtube" to AppUsage(runTimeSec = 10))
                    ),
                    "20260907" to DailyStatistics(
                        date = "20260907",
                        appUsage = mapOf("youtube" to AppUsage(runTimeSec = 20))
                    )
                )
            )
        )
        val store = InMemorySnapshotStore().apply {
            values["uid|20260907"] = UsageSnapshot(
                userId = "uid",
                date = "20260907",
                appUsage = mapOf("youtube" to AppUsage(runTimeSec = 35))
            )
        }
        val repository = UserRepository(remote, store)

        val result = repository.getStatisticsRange("uid", "20260906", "20260907").getOrThrow()

        assertEquals(1, remote.monthReads)
        assertEquals(listOf(10L, 35L), result.map { it.totalAppTimeSec })
    }

    private class FakeRemote(
        private val months: Map<String, Map<String, DailyStatistics>> = emptyMap()
    ) : RemoteUserDataSource {
        var monthReads = 0

        override suspend fun fetchUser(userId: String): User = User(userId = userId)

        override suspend fun fetchMonthlyStatistics(
            userId: String,
            monthId: String
        ): Map<String, DailyStatistics> {
            monthReads++
            return months[monthId].orEmpty()
        }

        override suspend fun writeUsageSnapshots(snapshots: List<UsageSnapshot>) = Unit
    }

    private class InMemorySnapshotStore : UsageSnapshotStore {
        val values = mutableMapOf<String, UsageSnapshot>()
        override suspend fun accumulate(event: UsageEvent): UsageSnapshot {
            val key = "${event.userId}|${event.date}"
            return (values[key] ?: UsageSnapshot.empty(event.userId, event.date))
                .accumulate(event)
                .also { values[key] = it }
        }
        override suspend fun get(userId: String, date: String) = values["$userId|$date"]
        override suspend fun checkpointShortform(checkpoint: ShortformSessionCheckpoint): UsageSnapshot {
            val key = "${checkpoint.userId}|${checkpoint.date}"
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
        override suspend fun getRange(userId: String, startDate: String, endDate: String) =
            values.values.filter { it.userId == userId && it.date in startDate..endDate }
        override suspend fun seedIfAbsent(snapshot: UsageSnapshot) {
            values.putIfAbsent("${snapshot.userId}|${snapshot.date}", snapshot)
        }
        override suspend fun pending(userId: String, limit: Int) =
            values.values.filter { it.userId == userId && it.dirty }.take(limit)
        override suspend fun markSynced(userId: String, date: String, revision: Long) = Unit
    }
}
