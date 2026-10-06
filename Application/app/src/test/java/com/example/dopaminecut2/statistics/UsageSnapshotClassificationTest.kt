package com.example.dopaminecut2.statistics

import com.example.dopaminecut2.domain.ContentCategory
import com.example.dopaminecut2.domain.SupportedPlatform
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSnapshotClassificationTest {
    @Test
    fun `first checkpoint persists unknown usage before any classifier response`() {
        val snapshot = UsageSnapshot.empty("uid", "20260930").checkpointShortform(checkpoint())

        assertEquals(1L, snapshot.toDailyStatistics().totalShortformCount)
        assertEquals(5L, snapshot.toDailyStatistics().totalShortformTimeSec)
        assertEquals(0L, snapshot.toDailyStatistics().totalAppTimeSec)
        assertEquals(1L, snapshot.categoryUsage.getValue("UNKNOWN").count)
        assertTrue(snapshot.shortformSessions.getValue("session-1").isPending)
        assertTrue(snapshot.dirty)
    }

    @Test
    fun `duplicate and out of order cumulative checkpoints never repeat usage`() {
        val initial = UsageSnapshot.empty("uid", "20260930").checkpointShortform(checkpoint(duration = 15L))
        val duplicate = initial.checkpointShortform(checkpoint(duration = 15L))
        val stale = duplicate.checkpointShortform(checkpoint(duration = 5L))

        assertEquals(initial, duplicate)
        assertEquals(initial, stale)
        assertEquals(1L, stale.toDailyStatistics().totalShortformCount)
        assertEquals(15L, stale.toDailyStatistics().totalShortformTimeSec)
    }

    @Test
    fun `classification moves prior usage without repeating counts or original hour`() {
        val occurredAt = atHour(9)
        val pending = UsageSnapshot.empty("uid", "20260930")
            .checkpointShortform(checkpoint(duration = 20L, occurredAt = occurredAt))
        val resolved = pending.resolveShortformCategory("session-1", classified(), atHour(10))

        assertEquals(pending.appUsage, resolved.appUsage)
        assertEquals(mapOf("09" to 1L), resolved.hourlyShortformCount)
        assertEquals(0L, resolved.categoryUsage.getValue("UNKNOWN").durationSec)
        assertEquals(1L, resolved.categoryUsage.getValue("SPORTS").count)
        assertEquals(20L, resolved.categoryUsage.getValue("SPORTS").durationSec)
        assertEquals(2L, resolved.deductedScore)
    }

    @Test
    fun `checkpoint after classification adds only new duration to final category`() {
        val pending = UsageSnapshot.empty("uid", "20260930").checkpointShortform(checkpoint())
        val resolved = pending.resolveShortformCategory("session-1", classified(), 2_000L)
        val continued = resolved.checkpointShortform(checkpoint(duration = 15L, occurredAt = 3_000L))

        assertEquals(15L, continued.toDailyStatistics().totalShortformTimeSec)
        assertEquals(15L, continued.categoryUsage.getValue("SPORTS").durationSec)
        assertEquals(1L, continued.toDailyStatistics().totalShortformCount)
        assertEquals(2L, continued.deductedScore)
        assertEquals(1_000L, continued.shortformSessions.getValue("session-1").occurredAtEpochMs)
    }

    @Test
    fun `duplicate and conflicting late classifications do not change terminal result`() {
        val resolved = UsageSnapshot.empty("uid", "20260930")
            .checkpointShortform(checkpoint())
            .resolveShortformCategory("session-1", classified(), 2_000L)

        assertEquals(resolved, resolved.resolveShortformCategory("session-1", classified(), 3_000L))
        assertEquals(resolved, resolved.resolveShortformCategory(
            "session-1", classified().copy(category = ContentCategory.GAME), 3_000L
        ))
    }

    @Test
    fun `quota exceeded remains explicitly unknown and incurs no penalty`() {
        val pending = UsageSnapshot.empty("uid", "20260930").checkpointShortform(checkpoint())
        val unknown = pending.resolveShortformCategory("session-1", UsageClassification(
            ContentCategory.UNKNOWN, "UNRESOLVED", "SERVER", "QUOTA_EXCEEDED"
        ), 2_000L)

        assertEquals(pending.categoryUsage, unknown.categoryUsage)
        assertEquals(pending.revision, unknown.revision)
        assertFalse(unknown.shortformSessions.getValue("session-1").isPending)
        assertEquals("QUOTA_EXCEEDED", unknown.shortformSessions.getValue("session-1").classification?.reason)
        assertEquals(0L, unknown.deductedScore)
        assertEquals(1L, unknown.toDailyStatistics().totalShortformCount)
    }

    @Test
    fun `restart finalizes lost OCR while preserving stored usage and valid results`() {
        val before = UsageSnapshot.empty("uid", "20260930")
            .checkpointShortform(checkpoint())
            .checkpointShortform(checkpoint(id = "session-2", duration = 8L))
            .resolveShortformCategory("session-2", classified(), 2_000L)
            .copy(dirty = false)
        val after = before.finalizePendingClassifications("PROCESS_RESTART")

        assertEquals(before.toDailyStatistics(), after.toDailyStatistics())
        assertEquals(before.revision, after.revision)
        assertFalse(after.dirty)
        assertTrue(after.shortformSessions.values.none { it.isPending })
        assertEquals(ContentCategory.SPORTS, after.shortformSessions.getValue("session-2").category)
        assertEquals("PROCESS_RESTART", after.shortformSessions.getValue("session-1").classification?.reason)
        assertEquals(after, after.resolveShortformCategory("session-1", classified(), 3_000L))
    }

    @Test
    fun `live checkpoints ignore obsolete counts and accumulate time only`() {
        val initial = UsageSnapshot.empty("uid", "20260930").checkpointShortform(checkpoint(
            count = 0L, category = ContentCategory.LIVE, occurredAt = atHour(9)
        ))
        val forty = initial.checkpointShortform(checkpoint(
            duration = 40L, count = 1L, category = ContentCategory.LIVE, occurredAt = atHour(9)
        ))
        val eighty = forty.checkpointShortform(checkpoint(
            duration = 80L, count = 2L, category = ContentCategory.LIVE, occurredAt = atHour(10)
        ))
        val duplicate = eighty.checkpointShortform(checkpoint(
            duration = 80L, count = 2L, category = ContentCategory.LIVE, occurredAt = atHour(10)
        ))

        assertEquals(0L, initial.toDailyStatistics().totalShortformCount)
        assertEquals(5L, initial.toDailyStatistics().totalShortformTimeSec)
        assertFalse(initial.shortformSessions.getValue("session-1").isPending)
        assertEquals(0L, eighty.deductedScore)
        assertEquals(0L, eighty.categoryUsage.getValue("LIVE").count)
        assertEquals(0L, eighty.toDailyStatistics().totalShortformCount)
        assertEquals(80L, eighty.categoryUsage.getValue("LIVE").durationSec)
        assertEquals(emptyMap<String, Long>(), eighty.hourlyShortformCount)
        assertEquals(eighty, duplicate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `result without saved session is rejected`() {
        UsageSnapshot.empty("uid", "20260930").resolveShortformCategory("missing", classified(), 2_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkpoint with mismatched account is rejected`() {
        UsageSnapshot.empty("other", "20260930").checkpointShortform(checkpoint())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkpoint with mismatched date is rejected`() {
        UsageSnapshot.empty("uid", "20261001").checkpointShortform(checkpoint())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `same session cannot change platform`() {
        UsageSnapshot.empty("uid", "20260930").checkpointShortform(checkpoint())
            .checkpointShortform(checkpoint().copy(platform = SupportedPlatform.INSTAGRAM))
    }

    private fun checkpoint(
        id: String = "session-1",
        duration: Long = 5L,
        count: Long = 1L,
        category: ContentCategory = ContentCategory.UNKNOWN,
        occurredAt: Long = 1_000L
    ) = ShortformSessionCheckpoint(
        "uid", "20260930", id, SupportedPlatform.YOUTUBE, duration, count, occurredAt, category
    )

    private fun classified() = UsageClassification(
        ContentCategory.SPORTS, "CLASSIFIED", "JEV", confidence = 0.94, deductedScore = 2L
    )

    private fun atHour(hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.SEPTEMBER, 30, hour, 0, 0)
    }.timeInMillis
}
