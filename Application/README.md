# DopamineCut 

본 폴더는 'DopamineCut(도파민컷)' 프로젝트의 구현 내용을 포함하고 있습니다.

패키지 구조도
```
com.example.dopaminecut
 ┣ 📂 auth                 // [1] 로그인, 회원가입 등 인증 관련 UI 및 로직
 ┃ ┣ 📜 LoginActivity     // 로그인 UI 로직
 ┃ ┣ 📜 SignupActivity    // 회원가입 UI 로직
 ┃ ┗ 📜 AuthViewModel     // (UI와 데이터를 연결해주는 역할)
 ┃
┣ 📂 main                 // [2] 앱 실행 시 첫 화면 및 탭(통계 등) 관리
 ┃ ┣ 📜 MainActivity      // 하단 탭(홈, 통계, 커뮤니티, 설정) 화면 전환 제어
 ┃ ┣ 📜 HomeFragment      // 메인 대시보드 (현재 도파민 점수 및 요약 통계 표시)
 ┃ ┣ 📜 StatsFragment     // 추가 통계 화면 (MPAndroidChart 연동, 주간 추이 등)
 ┃ ┣ 📜 TargetSettingDialog // 앱별 목표(시간/횟수) 수정 팝업 UI
 ┃ ┗ 📜 MainViewModel     // 대시보드/통계 데이터 제공 및 목표 설정 저장 로직
 ┃
┣ 📂 settings               // [3] 앱 설정 패키지
 ┃ ┣ 📜 SettingsFragment     // 계정 관리(로그아웃/탈퇴), 필수 권한 상태 확인, 앱 정보 UI
 ┃ ┗ 📜 SettingsViewModel    // 설정 관련 서버 통신 및 로컬 세션 초기화 로직
 ┃
 ┣ 📂 community            // [4] 인앱 소그룹 모임방 및 커뮤니티 내용
 ┃ ┣ 📜 RoomListActivity  // 참여 가능한 그룹 목록 표시
 ┃ ┣ 📜 RoomActivity      // 각 그룹 내의 활동
 ┃ ┗ **구현한 커뮤니티웹과 연결하는 추가적인 클래스 작성**
 ┃
 ┣ 📂 logic                // [5] 앱의 핵심 비즈니스 로직 (AI, OCR, 앱 감지)
 ┃ ┣ 📂 manager            // 플랫폼별 매니저 클래스 모음
 ┃ ┃ ┣ 📜 AppManagerInterface  
 ┃ ┃ ┣ 📜 BaseAppManager  // 실행된 앱의 상태에 따른 로직
 ┃ ┃ ┣ 📜 YoutubeManager  // 각 앱에서의 상태 및 식별자 반환
 ┃ ┃ ┣ 📜 TiktokManager 
 ┃ ┃ ┣ 📜 KakaoTalkManager
 ┃ ┃ ┗ 📜 InstagramManager 
 ┃ ┣ 📂 ai                 // AI 및 텍스트 처리  
 ┃ ┃ ┣ 📜 OCRProcessor    // OCR 모델 처리
 ┃ ┃ ┗ 📜 GeminiCategoryClassifier // Gemini API에 프롬프트와 함께 전달하여 카테고리 분류하기
 ┃ ┗ 📜 ViewTracker        // 화면 타이머 및 추적 제어
 ┃
 ┣ 📂 service              // [6] 안드로이드 백그라운드 서비스
 ┃ ┗ 📜 AppBlockService    // AccessibilityService 상속 클래스로 목표 초과된 앱 실행 및 숏폼 감지시 차단 역할 수행
 ┃
 ┗ 📂 data                 // [7] 데이터 계층 (서버 통신 및 로컬 DB)
   ┣ 📂 model              // 데이터 클래스
   ┃ ┣ 📜 User.kt
   ┃ ┣ 📜 DopamineLog.kt
   ┃ ┗ 📜 Room.kt
   ┣ 📂 repository         // 데이터 저장/불러오기 전담
   ┃ ┗ 📜 UserRepository 
   ┣ 📂 remote             // Firebase 관련 코드 (Firestore, Functions)
   ┗ 📂 local              // 기기 내부 저장소 (Room DB, DataStore)
```

---

### auth 패키지 명세

