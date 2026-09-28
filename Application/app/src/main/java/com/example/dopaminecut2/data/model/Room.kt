package com.example.dopaminecut2.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Room(
    @DocumentId
    var roomId: String = "",

    @get:PropertyName("room_name")
    @set:PropertyName("room_name")
    var roomName: String = "",

    // 방장 UID
    @get:PropertyName("master_id")
    @set:PropertyName("master_id")
    var masterId: String = "",

    // 초대용 그룹방 코드
    @get:PropertyName("invite_code")
    @set:PropertyName("invite_code")
    var inviteCode: String = "",

    // 그룹 참여 멤버 (Key: 유저 UID)
    var members: Map<String, RoomMember> = emptyMap()
)

data class RoomMember(
    var nickname: String = "",

    // 그룹 내 랭킹도 주간 점수로 띄우기 (그룹 안에서의 미니 랭킹용)
    // 머무는 동안 작게 경쟁심을 주는 용도
    @get:PropertyName("weekly_score")
    @set:PropertyName("weekly_score")
    var weeklyScore: Long = 0L,

    @get:PropertyName("equipped_title")
    @set:PropertyName("equipped_title")
    var equippedTitle: String = "일반",

    @get:PropertyName("current_status")
    @set:PropertyName("current_status")
    var currentStatus: String = "OFFLINE"
)