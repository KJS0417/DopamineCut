package com.example.dopaminecut2.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Room(
    @DocumentId
    val roomId: String = "",                     // 문서 ID

    @PropertyName("room_name")
    val roomName: String = "",

    @PropertyName("master_id")
    val masterId: String = "",                   // 방장 UID

    @PropertyName("invite_code")
    val inviteCode: String = "",

    // Key: user_id (UID 문자열), Value: RoomMember 객체
    val members: Map<String, RoomMember> = emptyMap()
)

data class RoomMember(
    val nickname: String = "",

    @PropertyName("daily_score")
    val dailyScore: Long = 100L,                 // 매일 자정 100점 초기화

    @PropertyName("user_title")
    val userTitle: String = "일반",

    @PropertyName("user_status")
    val userStatus: String = "active"
)