#### 1. LoginActivity
* **역할:** 사용자에게 이메일/비밀번호 입력 화면을 제공하고 로그인 이벤트를 처리하는 UI 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `binding: ActivityLoginBinding` | 화면 UI 요소를 제어하기 위한 객체 |
| **변수** | `viewModel: AuthViewModel` | 로그인 로직 및 상태를 관리하는 객체 |
| **함수** | `onCreate(savedInstanceState: Bundle?)` | 화면 생성 시 UI 초기화 및 연결 수행 |
| **함수** | `setupListeners()` | 로그인 및 회원가입 버튼 클릭 이벤트 정의 (클릭 시 `viewModel.login()` 호출) |
| **함수** | `observeViewModel()` | `viewModel`의 상태를 통해 로그인 결과를 확인하여 화면 전환 또는 에러 표시 |

<br>

#### 2. SignupActivity
* **역할:** 신규 유저의 정보(이메일, 비밀번호, 닉네임 등)를 입력받는 회원가입 UI 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `binding: ActivitySignupBinding` | 화면 UI 요소를 제어하기 위한 객체 |
| **변수** | `viewModel: AuthViewModel` | 회원가입 로직 및 상태를 관리하는 객체 |
| **함수** | `onCreate(savedInstanceState: Bundle?)` | 화면 생성 시 UI 초기화 및 연결 수행 |
| **함수** | `setupListeners()` | 가입 완료 버튼 클릭 시 입력값을 검증하고 가입 요청(`viewModel.signup()` 호출) |
| **함수** | `observeViewModel()` | 회원가입 성공 여부 확인 후 성공 시 메시지를 띄우고 화면을 종료(`finish()` 호출) |

<br>

#### 3. AuthViewModel
* **역할:** UI에서 전달받은 데이터를 바탕으로 Firebase 인증을 요청하고, 그 결과 상태를 보관하여 UI에 전달하는 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `isLoading: StateFlow<Boolean>` | 현재 서버 통신(로딩) 중인지 여부를 나타내는 상태 변수 |
| **변수** | `uiEvent: SharedFlow<String>` | 화면(Activity)으로 전달할 결과 메세지 및 이벤트 알림 |
| **함수** | `login(email: String, pw: String)` | 상태를 로딩(`true`)으로 변경 후 `UserRepository`에 로그인 요청하고, 결과에 따라 상태 갱신 |
| **함수** | `signup(email: String, pw: String, nickname: String)`| `UserRepository`에 회원가입 요청 시, **초기값(`restrictions: []`, `inventory: {poke: 0, megaphone: 0}`)을 생성**하여 `users` 컬렉션에 적재 |
| **함수** | `validateInput(email: String, pw: String): Boolean` | 이메일 및 비밀번호 형식 등 회원가입 요청 전 입력값 유효성 내부 검사 로직 |

<br>

---

### main 패키지 명세

#### 1. MainActivity
* **역할:** 앱의 기본 뼈대가 되는 화면으로, 하단 네비게이션 바를 통해 홈(Home), 통계(Stats), 커뮤니티(Community), 설정(Settings) **4개의 화면(탭)** 간의 화면 전환을 제어하는 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `binding: ActivityMainBinding` | 하단 네비게이션 및 화면 컨테이너 등 UI를 제어하기 위한 객체 |
| **함수** | `onCreate(savedInstanceState: Bundle?)` | 화면 생성 시 UI 초기화 및 첫 화면(`HomeFragment`)을 연결하는 함수 |
| **함수** | `setupBottomNavigation()` | 하단 탭 클릭 이벤트 정의 (선택된 메뉴에 따라 `HomeFragment`, `StatsFragment`, `CommunityFragment`, `SettingFragment`로 화면 교체 수행) |

<br>

#### 2. HomeFragment
* **역할:** 앱의 메인 화면으로, 일일 현황 통계(카테고리 비율, 앱 사용 랭킹)를 제공하는 대시보드 UI 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `binding: FragmentHomeBinding` | 대시보드 UI 요소를 제어하기 위한 객체 |
| **변수** | `viewModel: MainViewModel` | 유저의 실시간 점수 및 로그, 당일 통계 데이터를 관리하는 객체 |
| **함수** | `onCreateView(...)` | 화면 생성 시 UI 초기화 및 연결 수행 |
| **함수** | `onViewCreated(...)` | 뷰 생성 직후 UI 세팅 및 데이터 감시(`observeDashboardData()`) 호출 |
| **함수** | `observeDashboardData()` | `dopamine_logs`와 `daily_statistics` 데이터를 감시하여 점수 갱신 및 하위 차트 함수 호출 |
| **함수** | `initPieChart()` | `dopamine_logs`의 `category` 필드를 가공하여 **오늘의 숏폼 카테고리 비율** 렌더링 (Pie Chart) |
| **함수** | `initHorizontalBarChart()` | `daily_statistics`의 `app_usage.{platform}.run_time_sec`를 가공하여 **오늘의 앱 사용 시간 랭킹** 렌더링 (Horizontal Bar) |

