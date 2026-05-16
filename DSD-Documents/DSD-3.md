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

### 3.1.5 커뮤니티 및 광고 인터페이스 (Community & Advertisement Interface)
커뮤니티 공간은 앱 내 '모임(방)'의 작은 대화 공간과는 완전히 독립된 **웹(Web) 기반 플랫폼**으로 분리하여 운영합니다. 앱 본연의 기능인 '숏폼 차단 및 몰입'을 방해하지 않으면서, 웹 커뮤니티 내 배너 광고(개인 광고) 및 사용자 참여형 비즈니스 모델(BM)을 통해 수익을 창출합니다.

* **인터페이스 개요:** Mobile App (Trigger Link) ↔ Web Front-end ↔ Web Server ↔ Firebase / DB
* **주요 인터페이스 및 데이터 흐름:**
    1. **웹 브라우저 연동 인터페이스:** 앱 하단 탭의 '커뮤니티' 버튼 클릭 시, 유저 토큰(UID) 및 당일 도파민 스코어 데이터를 파라미터로 지닌 상태로 외부 웹 브라우저(또는 WebID) 인터페이스를 호출합니다.
    2. **동적 UI 제어 인터페이스:** 당일 도파민 점수(Score)를 웹 서버가 조회하여 커뮤니티 게시글/댓글 작성 시 유저의 글자 크기(Font Size)를 동적으로 제한하거나 칭호 이모티콘을 매핑합니다.
    3. **광고 인프라 및 아이템 인터페이스 (당근마켓 모델 벤치마킹):** * 일반 스폰서십 배너 광고 API 연동.
        * 상점(스토어)에서 결제한 '확성기(광고하기)' 아이템 사용 시, 해당 유저의 홍보성 게시글을 일정 시간 피드 상단에 고정하거나 '광고' 배너 태그를 부착하여 노출하는 인터페이스를 제어합니다.
    4. **CRUD 및 소셜 인터페이스:** 글 작성(사진 업로드 포함), 상세 보기, 댓글 등록, 내 글/댓글 목록 조회 및 삭제 요청을 처리하는 RESTful API 인터페이스입니다.

---

---

## 3.2 서버 (Server Block)

### 3.2.1 데이터베이스 구조 및 관계 정의
본 시스템은 앱 내부의 실시간 모임(방) 관리, 도파민 점수 차감 데이터와 웹 기반 독립 커뮤니티의 게시글, 댓글, 광고 아이템 트랜잭션을 처리하도록 설계되었습니다.

* USER (유저) - ROOM_MEMBER (모임 멤버) : 유저는 모임 멤버로 속함 (1:N)
* ROOM (모임방) - ROOM_MEMBER (모임 멤버) : 모임방은 여러 멤버를 포함함 (1:N)
* USER (유저) - DOPAMINE_LOG (시청 로그) : 유저는 시청 로그를 생성함 (1:N)
* USER (유저) - COMMUNITY_POST (게시글) : 유저는 커뮤니티 게시글을 작성함 (1:N)
* USER (유저) - COMMUNITY_COMMENT (댓글) : 유저는 커뮤니티 댓글을 작성함 (1:N)
* USER (유저) - USER_INVENTORY (인벤토리) : 유저는 아이템 인벤토리를 소유함 (1:N)

---

### 3.2.2 각 테이블의 구조

#### 1) 유저 테이블 (USER)
* **설명:** 회원가입 시 생성되는 유저의 기본 정보 및 앱 내 도파민 통제 점수를 관리하는 테이블입니다.

| 속성명 (Attribute) | 자료형 (Data Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `user_id` | VARCHAR(50) | PRIMARY KEY | Firebase Auth 연동 유저 고유 UID |
| `email` | VARCHAR(100) | NOT NULL, UNIQUE | 유저 이메일 계정 |
| `nickname` | VARCHAR(30) | NOT NULL | 서비스 내 노출될 유저 식별 닉네임 |
| `daily_score` | INT | DEFAULT 100 | 금일 잔여 도파민 점수 (매일 자정 100 초기화) |
| `restriction_weights` | JSON | NOT NULL | 1~5위 위험 카테고리 가중치 및 선호 카테고리 설정 데이터 |

#### 2) 모임 테이블 (ROOM)
* **설명:** 앱 내에서 ()"열품타 앱"을 벤치마킹하여 생성한) 유저간 소그룹 모임방 메타데이터 테이블입니다.

