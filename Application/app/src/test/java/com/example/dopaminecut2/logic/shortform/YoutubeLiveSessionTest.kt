package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.logic.manager.NormalizedBounds
import com.example.dopaminecut2.logic.manager.ScreenElement
import com.example.dopaminecut2.logic.manager.ScreenSnapshot
import org.junit.Assert.*
import org.junit.Test

class YoutubeLiveSessionTest {
    @Test fun `live key is stable across pause and recovery without any metadata`() {
        var ids = 0
        val live = YoutubeLiveSession { (++ids).toString() }
        assertNull(live.key)
        val key = live.confirm()
        live.pause()
        assertFalse(live.confirmed)
        assertEquals(key, live.key)
        assertEquals(key, live.confirm())
        assertEquals(1, ids)
        live.reset()
        assertNull(live.key)
        assertFalse(live.confirmed)
        assertNotEquals(key, live.confirm())
    }

    @Test fun `live stays time only past forty seconds and paused interval is excluded`() {
        var now = 0L
        val live = YoutubeLiveSession { "session" }
        val tracker = ShortformSessionTracker(clockMs = { now }, onSessionCompleted = {})
        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, live.confirm(), ShortformContentKind.LIVE)
        now = 45_000
        tracker.tick()
        assertEquals(45L, tracker.activeProgress()!!.durationSec)
        assertEquals(0L, tracker.activeProgress()!!.count)
        val session = tracker.activeViewSessionId()
        live.pause()
        tracker.setPaused(true)
        now = 60_000
        tracker.onScreenChanged(true, SupportedPlatform.YOUTUBE, live.confirm(), ShortformContentKind.LIVE)
        tracker.setPaused(false)
        now = 65_000
        assertEquals(session, tracker.activeViewSessionId())
        assertEquals(50L, tracker.activeProgress()!!.durationSec)
        assertEquals(0L, tracker.activeProgress()!!.count)
        tracker.onScreenChanged(false, null, null, ShortformContentKind.UNKNOWN)
        live.reset()
        assertNull(tracker.activeProgress())
    }

    @Test fun `viewer count is not meaningful title text`() {
        listOf("875명 시청 중", "1,234명 시청 중", "1.2만명 시청 중").forEach {
            assertNull(VideoIdentityNormalizer.normalizeText(it))
        }
        assertNotNull(VideoIdentityNormalizer.normalizeText("배추 재배 방법"))
    }

    @Test fun `live instructions and viewers do not create a title`() {
        fun element(description: String, top: Float) = ScreenElement(null, description, null,
            "android.view.View", NormalizedBounds(.05f, top, .6f, top + .03f), false)
        val snapshot = ScreenSnapshot(emptyList(), emptyList(), listOf(
            element("@creator 채널로 이동", .7f),
            element("탭하여 실시간으로 시청하기", .75f),
            element("위로 이동 875명 시청 중", .8f),
            element("875명 시청 중", .85f)))
        val identity = YoutubeAccessibilityIdentityExtractor().extract(snapshot, 0)
        assertNull(identity.title)
        assertNull(identity.contentKey)
    }
}
