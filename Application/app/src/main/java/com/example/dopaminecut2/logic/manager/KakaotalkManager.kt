package com.example.dopaminecut2.logic.manager

import android.view.accessibility.AccessibilityNodeInfo

class KakaotalkManager : BaseAppManager() {

    override val packageName = "com.kakao.talk"
    override val platformName = "KakaoTalk"

    override fun isShortformSection(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        // 카톡 숏폼이 켜졌을 때의 단어 조합
        val shortformKeywords = listOf(
            "Shorts",
            "쇼츠",
            "숏폼",
            "조회수",
            "좋아요",
            "댓글",
            "공유",
            "음소거"
        )

        // 2개 이상 떠야, 숏폼으로 확인
        val matchedCount = shortformKeywords.count { keyword ->
            findNodeByText(rootNode, keyword)
        }

        // ------ [Start] Test Code ------

        // 2개를 넘겼는지 단순 Count 대신, 어떤 단어가 수집되었는지 나타내기
        val matchedKeywords = shortformKeywords.filter { keyword ->
            findNodeByText(rootNode, keyword)
        }

        // 화면에 단어가 1개라도 잡히면 출력
        if (matchedKeywords.isNotEmpty()) {
            android.util.Log.d("TEST_LOG", "[Kakao] 채팅방/친구창인데 잡힌 단어들: $matchedKeywords")
        }

        // ------ [End] Test Code

        // 단순히 단어 하나는 차단 방지
        return matchedCount >= 2
    }

    override fun getVideoIdentifier(rootNode: AccessibilityNodeInfo?): String? {
        return findLongestText(rootNode)
    }

    override fun isAdContent(rootNode: AccessibilityNodeInfo?): Boolean {
        return findNodeByAnyText(
            rootNode,
            listOf("광고", "Sponsored", "스폰서")
        )
    }
}
