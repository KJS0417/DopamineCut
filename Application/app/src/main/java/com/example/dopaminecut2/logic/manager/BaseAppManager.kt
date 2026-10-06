package com.example.dopaminecut2.logic.manager

abstract class BaseAppManager : AppManagerInterface {
    protected val actionGroups = listOf(
        listOf("좋아요", "like"),
        listOf("댓글", "comment"),
        listOf("공유", "share"),
        listOf("리믹스", "remix"),
        listOf("팔로우", "follow")
    )

    protected val adKeywords = listOf("광고", "sponsored", "스폰서", "프로모션")

    protected val staticControlKeywords = actionGroups.flatten() + adKeywords + listOf(
        "shorts", "쇼츠", "reels", "릴스", "for you", "추천", "following", "팔로잉"
    )
}