<br>

#### 3. TargetSettingDialog
* **역할:** 홈 화면에서 호출되어 화면 위로 뜨는 팝업 클래스로, 사용자에게 앱별(유튜브, 인스타 등) 목표 사용 시간과 숏폼 시청 개수를 입력받는 UI 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `binding: DialogTargetSettingBinding` | 목표 입력창(EditText) 및 저장/취소 버튼 등 팝업 UI 요소를 제어하기 위한 객체 |
| **변수** | `viewModel: MainViewModel` | 입력된 설정값을 검증하고 DB에 저장 요청을 보내기 위해 `HomeFragment`와 뷰모델을 공유 |
| **함수** | `onViewCreated(...)` | 팝업 생성 직후 유저의 기존 목표 설정값을 불러와 입력창에 기본값으로 세팅 |
| **함수** | `setupListeners()` | '저장' 버튼 클릭 시 입력값을 뷰모델로 전달하여 저장 요청(`viewModel.saveNewTarget(...)` 호출) |
| **함수** | `observeSaveResult()` | 뷰모델의 저장 결과(성공 또는 40% 초과 에러 등)를 감시하여 팝업을 닫거나 에러 메시지 표기 |

<br>

#### 4. MainViewModel (목표 설정 로직 추가)
* **역할:** 대시보드와 통계 데이터뿐만 아니라, 팝업에서 입력한 **목표 설정값의 유효성(40% 초과 여부 등)을 검사하고 DB에 동기화**하는 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `targetSettings: StateFlow<Map<String, AppSetting>>` | 각 플랫폼별 현재 설정된 목표 시간 및 횟수 데이터를 쥐고 있는 상태 변수 |
| **변수** | `targetSaveEvent: SharedFlow<String>` | 목표 저장 성공 또는 변경 불가(에러) 메시지를 팝업(`TargetSettingDialog`)으로 전달하는 이벤트 |
| **함수** | `saveNewTarget(platform: String, timeLimit: Int, countLimit: Int)` | 변경하려는 앱의 당일 사용량이 **목표 시간의 40%를 초과했는지 검사** 후, 통과 시 DB에 새로운 목표값 업데이트 요청 |

<br>

#### 5. StatsFragment
* **역할:** 누적된 데이터를 바탕으로, 주간 추세 및 심층 분석 통계(취약 시간대, 앱 사용 목적, 달성일 등)를 제공하는 UI 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `binding: FragmentStatsBinding` | 심층 분석 차트 컴포넌트(Bar, Line 등)와 달력 UI를 제어하기 위한 객체 |
| **변수** | `viewModel: MainViewModel` | 심층 통계 렌더링에 필요한 누적/과거 데이터를 불러오는 객체 |
| **함수** | `onCreateView(...)` | 화면 생성 시 UI 초기화 및 연결 수행 |
| **함수** | `onViewCreated(...)` | 차트 컴포넌트 초기 세팅 및 데이터 감시(`observeStatisticsData()`) 호출 |
| **함수** | `observeStatisticsData()` | 누적 데이터 수신 완료 시 하위 차트 및 UI 렌더링 함수들을 순차적으로 호출 |
| **함수** | `initBarChart()` | `daily_statistics`의 `app_usage.{platform}.shortform_count` 총합을 가공하여 **주간 숏폼 시청 추이** 렌더링 (Bar Chart) |
| **함수** | `initLineChart()` | `dopamine_logs`의 `created_at`과 `deducted_score`를 분석하여 **취약 시간대** 렌더링 (Line Chart) |
| **함수** | `initDoughnutChart()` | `daily_statistics`의 앱별 `run_time_sec` 대비 `shortform_time_sec` 비율을 가공하여 **앱 사용 목적 분석** 렌더링 (Doughnut Chart) |
| **함수** | `initStreakUI()` | `daily_statistics`의 `daily_score`를 판별하여 **목표 달성일 확인** 캘린더 아이콘 표기 |

