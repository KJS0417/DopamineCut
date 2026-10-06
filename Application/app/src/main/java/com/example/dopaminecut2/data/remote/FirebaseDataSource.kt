package com.example.dopaminecut2.data.remote

import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.data.model.CategoryUsage
import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.model.User
import com.example.dopaminecut2.statistics.UsageSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

interface RemoteUserDataSource {
    suspend fun fetchUser(userId: String): User
    suspend fun fetchMonthlyStatistics(
        userId: String,
        monthId: String
    ): Map<String, DailyStatistics>
    suspend fun writeUsageSnapshots(snapshots: List<UsageSnapshot>)
}

class FirebaseDataSource(
    private val firestore: FirebaseFirestore
) : RemoteUserDataSource {
    override suspend fun fetchUser(userId: String): User {
        val snapshot = firestore.collection(FirestoreCollections.USERS).document(userId).get().await()
        if (!snapshot.exists()) error("유저 정보를 찾을 수 없습니다.")
        return snapshot.toUser(userId)
    }

    override suspend fun fetchMonthlyStatistics(
        userId: String,
        monthId: String
    ): Map<String, DailyStatistics> {
        require(MONTH_PATTERN.matches(monthId))
        val snapshot = firestore.collection(FirestoreCollections.USERS)
            .document(userId)
            .collection(FirestoreCollections.MONTHS)
            .document(monthId)
            .get()
            .await()
        val days = snapshot.get(FirestoreFields.DAYS).asStringMap()
        return days.mapNotNull { (dayKey, rawDay) ->
            val day = dayKey.removePrefix(DAY_PREFIX)
            if (!DAY_PATTERN.matches(day)) return@mapNotNull null
            val date = monthId + day
            date to rawDay.asStringMap().toDailyStatistics(date)
        }.toMap()
    }

    override suspend fun writeUsageSnapshots(snapshots: List<UsageSnapshot>) {
        if (snapshots.isEmpty()) return
        try {
            val batch = firestore.batch()
            snapshots.groupBy { snapshot ->
                require(DATE_PATTERN.matches(snapshot.date))
                snapshot.userId to snapshot.date.substring(0, 6)
            }.forEach { (ownerMonth, monthSnapshots) ->
                val (userId, monthId) = ownerMonth
                val days = monthSnapshots.associate { snapshot ->
                    DAY_PREFIX + snapshot.date.substring(6, 8) to snapshot.toRemoteMap()
                }
                val reference = firestore.collection(FirestoreCollections.USERS)
                    .document(userId)
                    .collection(FirestoreCollections.MONTHS)
                    .document(monthId)
                batch.set(
                    reference,
                    mapOf(
                        FirestoreFields.SCHEMA_VERSION to CURRENT_FIRESTORE_SCHEMA_VERSION,
                        FirestoreFields.DAYS to days,
                        FirestoreFields.UPDATED_AT to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            batch.commit().await()
        } catch (error: FirebaseFirestoreException) {
            if (error.code in RETRYABLE_FIRESTORE_CODES) {
                throw RetryableRemoteWriteException(error)
            }
            throw error
        }
    }

    private fun UsageSnapshot.toRemoteMap(): Map<String, Any> = mapOf(
        FirestoreFields.APP_USAGE to appUsage.mapValues { (_, usage) ->
            mapOf(
                FirestoreFields.APP_TIME_SEC to usage.runTimeSec,
                FirestoreFields.SHORTFORM_TIME_SEC to usage.shortformTimeSec,
                FirestoreFields.SHORTFORM_COUNT to usage.shortformCount
            )
        },
        FirestoreFields.CATEGORY_USAGE to categoryUsage.mapValues { (_, usage) ->
            mapOf(
                FirestoreFields.CATEGORY_COUNT to usage.count,
                FirestoreFields.CATEGORY_DURATION_SEC to usage.durationSec
            )
        },
        FirestoreFields.HOURLY_SHORTFORM_COUNT to hourlyShortformCount,
        FirestoreFields.DEDUCTED_SCORE to deductedScore
    )

    private fun User.toRemoteMap(): Map<String, Any> = mapOf(
        FirestoreFields.SCHEMA_VERSION to CURRENT_FIRESTORE_SCHEMA_VERSION,
        FirestoreFields.PROFILE to mapOf(FirestoreFields.NICKNAME to nickname),
        FirestoreFields.GOAL to mapOf(
            FirestoreFields.APP_TIME_LIMIT_MIN to targetTimeMin,
            FirestoreFields.SHORTFORM_LIMIT_COUNT to targetCount,
            FirestoreFields.RESTRICTED_CATEGORIES to restrictions
        ),
        FirestoreFields.CREATED_AT to createdAt,
        FirestoreFields.UPDATED_AT to FieldValue.serverTimestamp()
    )

    private fun DocumentSnapshot.toUser(userId: String): User {
        check(getLong(FirestoreFields.SCHEMA_VERSION) == CURRENT_FIRESTORE_SCHEMA_VERSION) {
            "지원하지 않는 사용자 스키마입니다."
        }
        val profile = get(FirestoreFields.PROFILE)
            .requiredStringMap(FirestoreFields.PROFILE)
        val goal = get(FirestoreFields.GOAL)
            .requiredStringMap(FirestoreFields.GOAL)
        val createdAt = requireNotNull(getDate(FirestoreFields.CREATED_AT)) {
            "사용자 문서에 ${FirestoreFields.CREATED_AT}이 없습니다."
        }
        requireNotNull(getDate(FirestoreFields.UPDATED_AT)) {
            "사용자 문서에 ${FirestoreFields.UPDATED_AT}이 없습니다."
        }
        return User(
            userId = userId,
            nickname = profile[FirestoreFields.NICKNAME] as? String
                ?: error("사용자 문서의 ${FirestoreFields.NICKNAME} 형식이 올바르지 않습니다."),
            createdAt = createdAt,
            restrictions = goal[FirestoreFields.RESTRICTED_CATEGORIES]
                .requiredStringList(FirestoreFields.RESTRICTED_CATEGORIES),
            targetTimeMin = goal.requiredInt(FirestoreFields.APP_TIME_LIMIT_MIN),
            targetCount = goal.requiredInt(FirestoreFields.SHORTFORM_LIMIT_COUNT)
        )
    }

    private fun Map<String, Any?>.toDailyStatistics(date: String): DailyStatistics =
        DailyStatistics(
            date = date,
            deductedScore = longValue(FirestoreFields.DEDUCTED_SCORE),
            appUsage = this[FirestoreFields.APP_USAGE].asStringMap().mapValues { (_, rawUsage) ->
                rawUsage.asStringMap().let { usage ->
                    AppUsage(
                        runTimeSec = usage.longValue(FirestoreFields.APP_TIME_SEC),
                        shortformTimeSec = usage.longValue(FirestoreFields.SHORTFORM_TIME_SEC),
                        shortformCount = usage.longValue(FirestoreFields.SHORTFORM_COUNT)
                    )
                }
            },
            categoryUsage = this[FirestoreFields.CATEGORY_USAGE].asStringMap().mapValues { (_, rawUsage) ->
                rawUsage.asStringMap().let { usage ->
                    CategoryUsage(
                        count = usage.longValue(FirestoreFields.CATEGORY_COUNT),
                        durationSec = usage.longValue(FirestoreFields.CATEGORY_DURATION_SEC)
                    )
                }
            },
            hourlyShortformCount = this[FirestoreFields.HOURLY_SHORTFORM_COUNT]
                .asStringMap()
                .mapValues { (_, count) -> (count as? Number)?.toLong() ?: 0L }
        )

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asStringMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.requiredStringMap(fieldName: String): Map<String, Any?> =
        this as? Map<String, Any?>
            ?: error("사용자 문서의 $fieldName 형식이 올바르지 않습니다.")

    private fun Any?.requiredStringList(fieldName: String): List<String> {
        val values = this as? List<*>
            ?: error("사용자 문서의 $fieldName 형식이 올바르지 않습니다.")
        check(values.all { it is String }) {
            "사용자 문서의 $fieldName 형식이 올바르지 않습니다."
        }
        return values.filterIsInstance<String>()
    }

    private fun Map<String, Any?>.longValue(key: String): Long =
        (this[key] as? Number)?.toLong() ?: 0L

    private fun Map<String, Any?>.requiredInt(key: String): Int =
        (this[key] as? Number)?.toInt()
            ?: error("사용자 문서의 $key 형식이 올바르지 않습니다.")

    private companion object {
        const val DAY_PREFIX = "d"
        val DATE_PATTERN = Regex("^\\d{8}$")
        val MONTH_PATTERN = Regex("^\\d{6}$")
        val DAY_PATTERN = Regex("^(0[1-9]|[12]\\d|3[01])$")
        val RETRYABLE_FIRESTORE_CODES = setOf(
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.CANCELLED,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.UNKNOWN
        )
    }
}
