package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.logic.manager.NormalizedBounds
import com.example.dopaminecut2.logic.manager.ScreenSnapshot
import com.example.dopaminecut2.logic.manager.ScreenElement
import com.example.dopaminecut2.logic.manager.ScreenWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrContentTextSanitizerTest {
    private val sanitizer = OcrContentTextSanitizer()

    @Test fun `youtube keeps upper captions and excludes creator title and crossing lines`() {
        val snapshot = youtubeSnapshot(listOf(ScreenElement("@creator", null,
            null, null, NormalizedBounds(.05f, .7f, .65f, .75f), false)))
        val frame = OcrFrame(listOf(line("윗부분 자막", .4f, .13f),
            line("영상 설명 자막", .4f, .5f), line("작성자 이름", .4f, .72f),
            line("영상 제목", .4f, .8f), line("경계에 걸친 자막", .4f, .68f),
            line("상태 표시", .4f, .05f)), 1000, 2000)
        assertEquals("윗부분 자막 영상 설명 자막", sanitizer.sanitizeYoutube(frame, snapshot))
    }

    @Test fun `youtube does not retain bottom text without a creator anchor`() {
        assertEquals("", sanitizer.sanitizeYoutube(OcrFrame(listOf(line("영상 제목", .4f, .8f))),
            ScreenSnapshot(emptyList(), emptyList())))
    }

    @Test fun `creator moved upward shrinks category region`() {
        val snapshot = youtubeSnapshot(listOf(ScreenElement(null,
            "@creator 채널로 이동", null, null, NormalizedBounds(.05f, .5f, .65f, .55f), false)))
        assertEquals("윗 자막", sanitizer.sanitizeYoutube(OcrFrame(listOf(
            line("윗 자막", .4f, .3f), line("아래 자막", .4f, .6f)), 1000, 2000), snapshot))
    }

    @Test
    fun `keeps content text and removes account and control text`() {
        val result = sanitizer.sanitize(
            OcrFrame(
                lines = listOf(
                    line("오늘 경기에서 나온 최고의 장면", 0.2f, 0.4f),
                    line("@private_user", 0.2f, 0.7f),
                    line("좋아요", 0.8f, 0.5f),
                    line("person@example.com", 0.2f, 0.5f),
                    line("댓글 내용", 0.95f, 0.5f)
                )
            )
        )

        assertTrue(result.contains("최고의 장면"))
        assertFalse(result.contains("private_user"))
        assertFalse(result.contains("좋아요"))
        assertFalse(result.contains("example.com"))
        assertFalse(result.contains("댓글"))
    }

    private fun youtubeSnapshot(elements: List<ScreenElement>) = ScreenSnapshot(emptyList(), emptyList(),
        elements + ScreenElement(null, null, "com.google.android.youtube:id/reel_watch_player", null,
            NormalizedBounds(0f, 0f, 1f, 1f), false), ScreenWindow(0, 0, 1000, 2000))

    private fun line(text: String, centerX: Float, centerY: Float) = OcrTextLine(
        text = text,
        bounds = NormalizedBounds(
            left = centerX - 0.05f,
            top = centerY - 0.02f,
            right = centerX + 0.05f,
            bottom = centerY + 0.02f
        )
    )
}
