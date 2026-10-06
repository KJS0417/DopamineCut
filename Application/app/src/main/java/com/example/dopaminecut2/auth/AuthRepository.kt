package com.example.dopaminecut2.auth

import com.example.dopaminecut2.data.remote.CURRENT_FIRESTORE_SCHEMA_VERSION
import com.example.dopaminecut2.data.remote.FirestoreCollections
import com.example.dopaminecut2.data.remote.FirestoreFields
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    fun currentUserId(): String?
    fun currentUserEmail(): String? = null
    fun authState(): Flow<String?> = flowOf(currentUserId())
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun signup(email: String, password: String, nickname: String): Result<Unit>
    suspend fun requestPasswordReset(email: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("비밀번호 재설정 기능이 연결되지 않았습니다."))
    fun logout(): Result<Unit>
}

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {
    override fun currentUserId(): String? = auth.currentUser?.uid
    override fun currentUserEmail(): String? = auth.currentUser?.email

    override fun authState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun signup(
        email: String,
        password: String,
        nickname: String
    ): Result<Unit> = runCatching {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = authResult.user?.uid ?: error("UID 생성 실패")
        val user = mapOf(
            FirestoreFields.SCHEMA_VERSION to CURRENT_FIRESTORE_SCHEMA_VERSION,
            FirestoreFields.PROFILE to mapOf(
                FirestoreFields.NICKNAME to nickname
            ),
            FirestoreFields.GOAL to mapOf(
                FirestoreFields.APP_TIME_LIMIT_MIN to DEFAULT_TARGET_TIME_MIN,
                FirestoreFields.SHORTFORM_LIMIT_COUNT to DEFAULT_TARGET_COUNT,
                FirestoreFields.RESTRICTED_CATEGORIES to emptyList<String>()
            ),
            FirestoreFields.CREATED_AT to FieldValue.serverTimestamp(),
            FirestoreFields.UPDATED_AT to FieldValue.serverTimestamp()
        )
        try {
            firestore.collection(FirestoreCollections.USERS).document(uid).set(user).await()
        } catch (error: Exception) {
            runCatching { authResult.user?.delete()?.await() }
            throw error
        }
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    override fun logout(): Result<Unit> = runCatching { auth.signOut() }

    private companion object {
        const val DEFAULT_TARGET_TIME_MIN = 120
        const val DEFAULT_TARGET_COUNT = 15
    }
}
