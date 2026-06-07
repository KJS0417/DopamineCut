package com.example.dopaminecut2.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dopaminecut2.data.local.DataStoreManager
import com.example.dopaminecut2.data.remote.FirebaseDataSource
import com.example.dopaminecut2.data.repository.UserRepository
import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.model.DopamineLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// 목표 설정 팝업에서 쓸 데이터 상자
data class AppTarget(val timeLimitMin: Int, val countLimit: Int)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // UserRepository 사용 준비
    private val repository = UserRepository(
        FirebaseDataSource(),
        DataStoreManager(application)
    )

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

    // Repository에서 데이터 가져오기
    fun fetchDashboardData(userId: String) {
        viewModelScope.launch {
            // 유저 정보 실시간으로 가져오기 (getUserInfoFlow 활용)
            repository.getUserInfoFlow(userId).collect { user ->
                // TODO: 유저 데이터가 들어오면 차트에 맞게 가공하는 로직 추가
                // (Firestore DB 구조에 따라 다르게 연동)
            }
        }
    }

    // 40% 초과 검사 및 저장
    fun saveNewTarget(platform: String, timeLimitMin: Int, countLimit: Int) {
        viewModelScope.launch {
            val stats = _dailyStats.value

            // 현재까지 사용한 시간 (초 -> 분 단위로 환산)
            val usedSec = stats?.appUsage?.get(platform)?.runTimeSec ?: 0L
            val usedMin = usedSec / 60

            // 이미 목표 시간의 40%를 초과했는지 검사
            val maxAllowedUsedMin = timeLimitMin * 0.4
            if (usedMin > maxAllowedUsedMin) {
                // 40%가 넘었으면 에러 메시지 팝업
                _targetSaveEvent.emit("설정 불가: 이미 새 목표 시간의 40%를 초과하였습니다.")
                return@launch
            }

            try {
                // 통과 시 Repository에 업데이트 요청.
                // (updateTargetSettings 함수 호출)
                repository.updateTargetSettings("임시유저ID", listOf(platform))

                // 화면 데이터 갱신 (임시)
                val currentMap = _targetSettings.value.toMutableMap()
                currentMap[platform] = AppTarget(timeLimitMin, countLimit)
                _targetSettings.value = currentMap

                // 팝업창 닫을 수 있도록 신호 보내기
                _targetSaveEvent.emit("TARGET_SAVE_SUCCESS")
            } catch (e: Exception) {
                _targetSaveEvent.emit("저장 실패: ${e.localizedMessage}")
            }
        }
    }
}