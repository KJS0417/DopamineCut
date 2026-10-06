package com.example.dopaminecut2.logic.manager

import com.example.dopaminecut2.domain.SupportedPlatform

class KakaotalkManager : BaseAppManager() {

    override val platform = SupportedPlatform.KAKAOTALK

    override fun isShortformSection(snapshot: ScreenSnapshot): Boolean {
        val hasShortformMarker = snapshot.containsAny(listOf("펑", "쇼츠", "shorts", "숏폼"))
        return hasShortformMarker && snapshot.matchedGroupCount(actionGroups) >= 1
    }

    override fun getVideoIdentifier(snapshot: ScreenSnapshot): String? {
        return snapshot.contentFingerprint(staticControlKeywords)
    }

    override fun isAdContent(snapshot: ScreenSnapshot): Boolean {
        return snapshot.containsAny(adKeywords)
    }
}
