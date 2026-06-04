package com.example.dopaminecut2.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dopaminecut2.data.model.Inventory
import com.example.dopaminecut2.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class AuthViewModel : ViewModel() {

    // Firebase 인스턴스 가져오기
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // 1. 로딩 상태 관리 (UI에서 프로그레스 바 표시용)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    // 2. 일회성 이벤트 전달 (토스트 메시지, 화면 전환 등)
    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent: SharedFlow<String> get() = _uiEvent

    /**
     * 입력값 유효성 검사 내부 함수
     */
    private fun validateInput(email: String, pw: String): Boolean {
        if (email.isBlank() || pw.isBlank()) {
            emitEvent("이메일과 비밀번호를 모두 입력해주세요.")
            return false
        }
        if (pw.length < 6) {
            emitEvent("비밀번호는 6자리 이상이어야 합니다.")
            return false
        }
        return true
    }

    /**
     * 로그인 요청 로직
     */
    fun login(email: String, pw: String) {
        if (!validateInput(email, pw)) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Firebase Auth 로그인 요청 (await()로 동기식 처리)
                auth.signInWithEmailAndPassword(email, pw).await()
                emitEvent("LOGIN_SUCCESS") // UI 쪽에 성공 신호 전달
            } catch (e: Exception) {
                emitEvent("로그인 실패: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 회원가입 및 초기 데이터 생성 로직
     */
    fun signup(email: String, pw: String, nickname: String) {
        if (!validateInput(email, pw)) return
        if (nickname.isBlank()) {
            emitEvent("닉네임을 입력해주세요.")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Firebase Auth에 유저 계정 생성
                val authResult = auth.createUserWithEmailAndPassword(email, pw).await()
                val uid = authResult.user?.uid ?: throw Exception("UID 생성 실패")

                // 2. 기획된 명세서에 맞추어 유저 초기값(DB 객체) 세팅
                val newUser = User(
                    userId = uid,
                    email = email,
                    nickname = nickname,
                    createdAt = Date(),
                    restrictions = emptyList(), // 차단 카테고리 초기값 (빈 배열)
                    inventory = Inventory(poke = 0L, megaphone = 0L) // 아이템 초기값 0
                )

                // 3. Firestore 'users' 컬렉션에 초기 데이터 적재
                firestore.collection("users").document(uid).set(newUser).await()

                emitEvent("SIGNUP_SUCCESS") // UI 쪽에 가입 성공 신호 전달
            } catch (e: Exception) {
                emitEvent("회원가입 실패: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 코루틴 환경에서 이벤트를 안전하게 전송하기 위한 헬퍼 함수
     */
    private fun emitEvent(message: String) {
        viewModelScope.launch {
            _uiEvent.emit(message)
        }
    }
}