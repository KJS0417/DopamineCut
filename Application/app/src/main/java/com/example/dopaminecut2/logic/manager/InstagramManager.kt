package com.example.dopaminecut2.logic.manager

import android.view.accessibility.AccessibilityNodeInfo

class InstagramManager : BaseAppManager() {

    override val packageName = "com.instagram.android"
    override val platformName = "Instagram"

    override fun isShortformSection(root: AccessibilityNodeInfo?): Boolean {
        return findNodeByText(root, "Reels") ||
                findNodeByText(root, "릴스")
    }

    override fun getVideoIdentifier(root: AccessibilityNodeInfo?): String? {
        return findLongestText(root)
    }

    override fun isAdContent(root: AccessibilityNodeInfo?): Boolean {
        return findNodeByText(root, "Sponsored") ||
                findNodeByText(root, "광고")
    }
}
