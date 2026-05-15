# 3. 소프트웨어 기술
## 3.1 모듈별 세부 설계
### 3.1.1 앱별 숏폼 감지 모듈
#### [1] AppManagerInterface
  숏폼을 인식할 각 앱(Youtube, Instagram, Tiktok, Kakaotalk)의 UI 구조가 다르므로, 공통된 인터페이스를 상속받아
  개별 Manager 클래스(ex. YoutubeManager)에서 세부 로직을 오바리이딩하여 구현한다.
  #### 변수: packageName (실행을 확인할 앱 패키지명), platformName (UI 표출용 이름)
  | 함수명 | 입력 | 처리 | 출력 |
  | :--- | :--- | :---: | :---: |
  | isShortformSection | AccessibilityNodeInfo | 현재 화면의 UI 구조를 분석하여 현재 사용자가 숏폼 시청인지 판별한다. | Boolean(숏폼 여부 반환) |
  | isAdContent | AccessibilityNodeInfo | 광고 영상의 특징이 현재 화면의 UI 구조에 포함되어 있는지 탐색한다. | Boolean(광고 여부 반환) |
  | getVideoIdentifier | AccessibilityNodeInfo | 각 시청한 영상들을 구분할 영상별 고유 식별자를 생성 및 중복 시청 여부를 확인한다. | String(영상 식별자 반환) |


#### [2] ViewTracker
  | 함수명 | 입력 | 처리 | 출력 |
  | :--- | :--- | :---: | :---: |
  | startTracking | 없음 | 앱 실행 시간 및 숏폼 시청 카운트를 위해 백그라운드에서 타이머 시작한다. | 없음 |
  | stopTracking | 없음 | 활성화된 타이머를 중단하고 임시 데이터를 초기화한다. | 없음 |
  | isAppRunning | packageName: string | 전달받은 패키지명이 현재 포그라운드에서 실행 중인지 검사한다. |

  
#### [3] AppBlockService
  | 함수명 | 입력 | 처리 | 출력 |
  | :--- | :--- | :---: | :---: |
  | isTimeout | packageName: String | 실행 중인 특정 앱의 총 사용 시간이 사용자가 설정한 목표 시간을 초과했는지 판별한다. | Boolean (시간 초과 여부) |
  | isCountout | packageName: String | 해당 앱의 숏폼 시청 횟수를 사용자가 목표 횟수를 초과했는지 확인하고, 초과 시 뒤로가기 수행한다. | Boolean (횟수 초과 여부)

  
### 3.1.2 텍스트 추출 및 정제 모듈
#### [1] OCRProcessor
  | 함수명 | 입력 | 처리 | 출력 |
  | :--- | :--- | :---: | :---: |
  | extractTextFromScreen | screenshot: Bitmap | AccesilibtyService를 통해 캡처된 화면 이미지를 Google ML Kit Text Recognition 엔진에 입력하여 문자열을 추출한다. | String(추출된 텍스트) |
  | refineText | rawText: String | 추출된 텍스트에서 특수문자를 제거하고, UI의 단어와 불필요한 공백을 정제한다. | String(정제된 텍스트) |
### 3.1.3 카테고리 분류 모듈
#### [1] GeminiCategoryClassifier
  | 함수명 | 입력 | 처리 | 출력 |
  | :--- | :--- | :---: | :---: |
  | requestToGemini | refinedText: String | 정제된 텍스트 데이터와 프롬프트를 결합하여 Gemini APi로 전송하고, 반환된 응답에서 카테고리명을 파싱한다. | String (카테고리명) |
  | applyDopamineScore | category: String | 사용자가 설정한 우선순위 카테고리를 조회하여 도파민 점수 차감을 연산한다. | Int(연산 후 도파민점수) |
### 3.1.4 시스템 데이터 동기화 및 관리
#### [1] UserDataManager
  | 함수명 | 입력 | 처리 | 출력 |
  | :--- | :--- | :---: | :---: |
  | syncUserData | uid: String, score: Int, usageData: Object | 기기에서 갱신된 도파민 점수 및 앱별 사용 시간, 시청 횟수를 서버 데이터베이스에 업데이트한다. | Boolean(동기화 성공 여부) |
  | updateCategoryPriority | category: String, weight: Float | 사용자가 변경한 제한 카테고리 우선순위 가중치를 로컬 및 서버에 저장한다. | Boolean(업데이트 성공 여부) |

# 이어서 서버, 커뮤니티 부분 작성
### ex) 3.1.5 Auth

