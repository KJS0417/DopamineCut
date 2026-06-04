# 4. 기술적 접근이나 수행 방법 및 사용할 도구

본 프로젝트('DopamineCut')는 Android 클라이언트 내에서 숏폼을 감지 및 제어하는 네이티브 시스템과, 이를 기반으로 파생된 데이터를 처리하는 Web 커뮤니티 플랫폼, 그리고 실시간 동기화를 담당하는 Server/DB가 결합된 아키텍처를 채택하고 있습니다. 

## 4-1. 개발 도구 및 기술 스택

각 기술 스택은 모바일 앱 본연의 모니터링 기능과 웹의 소셜/수익 창출(BM) 기능을 완벽히 분리하고, 이들을 유기적으로 연결하기 위해 선정되었습니다.

* **Client (Android Application)**
    * **IDE:** Android Studio
    * **Language:** Kotlin (Min SDK 26, Target SDK 34)
    * **핵심 라이브러리:** `AccessibilityService` (UI 구조 분석 및 화면 캡처), Kotlin Coroutines (백그라운드 비동기 처리)
* **Web (Community Platform)**
    * **IDE:** VS Code
    * **Language & Framework:** React.js, JavaScript, HTML5, CSS3
    * **핵심 기능:** `COMMUNITY_POST`, `COMMUNITY_COMMENT` 테이블과 연동하여 유저의 당일 점수에 따른 동적 폰트 사이즈 조절 및 광고(확성기) UI 렌더링.
* **Backend Server & Database**
    * **Server:** Node.js (Express 프레임워크)
    * **Database (Firebase):** Firebase Realtime Database 및 Firestore 연계
    * **Push Notification:** Firebase Cloud Messaging (FCM)
    * **데이터 구조 관리:** `USER`, `ROOM`, `ROOM_MEMBER`, `DOPAMINE_LOG`, `USER_INVENTORY` 등의 테이블을 관계형 및 NoSQL의 장점을 살려 혼합 설계하여 실시간 랭킹 연산 및 트랜잭션 처리.
* **AI Engine & Computer Vision**
    * **Vision (OCR):** Google ML Kit Text Recognition API (온디바이스 기반 정제)
    * **AI Classification:** Google Gemini API (프롬프트 기반 텍스트 분석)
* **형상 관리 및 협업 도구**
    * Git, GitHub, VS Code Live Share

---

## 4-2. 수행 방법 및 시스템 아키텍처

DopamineCut의 기능은 3장에서 정의한 핵심 모듈들의 파이프라인과 서버의 7개 테이블이 상호작용하는 형태로 수행됩니다. 전체 흐름은 다음 5단계로 나뉩니다.

### 단계 1: 앱별 맞춤형 숏폼 감지 및 트래킹 (Detection & Tracking)
* **`AppManagerInterface` 기반 다형성 구현:** 플랫폼(Youtube, Instagram, Tiktok)마다 UI 노드 구조가 다르므로, 각 앱에 맞는 Manager 클래스가 `isShortformSection()`, `isAdContent()`, `getVideoIdentifier()`를 오버라이딩하여 실행 상태와 광고 여부를 판별합니다.
* **사용 시간 및 횟수 추적:** `ViewTracker` 모듈이 백그라운드에서 타이머를 가동하여 시청 카운트를 올리며, 설정된 목표치를 초과할 경우 `AppBlockService`의 `isTimeout()`, `isCountout()`을 통해 즉각적인 차단(뒤로가기) 이벤트를 발생시킵니다.

### 단계 2: 비가시적 텍스트 추출 및 정제 (OCR Processing)
* **`OCRProcessor` 구동:** 숏폼 시청이 확인되면, 캡처된 화면(Bitmap)이 기기 내부의 ML Kit로 전달됩니다. `extractTextFromScreen()`을 통해 텍스트를 추출하고, `refineText()`를 호출하여 불필요한 특수문자나 공백, 무의미한 UI 텍스트를 정제하여 AI 분석의 정확도를 높입니다.

### 단계 3: AI 기반 카테고리 분류 및 점수 연산 (Classification & Scoring)
* **`GeminiCategoryClassifier` 연동:** 정제된 텍스트는 프롬프트와 함께 Gemini API로 전송되며(`requestToGemini()`), 9종의 카테고리 중 하나를 반환받습니다.
* **개인화 패널티 적용:** `USER` 테이블의 `restriction_weights`(1~5위 위험 카테고리 가중치)를 참조하여, `applyDopamineScore()` 모듈이 도파민 점수의 차감량을 계산합니다.

### 단계 4: 시스템 데이터 동기화 및 그룹 상호작용 (Sync & Gamification)
* **DB 업데이트 로직:** 차감된 점수와 사용 기록은 `UserDataManager`의 `syncUserData()`를 통해 `USER` 테이블의 `daily_score`와 `DOPAMINE_LOG` 테이블에 즉시 기록됩니다.
* **모임방 실시간 랭킹:** 이 데이터는 `ROOM_MEMBER` 테이블의 `current_rank`에 반영되어 같은 방(`ROOM`)에 접속 중인 유저들에게 실시간으로 공유됩니다. 
* **찌르기 기능:** 유저가 `USER_INVENTORY` 테이블에 보유 중인 '찌르기(POKE)' 아이템을 소진하여 하위 유저를 찌르면, FCM을 통해 즉각적인 푸시 알림이 발송됩니다.

### 단계 5: 웹 커뮤니티 연동 및 인벤토리 비즈니스 로직 적용 (Web & BM)
* **토큰 기반 웹 연동:** 앱 내 커뮤니티 탭 클릭 시, 유저의 UID와 `daily_score`를 파라미터로 하여 독립된 웹 브라우저 인터페이스를 호출합니다.
* **동적 UI 제어:** 웹 서버는 전달받은 점수를 기준으로 `COMMUNITY_POST` 및 `COMMUNITY_COMMENT` 렌더링 시 유저의 글자 크기를 동적으로 제한하거나 칭호를 부여하여 시각적인 재미 요소를 더합니다.
* **광고 시스템 모델링:** 유저가 결제를 통해 획득한 `USER_INVENTORY`의 '확성기(MEGAPHONE)' 아이템을 사용하면, 게시글 생성 시 `is_ad` 값이 TRUE로, `expired_at`이 특정 시간으로 설정됩니다. 이를 통해 당근마켓 모델처럼 피드 상단에 광고를 고정 노출하여 커뮤니티 활성화 및 BM을 실현합니다.
