package com.example.dopaminecut2.statistics

import com.example.dopaminecut2.domain.ContentCategory
import com.example.dopaminecut2.domain.SupportedPlatform
data class UsageEvent(
    val userId: String,
    val date: String,
    val platform: SupportedPlatform,
    val runTimeSec: Long = 0L,
    val shortformTimeSec: Long = 0L,
    val shortformCount: Long = 0L,
    val category: ContentCategory? = null,
    val deductedScore: Long = 0L,
    val createdAtEpochMs: Long
) {
    init {
        require(runTimeSec >= 0L)
        require(shortformTimeSec >= 0L)
        require(shortformCount >= 0L)
        require(deductedScore in 0L..50_000L)
        require(runTimeSec > 0L || shortformTimeSec > 0L || shortformCount > 0L)
        require(shortformCount == 0L || category != null)
    }
}
