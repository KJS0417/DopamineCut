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

    // 대시보드 통계 데이터 (Home, Stats 화면에서 바라보는 곳)
    private val _dailyStats = MutableStateFlow<DailyStatistics?>(null)
    val dailyStats: StateFlow<DailyStatistics?> get() = _dailyStats

    private val _dopamineLogs = MutableStateFlow<List<DopamineLog>>(emptyList())
    val dopamineLogs: StateFlow<List<DopamineLog>> get() = _dopamineLogs

    // 목표 설정 데이터 (팝업에서 바라보는 곳)
    private val _targetSettings = MutableStateFlow<Map<String, AppTarget>>(emptyMap())
    val targetSettings: StateFlow<Map<String, AppTarget>> get() = _targetSettings

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

        // 유저 닉네임 실시간 감시
        viewModelScope.launch {
            val result = repository.getUserInfo(currentUserId)
            result.onSuccess { user ->
                _userNickname.value = user.nickname
            }.onFailure { e ->
                _userNickname.value = "에러: ${e.message}"
            }

            try {
                repository.getUserInfoFlow(currentUserId).collect { user ->
                    _userNickname.value = user.nickname
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

    // 40% 초과 검사 및 저장
    fun saveNewTarget(timeLimitMin: Int, countLimit: Int, selectedTags: List<String>) {
        // 로그인한 유저ID 가져오기
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // 로그인이 풀리거나 비정상 접근 시 차단
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

            // 초를 분으로 환산
            val totalUsedMin = totalUsedSec / 60

            // 40% 검사 로직
            val maxAllowedUsedMin = timeLimitMin * 0.4
            if (totalUsedMin > maxAllowedUsedMin) {
                // 이미 많이 쓴 상태일 경우 차단
                _targetSaveEvent.emit("이미 ${totalUsedMin}분을 사용하여 하루 목표 시간 40%를 초과했습니다.")
                return@launch
            }

            try {
                // firebase DB 연동
                // Repository를 통해 선택된 5개의 태그, 통합 시간과 횟수를 유저 DB에 덮어씌우기
                repository.updateTargetSettings(currentUserId, timeLimitMin, countLimit, selectedTags)

                // 팝업 닫힘 (성공)
                _targetSaveEvent.emit("TARGET_SAVE_SUCCESS")
            } catch (e: Exception) {
                _targetSaveEvent.emit("저장 실패: ${e.localizedMessage}")
            }
        }
    }
}