package com.example.dopaminecut2.logic.manager

import android.view.accessibility.AccessibilityNodeInfo

class TiktokManager : BaseAppManager() {

    override val packageName = "com.zhiliaoapp.musically"
    override val platformName = "Tiktok"

    override fun isShortformSection(root: AccessibilityNodeInfo?): Boolean {
        return root != null // 틱톡은 거의 항상 숏폼
    }

    override fun getVideoIdentifier(root: AccessibilityNodeInfo?): String? {
        return findLongestText(root)
    }

    override fun isAdContent(root: AccessibilityNodeInfo?): Boolean {
        return findNodeByText(root, "Sponsored") ||
                findNodeByText(root, "광고")
    }
}
