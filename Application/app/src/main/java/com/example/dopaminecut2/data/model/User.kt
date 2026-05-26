package com.example.dopaminecut2.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class User(
    @DocumentId
    val userId: String = "",                     // 문서 ID (Firebase Auth UID)

    val email: String = "",
    val nickname: String = "",

    @PropertyName("created_at")
    val createdAt: Date = Date(),

    val restrictions: List<String> = emptyList(), // 차단 카테고리
    val inventory: Inventory = Inventory()        // 아이템 인벤토리 (Map)
)

data class Inventory(
    val poke: Long = 0L,
    val megaphone: Long = 0L
)