<br>

---

### settings 패키지 명세서

#### 1. SettingsFragment
* **역할:** 하단 네비게이션 바의 '설정' 탭을 눌렀을 때 나타나는 화면으로, 계정 관리(로그아웃/탈퇴), 필수 권한 상태 확인, 앱 정보 등을 관리하는 일반 설정 UI 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `binding: FragmentSettingsBinding` | 로그아웃 버튼, 권한 스위치, 버전 텍스트 등 화면 UI 요소를 제어하기 위한 객체 |
| **변수** | `viewModel: SettingsViewModel` | 계정 로그아웃, 탈퇴 등 서버와 통신하는 비즈니스 로직을 관리하는 객체 |
| **함수** | `onCreateView(...)` | 화면 생성 시 UI 초기화 및 연결 수행 |
| **함수** | `onViewCreated(...)` | 뷰 생성 직후 현재 앱 버전 표기 및 권한 허용 상태를 체크하여 UI 세팅 |
| **함수** | `setupListeners()` | 로그아웃, 회원탈퇴, 이용약관 보기, 권한 설정 이동 등의 클릭 이벤트 정의 |
| **함수** | `checkPermissionsStatus()` | 현재 기기에서 '접근성 권한' 및 '다른 앱 위에 표시' 권한이 허용되어 있는지 검사하는 내부 함수 |
| **함수** | `observeViewModel()` | 뷰모델의 로그아웃/탈퇴 처리 결과를 감시하여 성공 시 `LoginActivity`로 화면 전환 처리 |

<br>

#### 2. SettingsViewModel
* **역할:** 설정 화면에서 발생하는 계정 관련 요청(로그아웃, 회원탈퇴)을 처리하고 로컬 기기의 유저 세션을 초기화하는 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `uiEvent: SharedFlow<String>` | 로그아웃/탈퇴 성공 여부나 에러 메시지를 화면(Fragment)으로 전달하는 이벤트 알림 |
| **함수** | `logout()` | Firebase Auth 세션을 종료하고 DataStore(로컬)에 저장된 자동 로그인 토큰/유저 정보를 삭제 |
| **함수** | `withdrawAccount()` | Firebase Auth에서 유저 계정을 영구 삭제하고, Firestore `users` 컬렉션의 데이터도 파기 요청 |

---

### community 패키지 명세서

---

### logic 패키지 명세서

#### 1. manager 패키지 (플랫폼별 숏폼 감지 및 카운팅, 실행 시간 기록 )
* **역할:** 유튜브(쇼츠), 인스타(릴스), 틱톡, 카카오톡(펑) 등 각기 다른 앱의 화면 구조를 분석하여 **"지금 사용자가 숏폼을 보고 있는가?"**를 정확히 짚어내는 매니저 클래스 모음

| 파일/클래스 | 구분 | 이름 (Name) | 설명 (Description) |
| :--- | :---: | :--- | :--- |
| **AppManagerInterface** | 
| | **변수** | `packageName` | 해당 앱의 고유 패키지 이름 (예: `com.google.android.youtube`) |
| | **변수** | `platformName` | 화면 UI 및 통계에 보여줄 앱 이름 (예: `"YouTube"`) |
| | **함수** | `isShortformSection(rootNode)` | 현재 화면(UI 노드)이 숏폼인지 판별하는 필수 구현 규칙 |
| | **함수** | `getVideoIdentifier(rootNode)` | 영상의 고유 식별자를 추출하는 규칙 (중복 시청 데이터 누적 방지용) |
| | **함수** | `isAdContent(rootNode)` | 현재 화면이 광고 콘텐츠인지 판별하여 무의미한 감지를 방지하는 규칙 |
| **BaseAppManager** |
| | **변수** | `watchedVideoIds` | 중복 카운트 방지를 위해 지금까지 시청한 영상의 식별자(ID)들을 임시 저장해두는 목록(Set) |
| | **함수** | `startShortsTimer()` | 숏폼 진입이 확인되면 타이머를 시작하여 체류 시간을 누적하는 공통 로직 |
| | **함수** | `trackVideoCount(rootNode)` | `getVideoIdentifier`로 추출한 식별자가 `watchedVideoIds`에 없으면 숏폼 시청 횟수를 +1 하고 목록에 추가, 이미 있으면 카운트하지 않는 횟수 측정 로직 |
| **YoutubeManager**<br>**TiktokManager**<br>**KakaoTalkManager**<br>**InstagramManager** |
| | **함수** | `인터페이스 상속 구현` | `isShortformSection`, `getVideoIdentifier` 등 `AppManagerInterface`의 필수 규칙들을 각 앱의 특성에 맞게 오버라이딩하여 구현 |
| | **함수** | `[내부 자율 구현 함수]`| 각 플랫폼의 특수성을 해결하기 위해 필요한 추가 함수 및 로직은 담당 인원이 작성 |

