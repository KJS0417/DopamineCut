package com.example.dopaminecut2.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class DailyStatistics(
    @DocumentId
    var documentId: String = "",

    @get:PropertyName("user_id")
    @set:PropertyName("user_id")
    var userId: String = "",

    var date: String = "",

    // 차트를 그리기 위한 앱별 사용량 데이터
    @get:PropertyName("app_usage")
    @set:PropertyName("app_usage")
    var appUsage: Map<String, AppUsage> = emptyMap()
)

data class AppUsage(
    @get:PropertyName("run_time_sec")
    @set:PropertyName("run_time_sec")
    var runTimeSec: Long = 0L,

    @get:PropertyName("shortform_time_sec")
    @set:PropertyName("shortform_time_sec")
    var shortformTimeSec: Long = 0L,

    @get:PropertyName("shortform_count")
    @set:PropertyName("shortform_count")
    var shortformCount: Long = 0L
)