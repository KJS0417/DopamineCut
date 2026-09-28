package com.example.dopaminecut2.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.dopaminecut2.R
import com.example.dopaminecut2.data.local.DataStoreManager
import com.example.dopaminecut2.data.model.DopamineLog
import com.example.dopaminecut2.data.remote.FirebaseDataSource
import com.example.dopaminecut2.data.repository.UserRepository
import com.example.dopaminecut2.logic.ViewTracker
import com.example.dopaminecut2.logic.manager.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class AppBlockService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var userRepository: UserRepository
    private lateinit var viewTracker: ViewTracker

    private lateinit var windowManager: WindowManager
    private var blockView: android.view.View? = null
    private var isOverlayShowing = false

    // 홈 버튼 누를 시, 발생하는 이벤트를 잠깐 무시하기 위한 용도의 변수
    private var lastHomeActionTime = 0L

    private val appManagers = mapOf(
        "com.google.android.youtube" to YoutubeManager(),
        "com.instagram.android" to InstagramManager(),
        "com.kakao.talk" to KakaotalkManager(),
        "com.zhiliaoapp.musically" to TiktokManager()
    )

    private var currentAppManager: AppManagerInterface? = null

    // 차단을 위해 실시간으로 저장해둘 변수
    private var targetTimeSec = 0L
    private var targetCount = 0L
    private var currentUsedSec = 0L
    private var currentShortformCount = 0L
    private var currentAppStartTime = 0L // 앱 켠 시간 기억하기

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AppBlockService", "접근성 서비스 실행됨.")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        userRepository = UserRepository(FirebaseDataSource(), DataStoreManager(applicationContext))
        observeUserData() // 목표 실시간 감시

        // 5초 시청 달성 시 화면에 알림 띄우기
        // TODO : 확인 필요
        viewTracker = ViewTracker(
            viewThresholdMs = 5000L,
            onValidViewCounted = { platform, videoId, durationSec ->
                Toast.makeText(
                    this@AppBlockService,
                    "숏폼 1회 ($durationSec 초) 기록됨.",
                    Toast.LENGTH_SHORT
                ).show()

                // 실제 본 시간을 전달 (숏폼을 봤으니 true)
                saveUsageToFirebase(platform, durationSec, true)
            }
        )
    }

    // 실시간 데이터 구독 함수
    private fun observeUserData() {
        serviceScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val todayDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

            // 내 목표(시간, 횟수) 가져오기
            launch {
                userRepository.getUserInfoFlow(userId).collect { user ->
                    targetTimeSec = user.goal.appTimeLimitMin * 60L // 분을 초로 변환
                    targetCount = user.goal.shortformLimitCount.toLong()
                }
            }

            // 오늘 하루 전체 앱 사용량 합산하기
            launch {
                userRepository.getDailyStatisticsFlow(userId, todayDate).collect { stats ->
                    if (stats != null) {
                        var totalSec = 0L
                        var totalCount = 0L
                        stats.appUsage.values.forEach { usage ->
                            totalSec += usage.runTimeSec
                            totalCount += usage.shortformCount
                        }
                        currentUsedSec = totalSec
                        currentShortformCount = totalCount
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 오버레이가 이미 떠 있는 상태일 때
        if (isOverlayShowing) {
            val activePackage = rootInActiveWindow?.packageName?.toString()

            // 1. 여전히 차단 대상 앱이 켜져 있다면
            // 팝업창 유지 & 팝업 재생성 막기
            if (activePackage == null || appManagers.containsKey(activePackage)) {
                return
            }

            // 2. 차단 앱이 아닌 곳으로 넘어갔다면
            // 팝업 내리기
            hideBlockOverlay()
            return
        }

        var packageName = event.packageName?.toString() ?: return

        // 패키지명으로 깜빡임 방지
        if (packageName == "com.example.dopaminecut2") return

        // 그러나, 홈으로 이동 하는 중 2초(2000L) 동안은 감지하지 않도록 함. (홈 이동하면서 이벤트가 바뀌면서 발생하는 오류 방지용임)
        if (System.currentTimeMillis() - lastHomeActionTime < 2000L) {
            return
        }

        // 상단바, 알림창, 키보드 뜰 때는 화면 무시
        val ignorePackages = listOf(
            // 시계/배터리/알림창(상단바) 패키지
            "com.android.systemui",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin"
        )
        if (ignorePackages.contains(packageName)) return

        // 현재 화면의 전체를 덮는 UI 확인하기
        val activePackage = rootInActiveWindow?.packageName?.toString()

        // A: 지금 화면을 덮고 있는 것이 차단 오버레이라면...
        // 유저가 홈 버튼을 누르기 전까지 절대 팝업을 내리지 말도록 하기
        if (activePackage == "com.example.dopaminecut2") return

        // B: 백그라운드 이벤트가 발생했다면...
        // 이벤트 패키지와 화면의 정보가 다르면, 백그라운드 이벤트이기에 무시, 진짜 정보로 덮어 씌우기
        if (activePackage != null && activePackage != packageName) {
            packageName = activePackage
        }

        val newAppManager = appManagers[packageName]

        if (newAppManager != currentAppManager) {
            if (currentAppManager != null && currentAppStartTime > 0) {
                val totalAppUsedSec = (System.currentTimeMillis() - currentAppStartTime) / 1000
                if (totalAppUsedSec > 0) {
                    saveUsageToFirebase(currentAppManager!!.platformName, totalAppUsedSec, false)
                }
            }

            currentAppManager = newAppManager
            viewTracker.onScreenChanged(false, null, false, "")

            // 새로 켠 앱의 타이머 시작
            if (currentAppManager != null) {
                currentAppStartTime = System.currentTimeMillis()
            }
        }

        if (currentAppManager == null) {
            hideBlockOverlay()
            return
        }

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            // 화면 정보를 못 가져왔으면 차단창 숨긴 후 대기 (버그 방지)
            hideBlockOverlay()
            return
        }

        val isShortform = currentAppManager!!.isShortformSection(rootNode)
        val isAd = currentAppManager!!.isAdContent(rootNode)
        val videoId = currentAppManager!!.getVideoIdentifier(rootNode)

        // 어떤 앱이, 어떤 ID를 가져오고 있는지 확인
        Log.d("TEST_LOG", "[Service] 앱: ${currentAppManager!!.platformName} | 비디오ID: $videoId")

        // 화면을 알아내면, 목표 초과 검사 시작
        if (isTargetExceeded()) {
            // 카카오톡일 경우의 예외 로직
            if (currentAppManager is KakaotalkManager) {
                if (isShortform) {
                    executeAppBlock() // 카톡인데 숏폼 화면이면 차단
                    return
                } else {
                    hideBlockOverlay() // 카톡인데 채팅방/친구창이면 차단 해제
                }
            } else {
                // 유튜브, 인스타, 틱톡 전체 차단
                executeAppBlock()
                return
            }
        } else {
            // 목표를 아직 안 넘었으면 팝업만 숨김
            hideBlockOverlay()
        }

        viewTracker.onScreenChanged(
            isShortform = isShortform,
            videoId = videoId,
            isAd = isAd,
            platform = currentAppManager!!.platformName
        )
    }

    // 차단 조건 검사 함수
    private fun isTargetExceeded(): Boolean {
        // 목표가 0이면 아직 설정 안한 것이므로 차단 X
        if (targetTimeSec == 0L || targetCount == 0L) return false

        // 사용 시간이 목표를 넘었거나 or 숏폼 횟수가 목표를 넘었으면 true(차단)
        return (currentUsedSec >= targetTimeSec) || (currentShortformCount >= targetCount)
    }

    // TODO : 접근성 서비스 자체의 기능, 소프트 도파민 디톡스에는 어울리지 않으며 반드시 수정 필요함.


    /**
     * 과거 코드
     * 차단 실행 시 홈 화면으로 튕기도록 설정
    private fun executeAppBlock() {
        Toast.makeText(this, "도파민 목표 초과 : 앱이 차단되었습니다.", Toast.LENGTH_SHORT).show()
        performGlobalAction(GLOBAL_ACTION_HOME) // 홈 화면 으로 강제 이동
    }
    */

    // 차단 실행 : 오버레이 띄우기 방식
    private fun executeAppBlock() {
        if (isOverlayShowing) return // 오버레이가 이미 떠있으면 무시

        // 오버레이가 뜨기 직전, 지금까지 쓴 시간을 정산 후 타이머 종료
        if (currentAppManager != null && currentAppStartTime > 0L) {
            val totalAppUsedSec = (System.currentTimeMillis() - currentAppStartTime) / 1000
            if (totalAppUsedSec > 0) {
                // 숏폼이 아니기 때문에 isShortform = false 로 기록함
                saveUsageToFirebase(currentAppManager!!.platformName, totalAppUsedSec, false)
            }
            // 타이머를 0으로 만들며, 오버레이 차단 화면을 보고 있는 동안 시간을 오르지 않도록 막기
            currentAppStartTime = 0L
        }
        
        showBlockOverlay()
    }

    // 화면에 차단 팝업 띄우기
    private fun showBlockOverlay() {
        if (isOverlayShowing) return // 이미 떠있으면 무시

        val inflater = android.view.LayoutInflater.from(this)
        blockView = inflater.inflate(R.layout.overlay_block, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            // 유저 권한 승인 없이 바로 띄우는 방법
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        // 버튼 누르면 홈으로 튕기며, 오버레이 제거
        blockView?.findViewById<android.widget.Button>(R.id.btn_go_home)?.setOnClickListener {
            lastHomeActionTime = System.currentTimeMillis() // 버튼 누른 시간 기록 시작
            hideBlockOverlay() // 오버레이 먼저 숨기기
            performGlobalAction(GLOBAL_ACTION_HOME) // 홈으로 이동
        }

        windowManager.addView(blockView, params)
        isOverlayShowing = true
    }

    // 차단 팝업 숨기기
    private fun hideBlockOverlay() {
        if (!isOverlayShowing) return
        try {
            blockView?.let {
                windowManager.removeView(it)
                blockView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isOverlayShowing = false
    }

    // TODO : saveDataToFirebase - durationSec과 saveGeneralTimeToFirebase - run_time_sec 체크.

    // 두 값은 각각 숏폼 총 사용 시간, 앱 자체 총 사용 시간으로 둘 다 데이터에 더해진다.
    // 앱 자체 총 사용 시간 안에 숏폼 사용 시간이 내장되어야 하므로, 둘 다 더해지는 개념이 아니다. (수정 필요)

    // 데이터 저장 함수 통합
    private fun saveUsageToFirebase(platform: String, durationSec: Long, isShortform: Boolean) {
        if (durationSec <= 0) return

        // 파이어베이스 DB 통신을 하기 전, 폰 변수부터 즉시 증가.
        if (isShortform) {
            currentShortformCount += 1L
        }
        currentUsedSec += durationSec   // 사용 시간 즉시 합산

        serviceScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val todayDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

            try {
                // 1. 통계 데이터 업데이트 (숏폼 여부에 따라 분배 계산)
                userRepository.incrementAppUsage(
                    userId = userId,
                    date = todayDate,
                    platform = platform,
                    durationSec = durationSec,
                    isShortform = isShortform
                )

                // 2. 숏폼일 경우에만 AI 분석용 로그 추가
                if (isShortform) {
                    val log = DopamineLog(
                        userId = userId,
                        platform = platform,
                        category = "UNKNOWN", // 나중에 제미나이? 같은 AI로 채울 곳임
                        durationSec = durationSec
                    )
                    userRepository.addDopamineLog(log)
                }
            } catch (e: Exception) {
                Log.e("AppBlockService", "Firebase 저장 실패", e)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        viewTracker.release()
        serviceScope.cancel()
    }
}