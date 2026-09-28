package com.example.dopaminecut2.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class User(
    @DocumentId
    var userId: String = "",

    @get:PropertyName("schema_version")
    @set:PropertyName("schema_version")
    var schemaVersion: Int = 2, // 버전 2로 되어있음

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Date = Date(),

    // 유저 데이터 변동 갱신일자
    @get:PropertyName("updated_at")
    @set:PropertyName("updated_at")
    var updatedAt: Date = Date(),

    // 1. 프로필 및 치장용 (칭호, 소모임 상태 등)
    var profile: UserProfile = UserProfile(),

    // 2. 목표 설정 (goal 맵)
    var goal: UserGoal = UserGoal(),

    // 3. 오늘의 일일 데이터
    var today: UserToday = UserToday(),

    // 4 .주간 랭킹전 전용 데이터 (매주 월요일 00시 리셋 생각 중)
    @get:PropertyName("weekly_score")
    @set:PropertyName("weekly_score")
    var weeklyScore: Long = 0L,

    // 5. 주간 누적 집중 시간 (3시간 이상이어야 랭킹 등록하게함.)
    @get:PropertyName("weekly_focus_time_sec")
    @set:PropertyName("weekly_focus_time_sec")
    var weeklyFocusTimeSec: Long = 0L,

    // 6. 인벤토리 (상점 아이템)
    var inventory: Inventory = Inventory(),

    // 7. 달력 연속 달성 기록
    @get:PropertyName("current_streak_days")
    @set:PropertyName("current_streak_days")
    var currentStreakDays: Int = 0
)

data class UserProfile(
    var nickname: String = "",

    // 그룹 방 번호 (없으면 null)
    @get:PropertyName("joined_room_id")
    @set:PropertyName("joined_room_id")
    var joinedRoomId: String? = null,

    // 그룹용 실시간 상태 ("OFFLINE", "STUDY", "SHORTS")
    // User가 쇼츠를 보고있으면 "SHORTS" 상태로 변경. 오프라인은 "OFFLINE", 공부 중이면 "STUDY"
    @get:PropertyName("current_status")
    @set:PropertyName("current_status")
    var currentStatus: String = "OFFLINE",

    // 패널티 및 상점 칭호 ("게으름뱅이", "도파민 국왕")
    @get:PropertyName("equipped_title")
    @set:PropertyName("equipped_title")
    var equippedTitle: String = "일반",

    // 이름표 색상 ("RED", "DEFAULT")
    // 패널티용 이름 색상 : RED, 그 외 색상도 상의 후 생각해보기.
    @get:PropertyName("name_color")
    @set:PropertyName("name_color")
    var nameColor: String = "DEFAULT"
)

// 앱 사용 최소 시간 및 숏폼 시청 횟수 설정 한계치 정하는 데이터, 태그 제한용 목표 설정 코드
data class UserGoal(
    @get:PropertyName("app_time_limit_min")
    @set:PropertyName("app_time_limit_min")
    var appTimeLimitMin: Int = 120,

    @get:PropertyName("shortform_limit_count")
    @set:PropertyName("shortform_limit_count")
    var shortformLimitCount: Int = 15,

    @get:PropertyName("restricted_categories")
    @set:PropertyName("restricted_categories")
    var restrictedCategories: List<String> = emptyList()
)

data class UserToday(
    // 점수 (이 데이터는 파이어베이스에서 상위 % 랭킹을 변동할 수 있음.)
    @get:PropertyName("daily_score")
    @set:PropertyName("daily_score")
    var dailyScore: Long = 0L,

    // 집중한 시간 (공부 타이머용)
    // 기능 자체는 타이머 구조 (DB 내부에서는 공부 시간을 누적).
    @get:PropertyName("focus_time_sec")
    @set:PropertyName("focus_time_sec")
    var focusTimeSec: Long = 0L,

    // 연속 숏폼 시청 횟수 (가중치 패널티 계산 용도)
    @get:PropertyName("consecutive_shorts_count")
    @set:PropertyName("consecutive_shorts_count")
    var consecutiveShortsCount: Int = 0,

    // 쉴드(휴식 아이템) 적용 여부 시간 기록
    @get:PropertyName("shield_active_until")
    @set:PropertyName("shield_active_until")
    var shieldActiveUntil: Date? = null
)

data class Inventory(
    var poke: Long = 0L,       // 찌르기 아이템
    var megaphone: Long = 0L,  // 확성기 아이템
    var shield: Long = 0L      // 휴식 아이템
)