| 속성명 (Attribute) | 자료형 (Data Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `room_id` | VARCHAR(50) | PRIMARY KEY | 모임방 고유 식별 ID |
| `room_name` | VARCHAR(50) | NOT NULL | 생성된 모임방 이름 |
| `master_id` | VARCHAR(50) | FOREIGN KEY (USER) | 방장 권한을 가진 유저의 ID |
| `invite_code` | VARCHAR(12) | NOT NULL, UNIQUE | 방 입장을 위한 난수 고유 코드 |

#### 3) 모임 멤버 상세 테이블 (ROOM_MEMBER)
* **설명:** 모임방 내 소속된 멤버(유저들) 정보, 실시간 랭킹 순위 및 자정 정산 결과인 칭호(대장/꼴찌 등) 권한을 매핑합니다.

| 속성명 (Attribute) | 자료형 (Data Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `member_id` | VARCHAR(50) | PRIMARY KEY | 매핑 고유 ID |
| `room_id` | VARCHAR(50) | FOREIGN KEY (ROOM) | 소속된 모임방 ID |
| `user_id` | VARCHAR(50) | FOREIGN KEY (USER) | 방에 참여한 유저 ID |
| `user_title` | VARCHAR(20) | DEFAULT '일반' | 자정 정산 칭호 (대장: 하위 유저 알림 권한 획득) |
| `current_rank` | INT | NULL | 방 내 금일 잔여 점수 기준 실시간 순위 |

#### 4) 도파민 시청 로그 테이블 (DOPAMINE_LOG)
* **설명:** OCR로 분류된 숏폼 기록이 적재되며, 방 내 유저 이모티콘 클릭 시 노출되는 '금일 도파민 컷 내용(유저별 숏폼 제한 내용)' 팝업의 통계 원천 데이터가 됩니다.

| 속성명 (Attribute) | 자료형 (Data Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `log_id` | VARCHAR(50) | PRIMARY KEY | 로그 행 식별 고유 ID |
| `user_id` | VARCHAR(50) | FOREIGN KEY (USER) | 시청 행위를 한 유저 ID |
| `category` | VARCHAR(20) | NOT NULL | OCR 매칭 카테고리 9종 중 하나 명시 |
| `duration_sec` | INT | NOT NULL | 해당 숏폼 영상 누적 체류 지속 시간(초) |
| `deducted_score` | INT | NOT NULL | 가중치가 반영되어 최종 차감 처리된 벌점액 |
| `created_at` | TIMESTAMP | DEFAULT NOW() | 숏폼 시청 로그 발생 시간 |

#### 5) 웹 커뮤니티 게시글 테이블 (COMMUNITY_POST)
* **설명:** 독립 웹 커뮤니티의 피드 데이터베이스입니다. 스토어(상점) 아이템(확성기)을 통한 개인 광고 글 여부 및 노출 만료 시간을 제어합니다.

| 속성명 (Attribute) | 자료형 (Data Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `post_id` | VARCHAR(50) | PRIMARY KEY | 게시글 고유 ID |
| `user_id` | VARCHAR(50) | FOREIGN KEY (USER) | 웹 커뮤니티에 글을 쓴 유저 ID |
| `title` | VARCHAR(150) | NOT NULL | 게시글 제목 |
| `content` | TEXT | NOT NULL | 게시글 본문 텍스트 내용 |
| `image_url` | VARCHAR(255) | NULL | 첨부 사진 스토리지 주소 (텍스트 전용일 시 NULL) |
| `is_ad` | BOOLEAN | DEFAULT FALSE | 확성기 아이템을 적용한 유저 광고 글 여부 (당근마켓 모델) |
| `expired_at` | TIMESTAMP | NULL | 광고 아이템 적용 시 상단 고정이 만료되는 시점 |
| `created_at` | TIMESTAMP | DEFAULT NOW() | 최초 작성 일시 |

#### 6) 웹 커뮤니티 댓글 테이블 (COMMUNITY_COMMENT)
* **설명:** 웹 커뮤니티 게시글 하단에 달리는 댓글 데이터를 관리하며, 작성 유저의 스코어(금일 도파민 점수)에 맞춰 글자 크기가 동적으로 적용됩니다.

| 속성명 (Attribute) | 자료형 (Data Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `comment_id` | VARCHAR(50) | PRIMARY KEY | 댓글 고유 식별 ID |
| `post_id` | VARCHAR(50) | FOREIGN KEY (POST) | 대댓글 및 원본 게시글 연동 식별 ID |
| `user_id` | VARCHAR(50) | FOREIGN KEY (USER) | 댓글 작성자 유저 ID |
| `content` | TEXT | NOT NULL | 댓글 텍스트 내용 |
| `created_at` | TIMESTAMP | DEFAULT NOW() | 댓글 작성 시간 |

#### 7) 유저 인벤토리 아이템 테이블 (USER_INVENTORY)
* **설명:** 인앱 결제 혹은 우수 보상으로 획득한 '찌르기(알림)', '확성기(개인 광고)' 아이템 소유 현황을 저장하여 앱의 비즈니스 모델(수익 창출)을 실현합니다.

| 속성명 (Attribute) | 자료형 (Data Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `item_id` | VARCHAR(50) | PRIMARY KEY | 인벤토리 내 아이템 식별 키 |
| `user_id` | VARCHAR(50) | FOREIGN KEY (USER) | 아이템을 소유한 유저 고유 ID |
| `item_type` | VARCHAR(30) | NOT NULL | 아이템 대분류 (POKE: 찌르기 권한 / MEGAPHONE: 광고 확성기) |
| `quantity` | INT | DEFAULT 0 | 유저가 보유 중인 해당 아이템 잔여 수량 |