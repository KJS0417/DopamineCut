package com.example.dopaminecut2.logic.shortform

import com.example.dopaminecut2.logic.manager.ScreenElement
import com.example.dopaminecut2.logic.manager.ScreenSnapshot
import org.junit.Assert.*
import org.junit.Test

class YoutubeAdEvidencePolicyTest {
    private fun prediction(kind: ShortformContentKind) = ShortformContentPrediction(kind, .93f, mapOf(kind to .93f))
    private fun snapshot(vararg text: String) = ScreenSnapshot(text.toList(), emptyList(), text.map {
        ScreenElement(it, null, null, null, null, false)
    })
    @Test fun `shopping and commission labels are not explicit ads`() {
        val evidence = YoutubeAdEvidenceDetector.detect(snapshot("쇼핑하기", "수수료 지급"))
        assertTrue(evidence.shoppingLink)
        assertFalse(evidence.explicitAd)
    }
    @Test fun `independent sponsor label is explicit ad`() {
        assertTrue(YoutubeAdEvidenceDetector.detect(snapshot("스폰서")).explicitAd)
        assertTrue(YoutubeAdEvidenceDetector.detect(snapshot("Sponsored")).explicitAd)
    }
    @Test fun `title mentions and paid promotion do not imply platform ad`() {
        assertFalse(YoutubeAdEvidenceDetector.detect(snapshot("광고 없이 만드는 요리", "유료 광고 포함")).explicitAd)
        val element = ScreenElement("광고", null, "com.google.android.youtube:id/video_title", null, null, false)
        assertFalse(YoutubeAdEvidenceDetector.detect(ScreenSnapshot(listOf("광고"), emptyList(), listOf(element))).explicitAd)
    }
    @Test fun `text only fallback cannot provide structural evidence`() {
        assertEquals(YoutubeAdEvidence(), YoutubeAdEvidenceDetector.detect(ScreenSnapshot(listOf("광고"), emptyList())))
    }
    @Test fun `explicit ad wins even when shopping and normal prediction coexist`() {
        assertEquals(ShortformContentKind.AD, YoutubeAdEvidencePolicy.resolve(prediction(ShortformContentKind.NORMAL), YoutubeAdEvidence(true, true)).kind)
    }
    @Test fun `shopping normal corrects confident cv ad candidate`() {
        assertEquals(ShortformContentKind.NORMAL, YoutubeAdEvidencePolicy.resolve(prediction(ShortformContentKind.AD), YoutubeAdEvidence(shoppingLink = true)).kind)
    }
    @Test fun `missing evidence never turns cv ad into normal`() {
        assertEquals(ShortformContentKind.UNKNOWN, YoutubeAdEvidencePolicy.resolve(prediction(ShortformContentKind.AD), YoutubeAdEvidence()).kind)
    }
    @Test fun `shopping preserves live and unknown`() {
        for (kind in listOf(ShortformContentKind.LIVE, ShortformContentKind.UNKNOWN)) {
            assertEquals(kind, YoutubeAdEvidencePolicy.resolve(prediction(kind), YoutubeAdEvidence(shoppingLink = true)).kind)
        }
    }
    @Test fun `missing model stays unknown unless an explicit ad label exists`() {
        assertEquals(ShortformContentKind.UNKNOWN, YoutubeAdEvidencePolicy.resolve(null, YoutubeAdEvidence(shoppingLink = true)).kind)
        assertEquals(ShortformContentKind.AD, YoutubeAdEvidencePolicy.resolve(null, YoutubeAdEvidence(explicitAd = true)).kind)
    }
    @Test fun `ordinary normal prediction remains normal`() {
        assertEquals(ShortformContentKind.NORMAL, YoutubeAdEvidencePolicy.resolve(prediction(ShortformContentKind.NORMAL), YoutubeAdEvidence()).kind)
    }
    @Test fun `combined advertiser label supported only with ad structure`() {
        val element = ScreenElement(null, "ABC마트\n광고", null, "android.view.ViewGroup", null, false)
        assertFalse(YoutubeAdEvidenceDetector.detect(ScreenSnapshot(emptyList(), emptyList(), listOf(element))).explicitAd)
        val structured = ScreenSnapshot(emptyList(), listOf("com.google.android.youtube:id/reel_player_delegated_overlay"), listOf(element))
        assertTrue(YoutubeAdEvidenceDetector.detect(structured).explicitAd)
        val title = element.copy(viewId = "com.google.android.youtube:id/title")
        assertFalse(YoutubeAdEvidenceDetector.detect(structured.copy(elements = listOf(title))).explicitAd)
    }
}
