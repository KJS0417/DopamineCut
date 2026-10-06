package com.example.dopaminecut2.data.remote

import com.example.dopaminecut2.domain.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

data class CloudGoals(val version: Long, val goals: List<ManagedGoal>)
class GoalConflictException : IllegalStateException("다른 기기의 목표가 변경됐습니다. 사용할 설정을 선택해 주세요.")
interface RemoteGoalSource {
    suspend fun fetch(userId: String): CloudGoals?
    suspend fun save(userId: String, expectedVersion: Long, goals: List<ManagedGoal>): CloudGoals
}

object CloudGoalCodec {
    fun encode(goals: List<ManagedGoal>): Map<String, Any> = goals.associate { goal ->
        goal.validate()
        goal.metric.name to mapOf("target" to goal.target, "platforms" to goal.platforms.map { it.name }.sorted(),
            "intervention" to goal.intervention.name, "status" to goal.status.name, "revision" to goal.revision)
    }
    fun decode(data: Map<String, Any>): CloudGoals {
        require((data["schema_version"] as? Number)?.toLong() == 1L)
        val version = (data["version"] as Number).toLong()
        require(version > 0)
        val rows = data["goals"] as Map<*, *>
        require(rows.size <= 3)
        val goals = rows.map { (key, value) ->
            val row = value as Map<*, *>
            ManagedGoal(GoalMetric.valueOf(key as String), (row["target"] as Number).toInt(),
                (row["platforms"] as List<*>).map { SupportedPlatform.valueOf(it as String) }.toSet(),
                InterventionMode.valueOf(row["intervention"] as String), GoalStatus.valueOf(row["status"] as String),
                (row["revision"] as Number).toLong()).also { it.validate() }
        }.sortedBy { it.metric.ordinal }
        return CloudGoals(version, goals)
    }
}

class FirestoreGoalSource(private val firestore: FirebaseFirestore) : RemoteGoalSource {
    private fun document(uid: String) = firestore.collection("users").document(uid).collection("settings").document("goals")
    override suspend fun fetch(userId: String): CloudGoals? {
        val snapshot = document(userId).get(Source.SERVER).await()
        return snapshot.data?.let(CloudGoalCodec::decode)
    }
    override suspend fun save(userId: String, expectedVersion: Long, goals: List<ManagedGoal>): CloudGoals =
        firestore.runTransaction { transaction ->
            val ref = document(userId)
            val current = transaction.get(ref).data?.let(CloudGoalCodec::decode)
            if (current?.goals == goals) return@runTransaction current
            if ((current?.version ?: 0L) != expectedVersion) throw GoalConflictException()
            val next = expectedVersion + 1
            transaction.set(ref, mapOf("schema_version" to 1, "version" to next, "goals" to CloudGoalCodec.encode(goals),
                "updated_at" to FieldValue.serverTimestamp()))
            CloudGoals(next, goals)
        }.await()
}
