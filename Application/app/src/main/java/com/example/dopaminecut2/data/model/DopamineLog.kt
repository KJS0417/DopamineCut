package com.example.dopaminecut2.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class DopamineLog(
    @DocumentId
    val logId: String = "",                      // 문서 ID (자동 생성)

    @PropertyName("user_id")
    val userId: String = "",

    val platform: String = "",

    @PropertyName("created_at")
    val createdAt: Date = Date(),

    val category: String = "",                   // OCR 분류 카테고리

    @PropertyName("duration_sec")
    val durationSec: Long = 0L
)
