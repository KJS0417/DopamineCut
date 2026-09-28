package com.example.dopaminecut2.data.remote

import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.model.DopamineLog
import com.example.dopaminecut2.data.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // 유저 정보 조회
    suspend fun fetchUser(userId: String): User {
        val snapshot = firestore.collection("users").document(userId).get().await()
        return snapshot.toObject(User::class.java)
            ?: throw Exception("유저 정보를 찾을 수 없습니다.")
    }

    // 유저 정보 실시간 스트림 (아이템 개수, 점수 변동 등을 화면에 즉각 반영하기 위함)
    fun getUserStream(userId: String): Flow<User> = callbackFlow {
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(User::class.java)
                if (user != null) {
                    trySend(user).isSuccess
                }
            }
        // 코루틴이 취소되거나 화면이 꺼지면 리스너를 안전하게 해제함
        awaitClose { listener.remove() }
    }

    // 차단 카테고리/목표 설정 업데이트
    /*
    suspend fun updateUserRestrictions(userId: String, restrictions: List<String>) {
        firestore.collection("users").document(userId)
            .update("restrictions", restrictions)
            .await() // 코루틴을 통해 서버 응답이 올 때까지 대기
    }
    */

    // 차단 카테고리/통합 목표 설정 업데이트
    suspend fun updateUserTargetSettings(userId: String, timeLimit: Int, countLimit: Int, tags: List<String>) {

        // goal Map에 데이터 세팅.
        val updates = hashMapOf<String, Any>(
            "updated_at" to java.util.Date(),
            "goal" to hashMapOf(
                "app_time_limit_min" to timeLimit,
                "shortform_limit_count" to countLimit,
                "restricted_categories" to tags
            )
        )

        // set과 merge로 데이터를 날리지 않고 덮어씌우기
        firestore.collection("users").document(userId)
            .set(updates, SetOptions.merge())
            .await()
    }

    // 오늘 날짜의 앱 통계 실시간 스트림
    fun getDailyStatisticsStream(userId: String, date: String): Flow<DailyStatistics?> = callbackFlow {
        val documentId = "${userId}_${date}"
        val listener = firestore.collection("daily_statistics").document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val stats = snapshot?.toObject(DailyStatistics::class.java)
                trySend(stats).isSuccess
            }
        awaitClose { listener.remove() }
    }

    // 해당 유저의 도파민 시청 기록 실시간 스트림
    fun getDopamineLogsStream(userId: String): Flow<List<DopamineLog>> = callbackFlow {
        val listener = firestore.collection("dopamine_logs")
            .whereEqualTo("user_id", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val logs = snapshot?.documents?.mapNotNull {
                    it.toObject(DopamineLog::class.java)
                } ?: emptyList()

                trySend(logs).isSuccess
            }
        awaitClose { listener.remove() }
    }

    // 숏폼 시청 로그 저장 (문서 ID는 자동 생성)
    suspend fun insertDopamineLog(log: DopamineLog) {
        firestore.collection("dopamine_logs").add(log).await()
    }

    // 앱 사용량 실시간 누적 (FieldValue.increment 활용)
    suspend fun incrementAppUsageData(
        userId: String,
        date: String,
        platform: String,
        durationSec: Long,
        isShortform: Boolean
    ) {
        val documentId = "${userId}_${date}"

        // 1. 기본적인 총 사용 시간(run_time_sec)은 무조건 더한다.
        val platformUpdates = hashMapOf<String, Any>(
            "run_time_sec" to com.google.firebase.firestore.FieldValue.increment(durationSec)
        )

        // 2. 숏폼을 본 거면, 숏폼 시간과 횟수도 같이 더한다.
        if (isShortform) {
            platformUpdates["shortform_time_sec"] = com.google.firebase.firestore.FieldValue.increment(durationSec)
            platformUpdates["shortform_count"] = com.google.firebase.firestore.FieldValue.increment(1L)
        }

        // 3. 업데이트할 데이터를 Key-Value의 Map 형태 객체로 만든다.
        val updates = hashMapOf<String, Any>(
            "user_id" to userId,
            "date" to date,
            "app_usage" to hashMapOf(
                platform.lowercase() to platformUpdates
            )
        )

        // 4. 파이어베이스에 merge로 덮어쓴다. (안전용)
        firestore.collection("daily_statistics").document(documentId)
            .set(updates, SetOptions.merge())
            .await()
    }

    // 최근 7일치 통계 데이터 실시간 스트림
    fun getWeeklyStatisticsStream(userId: String): Flow<List<DailyStatistics>> = callbackFlow {
        val listener = firestore.collection("daily_statistics")
            .whereEqualTo("user_id", userId)
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(7) // 최근 7일치만 가져오기
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val statsList = snapshot?.documents?.mapNotNull {
                    it.toObject(DailyStatistics::class.java)
                } ?: emptyList()

                trySend(statsList).isSuccess
            }
        awaitClose { listener.remove() }
    }
}