<br>


#### 2. ai 패키지 (텍스트 추출 및 카테고리 분류)
* **역할:** 화면에 떠 있는 글자를 읽어내고, 구글 AI(Gemini)를 통해 해당 숏폼이 어떤 카테고리(예: 게임, 뷰티, 연예 등)인지 똑똑하게 분류하는 클래스 모음

| 파일/클래스 | 구분 | 이름 (Name) | 설명 (Description) |
| :--- | :---: | :--- | :--- |
| **OCRProcessor** | **변수** | `ocrEngine` | 화면의 이미지를 글자로 변환해 주는 비전(Vision) 인식 객체 |
| | **함수** | `extractText(bitmap)` | 숏폼의 제목/해시태그 영역을 캡처한 이미지(Bitmap)를 던져주면, 텍스트(String)로 추출하여 반환하는 함수 |
| **GeminiPrompt** | **변수** | `PROMPT_TEMPLATE` | Gemini가 엉뚱한 대답을 하지 않도록 사전 프롬프트 |
| | **함수** | `classifyCategory(text)` | OCR로 뽑아낸 텍스트를 프롬프트와 조합해 Gemini API로 전송하고, 분석된 '카테고리 결과값'을 반환받는 함수 |

<br>

#### 3. ViewTracker
* **역할:** 사용자가 앱을 켜고 끌 때마다 실시간으로 화면 상태를 추적하고, 타이머를 돌리며 DB에 사용량을 기록함.

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `currentAppManager` | 현재 화면에 켜져 있는 앱이 무엇인지에 따라 교체되는 매니저 객체 (예: 유튜브 켜면 `YoutubeManager`로 변신) |
| **변수** | `usageTimer` | 앱 총 실행 시간 및 숏폼 체류 시간을 초(sec) 단위로 측정하는 타이머 |
| **함수** | `onAppChanged(packageName)` | 사용자가 다른 앱으로 화면을 전환할 때 호출되어, 해당 앱에 맞는 `AppManager`로 교체해 주는 함수 |
| **함수** | `updateAppUsageData()` | 타이머로 측정된 시간과 숏폼 시청 횟수를 `daily_statistics` DB의 `app_usage` 필드에 실시간(또는 주기적)으로 누적 업데이트 |
| **함수** | `triggerOCRAndAI()` | 숏폼 시청이 감지되었을 때, `OCRProcessor`와 `GeminiCategoryClassifier`를 순차적으로 호출하여 카테고리를 얻어내고 `dopamine_logs` DB에 로그를 남기는 연결 함수 |

<br>

---

### service 패키지 명세서

#### 1. AppBlockService
* **역할:** 안드로이드의 `AccessibilityService`(접근성 서비스)를 상속받아, 앱이 백그라운드에 있을 때도 화면 변화를 감지하고, 유저가 목표를 초과하면 차단하는 클래스

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **변수** | `viewTracker: ViewTracker` | 화면 변화(앱 전환, 스크롤 등)를 전달하여 실제 숏폼 여부와 시간을 계산하도록 지시하는 객체 |
| **함수** | `onServiceConnected()` | 접근성 권한이 허용되고 서비스가 최초 실행될 때 호출되며, `viewTracker` 등 초기 세팅을 수행 |
| **함수** | `onAccessibilityEvent(event)` | 화면이 바뀌거나(앱 전환), 스크롤을 내릴 때마다 안드로이드 OS가 호출해 주는 함수. 이 이벤트를 가공하여 `viewTracker.onAppChanged()` 등을 호출 |
| **함수** | `executeShortformBlock()` | **숏폼 제한 초과 시** 현재 화면이 숏폼(`isShortformSection == true`)일 경우, 뒤로 가기(`GLOBAL_ACTION_BACK`)를 실행하여 숏폼 시청만 차단 |
| **함수** | `executeAppBlock()` | **앱 사용 시간 초과 시** 숏폼 여부와 상관없이 홈 이동(`GLOBAL_ACTION_HOME`)을 연속 실행하여 해당 앱 자체의 실행을 차단 |
| **함수** | `showWarningToast(message)` | "숏폼 시청 횟수를 초과하여 차단되었습니다" 등의 짧은 안내 메시지를 띄우는 함수 |
| **함수** | `onInterrupt()` | 시스템에 의해 접근성 서비스가 강제로 끊겼을 때 자원을 해제하고 타이머를 멈추는 안전장치 함수 |

