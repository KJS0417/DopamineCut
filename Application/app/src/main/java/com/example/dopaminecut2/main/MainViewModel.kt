package com.example.dopaminecut2.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dopaminecut2.data.local.DataStoreManager
import com.example.dopaminecut2.data.remote.FirebaseDataSource
import com.example.dopaminecut2.data.repository.UserRepository
import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.model.DopamineLog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 목표 설정 팝업에서 쓸 데이터 상자
data class AppTarget(val timeLimitMin: Int, val countLimit: Int)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // UserRepository 사용 준비
    private val repository = UserRepository(
        FirebaseDataSource(),
        DataStoreManager(application)
    )

    private val _userNickname = MutableStateFlow<String>("로딩중...")
    val userNickname: StateFlow<String> get() = _userNickname

    // 내 목표 시간 기억용 변수 (HomeFragment에서 변수를 가져가도록 함)
    private val _currentTargetMin = MutableStateFlow(120)
    val currentTargetMin: StateFlow<Int> get() = _currentTargetMin

    private val _currentTargetCount = MutableStateFlow(15)
    val currentTargetCount: StateFlow<Int> get() = _currentTargetCount

    // 대시보드 통계 데이터 (Home, Stats 화면에서 바라보는 곳)
    private val _dailyStats = MutableStateFlow<DailyStatistics?>(null)
    val dailyStats: StateFlow<DailyStatistics?> get() = _dailyStats

    private val _dopamineLogs = MutableStateFlow<List<DopamineLog>>(emptyList())
    val dopamineLogs: StateFlow<List<DopamineLog>> get() = _dopamineLogs

    // 팝업으로 쏠 결과 메시지
    private val _targetSaveEvent = MutableSharedFlow<String>()
    val targetSaveEvent: SharedFlow<String> get() = _targetSaveEvent

    // Repository에서 데이터 가져오기 (실제 Firebase 연동)
    fun fetchDashboardData() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // 로그인이 풀렸거나 안 되어 있으면 멈춤
        if (currentUserId == null) {
            _userNickname.value = "게스트 (로그인 안됨)"
            return
        }

        // 유저 닉네임 및 목표 시간 실시간 감시
        viewModelScope.launch {
            val result = repository.getUserInfo(currentUserId)
            result.onSuccess { user ->
                _userNickname.value = user.nickname
                _currentTargetMin.value = user.targetTimeMin // 앱 켤 때 목표 가져오기
                _currentTargetCount.value = user.targetCount
            }.onFailure { e ->
                _userNickname.value = "에러: ${e.message}"
            }

            try { // DB 바뀌면 실시간 반영
                repository.getUserInfoFlow(currentUserId).collect { user ->
                    _userNickname.value = user.nickname
                    _currentTargetMin.value = user.targetTimeMin
                    _currentTargetCount.value = user.targetCount
                }
            } catch (e: Exception) { }
        }

        // 오늘 날짜를 "YYYYMMDD" 형태(예: "20260608")로 생성함
        val todayDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        // 일일 통계 실시간 감시
        viewModelScope.launch {
            try {
                repository.getDailyStatisticsFlow(currentUserId, todayDate).collect { stats ->
                    _dailyStats.value = stats
                }
            } catch (e: Exception) { }
        }

        // 도파민 로그 실시간 구독
        viewModelScope.launch {
            try {
                repository.getDopamineLogsFlow(currentUserId).collect { logs ->
                    _dopamineLogs.value = logs
                }
            } catch (e: Exception) { }
        }
    }

    // 60% 초과 검사 및 저장
    fun saveNewTarget(timeLimitMin: Int, countLimit: Int, selectedTags: List<String>) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId == null) {
            viewModelScope.launch {
                _targetSaveEvent.emit("로그인 정보가 없습니다. 다시 로그인 해주세요.")
            }
            return
        }

        viewModelScope.launch {
            val stats = _dailyStats.value

            // 모든 플랫폼의 하루 사용 시간을 모두 더함 (초 단위)
            var totalUsedSec = 0L
            stats?.appUsage?.values?.forEach { appUsage ->
                totalUsedSec += appUsage.runTimeSec
            }

            // 60% 검사를 초(Second) 단위로 정밀하게 계산
            val maxAllowedUsedSec = (timeLimitMin * 60) * 0.6

            if (totalUsedSec > maxAllowedUsedSec) {
                // 몇 분 몇 초 썼는지 텍스트로 변환
                val usedMin = totalUsedSec / 60
                val usedSec = totalUsedSec % 60

                _targetSaveEvent.emit("이미 ${usedMin}분 ${usedSec}초를 사용하여 하루 목표 60%를 초과했습니다!")
                return@launch
            }

            try {
                // firebase DB 연동
                repository.updateTargetSettings(currentUserId, timeLimitMin, countLimit, selectedTags)
                // DB 저장 성공 시, 화면의 목표 시간 즉시 변경
                _currentTargetMin.value = timeLimitMin
                _targetSaveEvent.emit("TARGET_SAVE_SUCCESS")
            } catch (e: Exception) {
                _targetSaveEvent.emit("저장 실패: ${e.localizedMessage}")
            }
        }
    }
}