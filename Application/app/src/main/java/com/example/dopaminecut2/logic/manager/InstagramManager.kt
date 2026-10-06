package com.example.dopaminecut2.logic.manager

import com.example.dopaminecut2.domain.SupportedPlatform

class InstagramManager : BaseAppManager() {

    override val platform = SupportedPlatform.INSTAGRAM

    override fun isShortformSection(snapshot: ScreenSnapshot): Boolean {
        return snapshot.containsAny(listOf("Reels", "릴스", "reel")) &&
            snapshot.matchedGroupCount(actionGroups) >= 1
    }

    override fun getVideoIdentifier(snapshot: ScreenSnapshot): String? {
        return snapshot.contentFingerprint(staticControlKeywords)
    }

    override fun isAdContent(snapshot: ScreenSnapshot): Boolean {
        return snapshot.containsAny(adKeywords + "협찬")
    }
}
