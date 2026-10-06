package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.domain.ContentCategory

/** 분류 결과를 일관된 9개 카테고리와 감점 값으로 변환한다. */
object DopamineScorePolicy {
    fun deductedScore(
        category: ContentCategory,
        restrictions: List<ContentCategory>
    ): Long {
        if (category == ContentCategory.UNKNOWN) return 0L
        val restrictionIndex = restrictions.indexOf(category)
        return if (restrictionIndex >= 0) {
            // 현재 UI의 선택 순서를 우선순위로 사용한다. 첫 항목일수록 감점이 크다.
            (5 - restrictionIndex.coerceAtMost(4)).toLong()
        } else {
            1L
        }
    }
}