<br>

---

### data 패키지 명세서 (데이터 계층)

* **역할:** 앱에서 발생하고 소비되는 모든 데이터(유저 정보, 통계, 숏폼 로그 등)를 로컬 기기에 저장하거나 Firebase 서버와 동기화하는 것을 전담하는 계층

#### 1. model 패키지 (데이터 클래스)
* **역할:** 기획된 DB 구조(Firestore 문서)와 안드로이드 코드 사이에서 데이터를 주고받기 위해 1:1로 매칭시켜 놓은 데이터 상자(클래스) 모음

| 파일/클래스 | 설명 (Description) |
| :--- | :--- |
| `User` | 유저 고유 ID, 이메일, 닉네임, 차단 카테고리(`restrictions`), 아이템(`inventory`)을 담는 객체 |
| `Room` | 모임방 고유 ID, 초대 코드, 방장 정보, 실시간 멤버 상태 및 점수(`members` 맵)를 담는 객체 |
| `DopamineLog` | 개별 숏폼 시청 기록(플랫폼, OCR 카테고리, 지속 시간, 감점 내역)을 담는 객체 |
| `DailyStatistics` | 하루 동안 누적된 총 도파민 점수와 앱별(유튜브/틱톡 등) 사용 시간(`run_time_sec`), 숏폼 시청 횟수 등을 담는 객체 |

<br>

#### 2. repository 패키지
* **역할:** ViewModel이나 Service가 데이터를 요구할 때, "서버(Firebase)에서 가져올지, 로컬 기기에서 가져올지"를 내부적으로 판단하여 깔끔하게 결과만 건네주는 단일 진입점

| 구분 | 이름 (Name) | 설명 (Description) |
| :---: | :--- | :--- |
| **클래스** | `UserRepository` | 유저 정보 조회, 목표 저장, 통계 업데이트 등 앱 전반의 데이터 흐름을 조율하는 메인 저장소 |
| **함수** | `getUserInfo(userId)` | 앱 실행 시 유저의 기본 정보와 인벤토리 현황을 불러오는 함수 |
| **함수** | `updateTargetSettings()` | 유저가 팝업에서 수정한 앱별 목표(시간/횟수)를 로컬 및 서버에 동기화하는 함수 |
| **함수** | `addDopamineLog(log)` | `ViewTracker`가 전달한 숏폼 시청 기록과 AI 카테고리 분류 결과를 서버 DB에 누적 업로드 |
| **함수** | `incrementAppUsage()` | 안드로이드 백그라운드에서 측정된 앱 실행 시간과 숏폼 횟수를 `daily_statistics` DB에 실시간 합산(`FieldValue.increment`) 처리 |

<br>

#### 3. remote & local 패키지
* **역할:** Firestore에 직접 접근하거나 기기 내부에 데이터를 쓰고 지우는 실무(네트워크/DB) 코드가 위치하는 곳

| 패키지 | 파일/클래스 | 설명 (Description) |
| :--- | :--- | :--- |
| **remote** | `FirebaseDataSource` | Firestore 컬렉션(users, rooms 등) 접근, 데이터 읽기/쓰기 및 실시간 랭킹 리스너(`SnapshotListener`) 세팅을 전담하는 클래스 |
| **local** | `DataStoreManager` | 자동 로그인용 유저 세션이나, '앱별 목표 설정값' 등을 기기 내부에 저장하는 클래스 |
| **local** | `AppDatabase` | 오프라인 상태(인터넷 끊김)일 때 측정된 체류 시간이나 로그를 임시 저장했다가 통신이 회복되면 서버로 밀어넣기 위한 객체 |

---
