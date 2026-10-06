package com.example.dopaminecut2.data.model

import java.util.Date

data class User(
    val userId: String = "",
    val nickname: String = "",
    val createdAt: Date = Date(),
    val restrictions: List<String> = emptyList(),
    val targetTimeMin: Int = 120,
    val targetCount: Int = 15
)
