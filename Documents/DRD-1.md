# 프로젝트 제안서

## Members
* Student1 : [윤창길(팀장)](https://github.com/Spearoad)
* Student2 : 김동욱
* Student3 : 김진수
* Student3 : 조수현
* Student3 : 천성찬
* Student3 : 최현준 

---

## Objectives
* **Title:** 과도한 숏폼 시청 방지 및 도파민 디톡스 어플리케이션

---

## Background & Basic knowledge
* Android Studio 환경에서의 Kotlin 기반 애플리케이션 개발 및 디버깅 프로세스를 숙지하고 있다.
* Git과 GitHub를 활용한 형상 관리 및 브랜치(Branch) 기반의 원격 협업, 충돌(Conflict) 해결에 능숙하다.
* 안드로이드의 Accessibility Service(접근성 서비스) 작동 원리를 이해하고, 화면(UI Node)의 이벤트를 백그라운드에서 감지 및 제어할 수 있다.
* Firebase Firestore(NoSQL)의 구조를 이해하고, 커뮤니티 데이터(게시글, 유저 정보)의 실시간 읽기/쓰기(CRUD) 및 비정규화 처리 방식을 알고 있다.

---

## Resource & Reading Material
* AccessibilityService API: 타사 앱(유튜브, 인스타그램 등)의 UI Node 계층 구조 탐색 및 뷰(View) 이벤트 감지 매뉴얼
* Cloud Firestore: NoSQL 기반의 데이터 모델링(Data Modeling) 및 커뮤니티 데이터 실시간 동기화(Realtime Updates) 구축 가이드
* Firebase Authentication: 안전한 유저 로그인 및 세션 관리 매뉴얼
* Kotlin Coroutines & Flow: DB 읽기/쓰기 및 차트 데이터 렌더링 시 메인 스레드(UI) 멈춤 현상을 방지하는 비동기 프로그래밍 문서

---

## Preparation & Tool
* **S/W:** Android Studio

---

## Functions (1 page 이내로 최대한 자세히 제시)

### * 접근성 감지
1. YouTube, Instagram, Kakaotalk, Tiktok 앱 실행 시간을 측정.
2. YouTube, Instagram, Kakaotalk, Tiktok 앱에서 숏폼 진입 시 이를 감지하여 시청 영상 수를 측정.

### * 연산 및 알림
1. 최초 실행 시 자가진단을 통해 단계별 맞춤 도파민 디톡스 방법 제안 및 초기 목표 수립.
2. 사용자가 설정한 일일 한도(시간/횟수) 초과가 감지될 때, 단계별 차단을 통해 스스로 제어할 수 잇도록 유도.

### 통계 제공
1. 수집된 시청 및 앱 사용 데이터를 불러와서 여러 통계를 차트로 시각화.
2. 사용자가 설정한 일일 한도 초과 및 수면 시간대에 사용한 내용을 강조 표시.

### 서버 및 커뮤니티
1. Firebase를 활용하여 커뮤니티 구축 및 동기화.
2. 사용자의 사용 데이터와 커뮤니티 데이터를 기록.

---

## Project Schedule

| 날짜 | 수행 내용 |
| :--- | :--- |
| **3/15** | 프로젝트 주제 결정 |
| **3/20** | 제안서 작성 및 수정 |
| **4/1** | 제안서 발표 및 보완 |
| **~4/20** | DRD 작성 및 수정 |
| **~5/18** | DSD 작성 및 수정 |
| **~6/12** | 프로토타입 제작 및 발 |
