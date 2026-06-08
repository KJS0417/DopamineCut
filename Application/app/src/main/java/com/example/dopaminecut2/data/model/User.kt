package com.example.dopaminecut2.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class User(
    @DocumentId
    val userId: String = "",

    val email: String = "",
    val nickname: String = "",

    @PropertyName("created_at")
    val createdAt: Date = Date(),

    // 집중 페널티 태그 (최대 5개)
    val restrictions: List<String> = emptyList(),

    // 총 목표 사용 시간 (분)
    @PropertyName("target_time_min")
    val targetTimeMin: Int = 120,

    // 총 숏폼 시청 제한 횟수 (회)
    @PropertyName("target_shortform_count")
    val targetCount: Int = 15,

    val inventory: Inventory = Inventory()
)

data class Inventory(
    val poke: Long = 0L,
    val megaphone: Long = 0L
)