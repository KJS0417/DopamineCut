package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.logic.ManagedGoalProgress
import com.example.dopaminecut2.data.model.AppUsage
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class PhotoPostIntegrationTest {
    @Test fun `photo tracker checkpoints time with zero count and stops after exit`() {
        var now = 0L
        val checkpoints = mutableListOf<ShortformSessionCheckpoint>()
        val counts = mutableListOf<ShortformCountDelta>()
        val completed = mutableListOf<CompletedShortformSession>()
        val tracker = ShortformSessionTracker(clockMs={now}, onCheckpoint=checkpoints::add,
            onCountDelta=counts::add, onSessionCompleted=completed::add)
        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, "photo", ShortformContentKind.PHOTO_POST)
        now = 40_000
        tracker.tick()
        tracker.onScreenChanged(false,null,null,ShortformContentKind.UNKNOWN)
        now = 60_000
        tracker.tick()
        assertTrue(counts.isEmpty())
        assertEquals(40L,checkpoints.last().durationSec)
        assertEquals(0L,checkpoints.last().count)
        assertEquals(40L,completed.single().durationSec)
    }

    @Test fun `fourth model output maps to photo and ambiguous stays unknown`() {
        val policy = ShortformContentDecisionPolicy()
        assertEquals(ShortformContentKind.PHOTO_POST, policy.fromLogits(floatArrayOf(0f, 0f, 0f, 5f)).kind)
        assertEquals(ShortformContentKind.UNKNOWN, policy.fromLogits(floatArrayOf(0f, 0f, 0f, 1f)).kind)
    }

    @Test fun `photo accumulates time after qualification but never count`() {
        val policy = ShortformCountingPolicy()
        assertEquals(ShortformCountResult(0, 0), policy.result(ShortformContentKind.PHOTO_POST, 4))
        for (duration in listOf(5L, 40L, 120L)) {
            assertEquals(ShortformCountResult(0, duration), policy.result(ShortformContentKind.PHOTO_POST, duration))
        }
        assertEquals(0L, policy.newCountSince(ShortformContentKind.PHOTO_POST, 0, 120))
    }

    @Test fun `time only session changes key on live photo transition`() {
        var ids = 0
        val session = YoutubeLiveSession { (++ids).toString() }
        val live = session.confirm()
        val photo = session.confirm(ShortformContentKind.PHOTO_POST)
        assertNotEquals(live, photo)
        assertEquals(photo, session.confirm(ShortformContentKind.PHOTO_POST))
        session.pause()
        assertEquals(photo, session.confirm(ShortformContentKind.PHOTO_POST))
        assertNotEquals(photo, session.confirm())
    }

    @Test fun `photo current goal progress adds time only`() {
        val active = ShortformSessionCheckpoint("photo", SupportedPlatform.YOUTUBE, "key", ShortformContentKind.PHOTO_POST, 80, 0)
        val usage = ManagedGoalProgress.effectiveUsage(mapOf("youtube" to AppUsage(shortformCount=3)),
            SupportedPlatform.YOUTUBE, 90, active, null).getValue("youtube")
        assertEquals(80L, usage.shortformTimeSec)
        assertEquals(3L, usage.shortformCount)
    }

    @Test fun `whole UI pixels match Pillow references across aspect ratios`() {
        val cases = listOf(Triple(13,31,"ec5726a9cea1caed74c1b6d5050b0d8b205cd174d801afd3a544a93c6a9afaea"),
            Triple(37,41,"1aee074d77f6d6491c2c102994022521e4082c0986fb905384cdfb10e5c7d7e0"),
            Triple(27,63,"7672733cb4ed5357dfb7a405730f330cd95f666967b8d1231681e5095f8d3dd0"))
        for ((w,h,expected) in cases) {
            val source = IntArray(w*h) { i -> (255 shl 24) or ((i*17%256) shl 16) or ((i*31%256) shl 8) or (i*47%256) }
            val result = WholeUiTiles.resize(source,w,h)
            val rgb = ByteArray(result.size*3)
            result.forEachIndexed { i,p -> rgb[i*3]=(p ushr 16).toByte();rgb[i*3+1]=(p ushr 8).toByte();rgb[i*3+2]=p.toByte() }
            val actual = MessageDigest.getInstance("SHA-256").digest(rgb).joinToString("") { "%02x".format(it) }
            assertEquals("${w}x$h",expected,actual)
        }
    }
}
