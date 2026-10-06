package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.logic.manager.NormalizedBounds
import com.example.dopaminecut2.logic.manager.ScreenElement
import com.example.dopaminecut2.logic.manager.ScreenSnapshot
import com.example.dopaminecut2.logic.manager.ScreenWindow
import org.junit.Assert.*
import org.junit.Test

class YoutubeContentRegionResolverTest {
    private val resolver = YoutubeContentRegionResolver()
    private fun snapshot(window: ScreenWindow, player: NormalizedBounds = NormalizedBounds(0f, 0f, 1f, 1f),
        creator: NormalizedBounds = NormalizedBounds(.1f, .8f, .6f, .85f)) = ScreenSnapshot(emptyList(), emptyList(), listOf(
        ScreenElement(null, null, "com.google.android.youtube:id/reel_watch_player", null, player, false),
        ScreenElement("@creator", null, null, null, creator, false)), window)

    @Test fun `resolution scaling keeps normalized region`() {
        val a = resolver.resolve(OcrFrame(emptyList(), 1000, 2000), snapshot(ScreenWindow(0, 0, 1000, 2000)))
        val b = resolver.resolve(OcrFrame(emptyList(), 2000, 4000), snapshot(ScreenWindow(0, 0, 2000, 4000)))
        assertEquals(a, b)
        assertEquals(.1f, a!!.top, .0001f)
        assertEquals(.79f, a.bottom, .0001f)
    }

    @Test fun `split window origin is mapped into full screenshot`() {
        val region = resolver.resolve(OcrFrame(emptyList(), 2000, 2400), snapshot(ScreenWindow(1000, 200, 1000, 2000)))!!
        assertEquals(.5f, region.left, .0001f)
        assertEquals(400f / 2400, region.top, .0001f)
        assertEquals(1780f / 2400, region.bottom, .0001f)
    }

    @Test fun `fold inner display can use narrow centered player`() {
        val region = resolver.resolve(OcrFrame(emptyList(), 1968, 2184), snapshot(ScreenWindow(0, 0, 1968, 2184),
            NormalizedBounds(.25f, .05f, .75f, .95f), NormalizedBounds(.3f, .8f, .6f, .85f)))!!
        assertEquals(.25f, region.left, .0001f)
        assertEquals(.14f, region.top, .0001f)
    }

    @Test fun `wide container cannot prove actual video area`() {
        assertNull(resolver.resolve(OcrFrame(emptyList(), 1968, 2184), snapshot(ScreenWindow(0, 0, 1968, 2184))))
    }

    @Test fun `side panel creator is not a bottom anchor`() {
        assertNull(resolver.resolve(OcrFrame(emptyList(), 2000, 2400), snapshot(ScreenWindow(0, 0, 2000, 2400),
            NormalizedBounds(0f, 0f, .5f, 1f), NormalizedBounds(.6f, .8f, .9f, .85f))))
    }

    @Test fun `missing player unknown display and offscreen window are rejected`() {
        val base = snapshot(ScreenWindow(0, 0, 1000, 2000))
        val frame = OcrFrame(emptyList(), 1000, 2000)
        assertNull(resolver.resolve(frame, base.copy(elements = base.elements.drop(1))))
        assertNull(resolver.resolve(frame, base.copy(displayId = 1)))
        assertNull(resolver.resolve(frame, base.copy(window = ScreenWindow(100, 0, 1000, 2000))))
    }

    @Test fun `ambiguous player pages are rejected`() {
        val base = snapshot(ScreenWindow(0, 0, 1000, 2000))
        assertNull(resolver.resolve(OcrFrame(emptyList(), 1000, 2000), base.copy(elements = base.elements +
            base.elements.first().copy(bounds = NormalizedBounds(0f, .1f, 1f, .9f)))))
    }

    @Test fun `ocr excludes button overlap and text beyond video`() {
        val base = snapshot(ScreenWindow(0, 0, 1000, 2000))
        val button = ScreenElement(null, null, "com.google.android.youtube:id/like_button", null,
            NormalizedBounds(.85f, .3f, 1f, .6f), false)
        val frame = OcrFrame(listOf(
            OcrTextLine("정상 영상 자막", NormalizedBounds(.1f, .3f, .7f, .4f)),
            OcrTextLine("버튼 근처 글자", NormalizedBounds(.8f, .3f, .95f, .4f)),
            OcrTextLine("작성자 영상 제목", NormalizedBounds(.1f, .8f, .7f, .9f))), 1000, 2000)
        assertEquals("정상 영상 자막", OcrContentTextSanitizer().sanitizeYoutube(frame, base.copy(elements = base.elements + button)))
    }
}
