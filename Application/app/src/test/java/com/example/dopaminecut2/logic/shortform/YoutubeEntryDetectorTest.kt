package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.logic.manager.ScreenSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeEntryDetectorTest {
    private val detector = YoutubeEntryDetector()

    @Test
    fun `shorts player root confirms entry without visible labels`() {
        val result = detector.detect(
            ScreenSnapshot(
                texts = listOf("영상 제목"),
                viewIds = listOf("com.google.android.youtube:id/reel_watch_fragment_root")
            )
        )

        assertEquals(ShortformScreenState.INSIDE, result.state)
        assertTrue(result.isConfirmedShortform)
        assertTrue("player_root" in result.evidence)
    }

    @Test
    fun `single weak action remains a candidate`() {
        val result = detector.detect(ScreenSnapshot(listOf("Shorts", "좋아요"), emptyList()))

        assertEquals(ShortformScreenState.CANDIDATE, result.state)
        assertFalse(result.isConfirmedShortform)
    }

    @Test
    fun `navigation marker and ordinary player actions do not confirm entry`() {
        val result = detector.detect(
            ScreenSnapshot(listOf("Shorts", "좋아요", "댓글", "공유"), emptyList())
        )

        assertEquals(ShortformScreenState.CANDIDATE, result.state)
        assertFalse(result.isConfirmedShortform)
    }

    @Test
    fun `shorts player overlay remains inside shortform`() {
        val result = detector.detect(
            ScreenSnapshot(
                texts = listOf("댓글 패널"),
                viewIds = listOf("reel_watch_fragment_root")
            )
        )

        assertEquals(ShortformScreenState.OVERLAY, result.state)
        assertTrue(result.isConfirmedShortform)
    }

    @Test
    fun `resource id substrings and different packages are rejected`() {
        for (id in listOf(
            "reel_watch_fragment_root_thumbnail",
            "com.other.app:id/reel_watch_fragment_root",
            "shorts_container_thumbnail"
        )) {
            assertFalse(detector.detect(ScreenSnapshot(emptyList(), listOf(id))).isConfirmedShortform)
        }
    }

    @Test
    fun `current shorts structure confirms entry without language labels`() {
        val snapshot = ScreenSnapshot(
            emptyList(),
            listOf(
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_recycler"
            )
        )

        assertTrue(detector.detect(snapshot).isConfirmedShortform)
        assertEquals(ShortformScreenState.INSIDE, detector.detect(snapshot).state)
    }

    @Test
    fun `only one current structural id does not confirm entry`() {
        for (id in listOf("reel_player_page_container", "reel_recycler")) {
            assertFalse(detector.detect(ScreenSnapshot(emptyList(), listOf(id))).isConfirmedShortform)
        }
    }

    @Test
    fun `unobserved legacy fallback ids no longer confirm entry`() {
        for (id in listOf("reel_progress_bar", "shorts_container")) {
            assertFalse(detector.detect(ScreenSnapshot(emptyList(), listOf(id))).isConfirmedShortform)
        }
    }

    @Test
    fun `shorts comments panel remains an overlay when player nodes are hidden`() {
        val result = detector.detect(
            ScreenSnapshot(
                texts = listOf("댓글. 250", "닫기"),
                viewIds = listOf(
                    "com.google.android.youtube:id/app_engagement_panel",
                    "com.google.android.youtube:id/panel_content_touch_wrapper",
                    "com.google.android.youtube:id/reel_time_bar"
                )
            )
        )

        assertEquals(ShortformScreenState.OVERLAY, result.state)
        assertTrue(result.isConfirmedShortform)
    }

    @Test
    fun `ordinary video comments panel does not become a shorts overlay`() {
        val result = detector.detect(
            ScreenSnapshot(
                texts = listOf("댓글", "닫기"),
                viewIds = listOf(
                    "com.google.android.youtube:id/engagement_panel_wrapper",
                    "com.google.android.youtube:id/watch_panel",
                    "com.google.android.youtube:id/reel_time_bar"
                )
            )
        )

        assertFalse(result.isConfirmedShortform)
    }

    @Test
    fun `title containing overlay words is not treated as a panel`() {
        val result = detector.detect(ScreenSnapshot(
            listOf("댓글 패널 만드는 방법 강의"),
            listOf("reel_player_page_container", "reel_recycler")
        ))
        assertEquals(ShortformScreenState.INSIDE, result.state)
    }
}
