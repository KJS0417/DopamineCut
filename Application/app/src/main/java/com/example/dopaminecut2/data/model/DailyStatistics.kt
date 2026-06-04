package com.example.dopaminecut2.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class DailyStatistics(
    @DocumentId
    val documentId: String = "",                 // 문서 ID ({user_id}_{YYYYMMDD})

    @PropertyName("user_id")
    val userId: String = "",

    val date: String = "",                       // 집계 기준 날짜 (예: "20260523")

    @PropertyName("daily_score")
    val dailyScore: Long = 0L,

    @PropertyName("shortform_time")
    val shortformTime: Long = 0L,

    @PropertyName("is_settled")
    val isSettled: Boolean = false,

    // Key: 플랫폼명 (instagram, kakaotalk, tiktok, youtube), Value: AppUsage 객체
    @PropertyName("app_usage")
    val appUsage: Map<String, AppUsage> = emptyMap()
)

data class AppUsage(
    @PropertyName("run_time_sec")
    val runTimeSec: Long = 0L,

    @PropertyName("shortform_time_sec")
    val shortformTimeSec: Long = 0L,

    @PropertyName("shortform_count")
    val shortformCount: Long = 0L
)
