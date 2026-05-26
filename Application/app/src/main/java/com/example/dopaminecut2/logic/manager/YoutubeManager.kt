package com.example.dopaminecut2.logic.manager

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class YoutubeManager :  AppManagerInterface {
    override val packageName: String = "com.google.android.youtube"
    override val platformName: String = "YouTube"

    /** 1. 숏폼 화면인지 확인 */
    override fun isShortformSection(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

//        Dislike, Share, Remix
//
//        싫어요, 공유, 리믹스
//        (숏폼에서만 확인할 수 있는 UI 로그에서의 특징 파악)
//
//        이 세 가지 중 두 가지가 포함되면 숏폼으로 간주함
        val shortsCoreIds = null


        return shortsCoreIds.any { rootNode.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }
    }

    /** 2. 현재 숏폼이 광고(Ad)인지 확인 */
    override fun isAdContent(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        return checkIsAdRecursive(rootNode)
    }

    private fun checkIsAdRecursive(node: AccessibilityNodeInfo): Boolean {
//        Sponsored 또는 스폰서 로 광고 구분
        if (node.contentDescription?.toString() == "") {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (checkIsAdRecursive(child)) return true
        }
        return false
    }

    /** * 3. 동일 영상 확인을 위한 식별자 생성 (가장 긴 텍스트 추출)
     * - 화면 캡처 및 OCR 타이머를 유지/초기화할 때 비교 대상으로 사용됨
     */
    override fun getVideoIdentifier(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null

        val IdentifierText =  findIdentifierText(rootNode)

        // 로딩 중이거나 텍스트가 아예 없는 경우 식별 불가 처리
        if (IdentifierText == null) return null

        Log.d("DopamineCut", "[Youtube] 영상 식별자 감지: $IdentifierText")

        // 문자열 자체가 영상의 고유한 ID 역할을 함
        return IdentifierText
    }

    /**
     * 화면 노드를 전체 순회하며 ViewGroup에 속한 텍스트 중 길이가 가장 긴 것을 찾음
     */
    private fun findIdentifierText(node: AccessibilityNodeInfo): String? {
        var longestText: String? = null
        var maxLength = -1

        // 내부 함수로 재귀 순회
        fun traverse(n: AccessibilityNodeInfo) {
            if (n.className?.toString() == "android.view.ViewGroup") {
                val text = n.text?.toString()?.trim() ?: n.contentDescription?.toString()?.trim()

                if (!text.isNullOrEmpty() && text.length > maxLength) {
                    maxLength = text.length
                    longestText = text
                }
            }

            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                traverse(child)
            }
        }

        traverse(node)
        return longestText
    }

    /** (선택 사항) 5초 타이머 초기 유지용 - longesttext로 영상 식별해서 같은 영상 시청했는지 확인 */
    fun getTrackingId(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        return findIdentifierText(rootNode)
    }
    // 해당 내용들은 acceesilibty 에서 scroll 이벤트 발생시에 실행되고 이벤트 발생 후 0.3초마다 반복 인식함
}