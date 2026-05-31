package com.example.dopaminecut2.logic.manager

import android.view.accessibility.AccessibilityNodeInfo

class KakaotalkManager : BaseAppManager() {

    override val packageName = "com.kakao.talk"
    override val platformName = "Kakaotalk"

    override fun isShortformSection(root: AccessibilityNodeInfo?): Boolean {
        return findNodeByText(root, "재생") ||
                findNodeByText(root, "동영상")
    }

    override fun getVideoIdentifier(root: AccessibilityNodeInfo?): String? {
        return findLongestText(root)
    }

    override fun isAdContent(root: AccessibilityNodeInfo?): Boolean {
        return findNodeByText(root, "광고")
    }
}
