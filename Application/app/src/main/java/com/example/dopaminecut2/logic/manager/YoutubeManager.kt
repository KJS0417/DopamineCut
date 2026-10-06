package com.example.dopaminecut2.logic.manager

import com.example.dopaminecut2.domain.SupportedPlatform
import com.example.dopaminecut2.logic.shortform.ShortformScreenDetection
import com.example.dopaminecut2.logic.shortform.VideoIdentity
import com.example.dopaminecut2.logic.shortform.YoutubeAccessibilityIdentityExtractor
import com.example.dopaminecut2.logic.shortform.YoutubeEntryDetector

class YoutubeManager : BaseAppManager() {

    override val platform = SupportedPlatform.YOUTUBE
    private val entryDetector = YoutubeEntryDetector()
    private val identityExtractor = YoutubeAccessibilityIdentityExtractor()

    override fun isShortformSection(snapshot: ScreenSnapshot): Boolean {
        return detectShortformScreen(snapshot).isConfirmedShortform
    }

    override fun detectShortformScreen(snapshot: ScreenSnapshot): ShortformScreenDetection =
        entryDetector.detect(snapshot)

    override fun isAdContent(snapshot: ScreenSnapshot): Boolean {
        return com.example.dopaminecut2.logic.shortform.YoutubeAdEvidenceDetector.detect(snapshot).explicitAd
    }

    override fun getVideoIdentifier(snapshot: ScreenSnapshot): String? {
        return getVideoIdentity(snapshot, observedAtElapsedMs = 0L)?.contentKey
    }

    override fun getVideoIdentity(
        snapshot: ScreenSnapshot,
        observedAtElapsedMs: Long
    ): VideoIdentity = identityExtractor.extract(snapshot, observedAtElapsedMs)
}
