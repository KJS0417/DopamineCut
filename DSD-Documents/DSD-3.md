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

### 3.2.2 각 컬렉션의 구조

#### 1) 유저 정보 컬렉션 (users)
* **설명:** 회원가입 시 생성되는 유저의 계정 정보 및 인벤토리 아이템, 카테고리 우선순위를 통합 관리하는 컬렉션입니다.
* **문서 ID:** Firebase Auth에서 발급된 유저 고유 ID (`user_id`)

| 필드명 (Field) | 유형 (Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `user_id` | `string` | **REQUIRED**, 문서 ID와 일치 필수 | 유저 고유 UID (조회 및 객체 매핑용) |
| `email` | `string` | **REQUIRED**, 이메일 형식 | 유저 이메일 계정 |
| `nickname` | `string` | **REQUIRED**, 글자 수 제한 (예: 2~30자) | 서비스 내 노출될 유저 식별 닉네임 |
| `created_at` | `timestamp` | **REQUIRED** | 계정 가입/생성 시간 |
| `restrictions` | `array<string>` | **REQUIRED**  | 유저가 설정한 1~N순위 절제 카테고리 명칭 배열 (예: `["연예", "게임"]`) |
| `inventory` | `map` | **REQUIRED**, 기본값 `0`, -1 이하 값 처리 로직 요구 | 아이템 잔여 수량 표시 |

### `inventory` map 구조
* `poke` (`int64`): 모임 내 찌르기 잔여 횟수 (제약: $\ge 0$)
* `megaphone` (`int64`): 커뮤니티 광고 확성기 잔여 수량 (제약: $\ge 0$)

---

#### 2) 모임방 컬렉션 (rooms)

* **설명:** 유저 간 소그룹 모임방 메타데이터를 관리하며, 멤버 맵 형식을 통해 방 문서 1개 조회만으로 실시간 랭킹 연출을 지원합니다 (`image_cdbb3e.png` 반영).
* **문서 ID:** 모임방 고유 식별 ID (`room_id`)

| 필드명 (Field) | 유형 (Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `room_id` | `string` | **REQUIRED**, 문서 ID와 일치 필수 | 모임방 고유 식별 ID (조회 및 객체 매핑용) |
| `room_name` | `string` | **REQUIRED**, 글자 수 제한 (예: 최대 50자) | 생성된 모임방 이름 |
| `master_id` | `string` | **REQUIRED**, `users` 컬렉션에 존재 확인 필수 | 방장 권한을 가진 유저의 ID |
| `invite_code` | `string` | **REQUIRED** | 방 입장을 위한 코드, 방장이 직접 정하거나 난수 사용 |
| `members` | `map` | **REQUIRED**, 최대 멤버 수 제한 (예: 20명) | 소속된 멤버들의 실시간 상태 및 점수 내역 |

### `members` 내부 중첩 구조 (Map)
* **설명:** 모임방 내 소속된 멤버(유저들) 정보 및 칭호(대장/꼴찌 등)를 매핑합니다.

  **`{user_uid}` (Key):** 각 참여 유저의 실제 고유 UID 문자열 (`users` 컬렉션의 유효한 ID여야 함)
  * `nickname` (`string`): **REQUIRED** / 해당 유저의 닉네임 (역정규화)
  * `daily_score` (`int64`): **REQUIRED** / 범위 제약: $0 \le \text{score} \le 100$ (매일 자정 100점 초기화)
  * `user_title` (`string`): **REQUIRED** / 기본값 `'일반'`
  * `user_status` (`string`): **REQUIRED** / 유저의 활동 정보

* **변경사항:** 기존 ROOM_MEMBER 테이블에 포함되어 있던 current_rank는 앱 내에서 계산함.

---

#### 3) 도파민 점수 변동 로그 (DOPAMINE_LOG)
* **설명:** 방 내 유저의 기록을 확인하는 '도파민 로그'의 데이터를 저장하는 컬렉션입니다.

| 필드명 (Field) | 유형 (Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `user_id` | `string` | **REQUIRED**, `users` 컬렉션에 존재 필수 | 시청 행위를 한 유저의 고유 UID |
| `platform` | `string` | **REQUIRED** | 영상을 시청한 플랫폼명 |
| `created_at` | `timestamp` | **REQUIRED**, 서버 시간 기준 생성 (`request.time`) | 숏폼 시청 로그 발생 시간 |
| `category` | `string` | **REQUIRED**, OCR 매칭 카테고리 9종 내 명시 | OCR 분류 카테고리 명칭 |
| `duration_sec` | `int64` | **REQUIRED** | 해당 숏폼 영상 누적 체류 지속 시간(초) |

---

#### 4) 커뮤니티 게시글&댓글 테이블 (COMMUNITY_POST)

* **설명:** 커뮤니티의 데이터베이스입니다. 댓글 작성자의 글자 크기를 점수에 따라 실시간 동기화하여 렌더링합니다.
* **문서 ID:** Firestore 자동 생성 ID (`post_id`)

### 필드 구성 및 제약조건
| 필드명 (Field) | 유형 (Type) | 제약조건 (Constraint) | 설명 (Description) |
| :--- | :--- | :--- | :--- |
| `post_id` | VARCHAR(50) | PRIMARY KEY | 게시글 고유 ID |
| `user_id` | `string` | **REQUIRED**, `users` 컬렉션에 존재 필수 | 웹 커뮤니티에 글을 쓴 유저 ID |
| `nickname` | `string` | **REQUIRED** | 게시글 상단에 바로 노출할 작성자 닉네임 |
| `title` | `string` | **REQUIRED**, 최대 글자 수 제한 (예: 150자) | 게시글 제목 |
| `content` | `string` | **REQUIRED**, 본문 최소/최대 제한 없음 | 게시글 본문 텍스트 내용 |
| `image_url` | `string` | **OPTIONAL**, URL 형식 준수 (없을 시 빈 문자열) | 첨부 사진 스토리지 주소 |
| `is_ad` | `boolean` | **REQUIRED**, 기본값 `false` | MEGAPHONE 아이템을 적용한 유저 광고 글 여부 |
| `expired_at` | `timestamp` | **OPTIONAL**, `is_ad`가 `true`일 때 필수 지정 | 광고 아이템 적용 시 상단 고정이 만료되는 시점 |
| `created_at` | `timestamp` | **REQUIRED**, 서버 시간 기준 생성 | 최초 작성 일시 |
| `comments` | `map` | **REQUIRED**, 기본값 빈 맵 `{}` 생성 | 게시글 하단에 달리는 댓글 데이터 |

### `comments` 내부 구조 (Map)
* **`{comment_number}` : **REQUIRED** / 댓글 순서 표시 및 고유 MAP ID
  * `user_id` (`string`): **REQUIRED** / 댓글 작성자의 user_id
  * `nickname` (`string`): **REQUIRED** / 댓글 작성자의 닉네임
  * `content` (`string`): **REQUIRED** / 댓글 텍스트 본문
  * `created_at` (`timestamp`): **REQUIRED** / 댓글 작성 시간
  * *(※ 해당 댓글 렌더링 시, 글자 크기는 `users` 컬렉션 내 동일 유저 UID의 `daily_score` 값을 실시간 대조하여 동적으로 스케일링합니다.)*

* **변경사항:** 댓글 테이블과 게시글 테이블을 별도의 테이블로 관리하지않고 함께 관리함.

---

### 5) 일일 누적 통계 컬렉션
* **설명:** 사용자의 하루 전체 앱 사용 동향 및 타겟 플랫폼별 숏폼 시청 통계를 보관합니다.
* **문서 ID:** 고유 ID $\rightarrow$ **`{user_id}_{YYYYMMDD}`** 로 지정할 것.
  
### 필드 구성
| 필드명 (Field) | 유형 (Type) | 설명 (Description) |
| :--- | :--- | :--- |
| `user_id` | `string` | 통계의 유저 고유 UID |
| `date` | `string` | 집계 기준 날짜 문자열 (예: `"20260523"`) |
| `daily_score` | `int64` | 오늘 하루 동안 누적되어 차감된 총 벌점액 |
| `shortform_time` | `int64` | 하루 동안 시청한 총 숏폼 누적 합산 시간(초) |
| `is_settled` | `boolean` | 자정에 당일 정산이 처리되었는지 여부 |
| `app_usage` | `map` | 제안된 구조를 기반으로 확장된 앱별 상세 사용 통계 map |

### `app_usage` 내부 중첩 구조 (Map)
유저가 타겟 앱을 종료하거나 백그라운드로 전환 시, 안드로이드 클라이언트단에서 `FieldValue.increment()` 원자적 연산을 활용해 실시간으로 필드 숫자를 누적 업데이트합니다.
  * `instagram` / `kakaotalk` / `tiktok` / `youtube` (동일 내부 명세)
  * `run_time_sec` (`int64`): 당일 해당 앱 총 실행 시간(초)
  * `shortform_time_sec` (`int64`): 해당 앱 내에서 '숏폼 영상'만 소비한 누적 시간(초)
  * `shortform_count` (`int64`): 당일 시청 완료 혹은 누적 집계된 숏폼 영상 개수

---

## 3.3 사용자 통계 제공
  도파민 컷은 사용자의 시청한 숏폼의 카테고리별 수, 총 숏폼 시청 수, 앱별 시청 수, 앱별 사용 시간 등의 정보를 수집하여 여러 통계를 제공한다.
  제공할 통계 및 시각화 지표는 다음과 같다.
  
  ### 3.3.1 기본 제공 통계
  로그인 후의 메인 화면에서 확인할 수 있는 통계이다.
  | 통계명 | 활용 테이블 | 시각화 방식 | 설명 |
  | :--- | :--- | :---: | :---: |
  | 오늘의 숏폼 카테고리 비율 | DAILY_STATISTICS<br>(category_count_json) | 파이 차트 | 오늘 시청한 숏폼 영상들이 카테고리 중 어디에 집중되어  있는지 비율(%)로 제공한다. |
  | 주간 숏폼 시청 추이 | DAILY_STATISTICS<br>(total_shorts_count) | 막대 그래프 | 최근 7일간의 총 숏폼 시청 개수 변화를 막대 그래프로 보여주어 점진적 감소 여부를 파악하게 한다. |
  | 오늘의 앱 사용 시간 랭킹 | DAILY_STATISTICS<br>(app_usage_json) | 수평 막대 그래프 | 등록한 앱 중 오늘 가장 많은 시간을 소비한 앱의 순위와 시간을 표기한다. |

  ### 3.3.2 추가 제공 통계
  앱 하단 탭 중 통계를 선택하면 기본 제공 통계와 함께 추가적으로 확인할 수 있는 통계이다.
  | 통계명 | 활용 테이블 | 시각화 방식 | 설명 |
  | :--- | :--- | :---: | :---: |
  | 취약 시간대 분석 | DOPAMINE_LOG<br>(created_at) | 꺾은선 그래프 | 하루 24시간 중 어느 시간대에 도파민 점수 차감이 가장 많이 발생하는지 분석하여 사용자의 취약 시간을 시각적으로 경고한다. |
  | 앱 사용 목적 분석 | DAILY_STATISTICS &<br>DOPAMINE_LOG | 도넛 차트 | 숏폼 플랫폼 앱(예: 유튜브)의 "총 실행 시간" 대비 "숏폼 재생 시간"의 비율을 계산한다. (예: "유튜브 사용 시간의 85%를 쇼츠 시청에 사용했습니다.") |
  | 목표 달성일 표시 | DAILY_STATISTICS<br>(final_scorㄷ) | 캘린더 & 불꽃 아이콘 | 사용자 지정 목표를 달성한 날짜를 표기한다. 캘린더 UI를 통해 달성한 기록을 사용자가 파악하게 끔하여 지속적인 참여를 유도한다. <br> 목표를 지정하지 않은 경우에는 목표 달성으로 포함하지 않는다. |
  
  
