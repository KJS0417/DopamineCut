package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.domain.ContentCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class DopamineScorePolicyTest {
    @Test
    fun `restriction order controls deduction`() {
        val restrictions = listOf(ContentCategory.GAME, ContentCategory.FOOD, ContentCategory.ENTERTAINMENT)
        assertEquals(5L, DopamineScorePolicy.deductedScore(ContentCategory.GAME, restrictions))
        assertEquals(4L, DopamineScorePolicy.deductedScore(ContentCategory.FOOD, restrictions))
        assertEquals(1L, DopamineScorePolicy.deductedScore(ContentCategory.EDUCATION, restrictions))
    }

    @Test
    fun `unresolved classification never deducts a score`() {
        assertEquals(0L, DopamineScorePolicy.deductedScore(
            ContentCategory.UNKNOWN, listOf(ContentCategory.UNKNOWN)
        ))
    }
}
