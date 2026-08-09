# AGENTS.md

이 문서는 본 프로젝트(`com.wevo.backend`)의 개발 규칙을 정의합니다.
코드를 작성·수정할 때는 아래 규칙을 항상 준수합니다.

**정본(source of truth) 원칙** — 이 문서는 "규칙과 계약"만 담습니다.
구현물(클래스 소스, BE 내부 enum 목록 등)은 문서에 복사하지 않으며, 아래 정본을 직접 참조합니다.
(예외: 제품 계약 enum의 이름 매핑은 §5.7 표가 정본입니다.)

**개발 판단 우선순위** — ① **팀이 합의하고 기록한 최신 개발 결정** ② **제품 정책서** ③ **프로젝트 기획서**.
기능을 설계·구현할 때는 정책서·기획서를 기준으로 하되, 문서와 다른 결정이 필요하면
**팀 합의를 거쳐** 결정하고 관련 문서에 즉시 기록합니다.
개인 판단으로 정책과 다르게 구현하지 않습니다 — 합의되고 기록된 결정만 정책서보다 우선합니다.

| 대상 | 정본 위치 |
| --- | --- |
| 제품 정책·상태값 의미 | `docs/제품 정책서.md` (상태값은 §8) — 2순위 |
| 프로젝트 목표·MVP 범위·우선순위 | `docs/프로젝트 기획서.md` (MoSCoW는 §4-3) — 3순위 |
| API 명세 — 엔드포인트별 계약 | `docs/API_SPEC.md` (작성 양식: `docs/API_TEMPLATE.md`) |
| API 공통 규칙 (응답 형식·상태 코드 등) | **이 문서 §5** — `API_SPEC.md §1`은 FE 공유용 요약이며, 갈리면 이 문서가 우선 |
| 실패 코드 목록 | `src/main/java/com/wevo/backend/global/exception/ErrorCode.java` |
| 공통 응답 구현 | `src/main/java/com/wevo/backend/global/response/ApiResponse.java` |
| 실행 중 API 문서 | Swagger UI `/swagger-ui/index.html` — 구현 확인용 보조 문서 (엔드포인트별 계약 정본은 `API_SPEC.md`) |

**문서 동기화** — `CLAUDE.md`를 변경하는 PR에서는 `AGENTS.md`를 동일 사본(제목 줄만 다름)으로
재생성합니다. §5(API 공통 규칙)를 변경할 때는 FE 공유용 `API_SPEC.md §1`과 `API_TEMPLATE.md`도
함께 갱신합니다.

---

## 0. 프로젝트 개요 · 빌드 · 실행

**Wevo** — 팀원들의 흩어진 의견을 AI가 정리·조율해 하나의 결과물(제안서/발표자료)로
완성하는 협업 워크스페이스의 백엔드입니다.

### 기술 스택

- Java 21, Spring Boot, Gradle — 의존성·버전의 정본은 `build.gradle`
- Spring Data JPA + PostgreSQL, Redis, Flyway — 인프라 버전의 정본은 `docker-compose.yml`
- Spring Security + JWT(JJWT), Google/Kakao OAuth
- Spring AI (OpenAI GPT-5.6 Luna 단일 Provider)
- Springdoc OpenAPI(Swagger), Lombok, JUnit 5 (테스트 계층별 DB 사용 기준은 §8)

### 빌드 / 테스트 / 로컬 실행

```bash
./gradlew build                 # 빌드 + 테스트
./gradlew test                  # 테스트만

# 로컬 실행 — 최초 1회: cp .env.example .env 후 비밀 값 채우기
docker compose up -d            # PostgreSQL / Redis 기동 (포트는 .env의 POSTGRES_PORT / REDIS_PORT)
./gradlew bootRun --args='--spring.profiles.active=local'
```

- 필요한 환경 변수 목록은 `.env.example`이 정본입니다. 변수 추가 시 `.env.example`도 함께 갱신합니다.
- Windows에서는 `./gradlew` 대신 `gradlew.bat`를 사용합니다.
- 프로파일: 공통 설정은 `application.yml`, 로컬 전용은 `application-local.yml`(+ `.env`)에 둡니다.
- 비밀 값(API 키, DB 비밀번호, JWT secret)은 절대 yml/코드에 직접 쓰지 않고 환경 변수로 주입합니다.
- `local` 프로파일에는 개발 편의용 `/api/auth/dev-login`이 활성화됩니다. (local 외 프로파일 등록 금지)
- 기능 우선순위는 기획서의 MoSCoW(§4-3)를 따릅니다.
  **Must 기능이 안정화되기 전에는 Should·Could 기능을 개발하지 않습니다.** (기획서 리스크 관리 원칙)
  단, Must 기능의 선행 의존 작업과 보안·안정성 작업은 팀 합의로 예외를 둘 수 있습니다.

---

## 1. 패키지 / 폴더 구조 — 도메인형

기능(도메인) 단위로 패키지를 분리합니다. 공통 인프라성 코드는 `global` 하위에 둡니다.

```
com.wevo.backend
├─ global
│  ├─ common        # 공통 엔티티 (BaseTimeEntity 등)
│  ├─ config        # 스프링 설정, Bean 등록
│  ├─ security      # 인증/인가, 시큐리티 설정
│  ├─ exception     # 공통 예외, 전역 예외 핸들러, ErrorCode
│  └─ response      # 공통 응답 포맷(ApiResponse 등)
├─ auth
├─ user
├─ project
├─ section
├─ opinion
├─ issue
├─ ai
├─ review
└─ export
```

### 도메인 패키지 내부 구조(권장)

```
user
├─ controller
├─ service
├─ repository
├─ domain        # 엔티티, Enum 등
└─ dto
   ├─ request
   └─ response
```

- 한 도메인에서만 쓰는 코드는 해당 도메인 패키지 안에 둡니다.
- 두 개 이상 도메인이 공유하는 인프라성 코드만 `global`로 올립니다.
- 도메인별 Enum은 해당 도메인의 `domain` 패키지에 둡니다. (공통 Enum 규칙은 §5.7 참고)
- 실시간 협업 중 **초안 편집 잠금(lease)** 은 섹션 초안의 접근 제어를 담당하므로 `section` 도메인에 둡니다.
  변경 반영 등 다른 실시간 협업 기능의 패키지 위치는 구현 착수 전에 팀 합의로 정하고 이 구조도를 함께 갱신합니다.

---

## 2. 코드 네이밍 컨벤션

| 항목 | 규칙 | 예시 |
| --- | --- | --- |
| class / interface | UpperCamelCase | `UserService`, `OrderRepository` |
| package | 소문자 | `service`, `repository` |
| method | lowerCamelCase | `getUser`, `createProject` |
| 변수 | lowerCamelCase | `userCount`, `totalPrice` |
| Enum 타입명 | UpperCamelCase | `OrderStatus` |
| Enum 상수 | UPPER_SNAKE_CASE | `IN_PROGRESS`, `DONE` |
| 상수(static final) | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |

### 클래스 접미사 규칙

| 역할 | 접미사 | 예시 |
| --- | --- | --- |
| 컨트롤러 | `Controller` | `UserController` |
| 서비스 | `Service` | `UserService` |
| 리포지토리 | `Repository` | `UserRepository` |
| 요청 DTO | `Request` | `UserCreateRequest` |
| 응답 DTO | `Response` | `UserResponse` |
| 예외 | `Exception` | `UserNotFoundException` |
| 설정 클래스 | `Config` | `SecurityConfig` |

---

## 3. 파일명 컨벤션

- **클래스명과 파일명은 동일**하게 작성합니다. (`UserService` → `UserService.java`)
- 기타 파일은 각 생태계 관례를 따릅니다. (예: `application.yml`, `build.gradle`)

---

## 4. DB 네이밍 · 스키마 관리

- 테이블·컬럼 모두 **snake_case**를 사용합니다. (예: `created_at`, `total_price`, `user_id`)
- 자바 엔티티 필드는 lowerCamelCase로 두고, 매핑은 ORM 설정(또는 `@Column(name = "...")`)으로 처리합니다.
- Enum 컬럼은 **문자열로 저장**합니다. (JPA `@Enumerated(EnumType.STRING)`)
- 스키마 변경은 Flyway 마이그레이션으로 관리합니다.
  - 위치: `src/main/resources/db/migration`, 파일명: `V{version}__{description}.sql`
  - **이미 적용된 마이그레이션은 수정하지 않고** 새 버전을 추가합니다.
  - JPA `ddl-auto`는 **`validate`** 를 사용합니다 — 스키마를 만들고 바꾸는 주체는 항상 Flyway이며,
    Hibernate가 스키마를 변경하지 않습니다.
    (`validate` 전환은 **V1 마이그레이션을 추가하는 변경에서 함께** 수행합니다 — 그 전에 설정만
    먼저 바꾸면 로컬 부팅이 실패합니다. 현재 경과는 `db/migration/README.md` 참고)

---

## 5. API 공통 규칙

### 5.1 Base URL

- 인증 필요 API: `/api`
- 공개(비인증) API: `/public` — 외부 검토자용 공개 리뷰 제출 등
- **API 버전은 MVP에서 사용하지 않습니다.** (`/api/v1` 없음 — 도입이 필요해지는 시점에 팀 합의로 결정)

### 5.2 인증 방식

- 인증 방식: `Bearer JWT`

```
Authorization: Bearer {accessToken}
```

- 별도 표기가 없는 한 모든 API는 **로그인 사용자만 호출 가능**합니다.
- 인증 없이 접근 가능한 경로는 다음뿐입니다 (`SecurityConfig`의 permitAll 목록과 항상 일치시킬 것):
  - `/public/**` — 공개 리뷰 API
  - `/api/auth/login`, `/api/auth/reissue` — 로그인 · 토큰 재발급
  - `/api/auth/dev-login` — local 프로파일 전용
  - `/swagger-ui/**`, `/v3/api-docs/**` — API 문서
- 비인증으로 사용할 수 있는 **제품 기능**은 외부 검토(`/public`)뿐입니다.
  (로그인·토큰 재발급·Swagger는 제품 기능이 아니라 인증 절차·개발 문서 경로입니다.)
  초대 링크로 프로젝트에 참여하려면 로그인이 필요하며(정책서 §2.1), 비로그인 게스트 입장은 MVP에 없습니다.

### 5.3 Content-Type

```
Content-Type: application/json
```

- 요청 Content-Type: JSON 요청은 `application/json`, 파일 업로드 요청은 `multipart/form-data`를 사용합니다.
- `ApiResponse` 래퍼(§5.4)는 **응답 형식 기준**으로 적용합니다.
  응답이 JSON이면 요청 형식과 무관하게 래퍼를 적용하고,
  파일·바이너리·스트리밍(SSE) 응답에는 적용하지 않습니다.
  비JSON 응답 API는 Content-Type과 응답 형태를 `API_SPEC.md`에 명시합니다. (예: export 파일 다운로드)

### 5.4 공통 응답 형식

모든 **JSON API 응답**은 `global.response.ApiResponse<T>` 래퍼로 감싸 동일한 형태로 반환합니다.
(예외: 본문이 없는 `204 No Content`, 그리고 §5.3에 정의한 비JSON 응답)

**응답 계약** (구현 정본: `ApiResponse.java`, `FieldError.java`)

- 모든 래핑된 응답에 `success`(boolean), `code`, `message`, `timestamp`(ISO-8601)를 포함합니다.
- **시간 표현(전 API 공통)**: 모든 날짜·시간 필드는 **KST(Asia/Seoul) 기준, 오프셋 없는 ISO-8601**
  (`2026-07-16T12:30:00`)이며 서버·DB·클라이언트 모두 KST로 해석합니다.
  이는 국내 전용 MVP의 **의도된 결정**입니다. 다중 타임존 지원이 필요해지면
  오프셋 포함(`OffsetDateTime`, `2026-07-16T12:30:00+09:00`) 계약으로 전환하되,
  FE와 합의가 필요한 **계약 변경**으로 다룹니다.
  시간 값 생성 시 JVM 기본 시간대에 의존하지 않고 `ZoneId.of("Asia/Seoul")`을 명시합니다.
  (배포 서버가 UTC여도 계약이 유지되도록)
- `message`는 사용자에게 그대로 노출해도 되는 한국어 문장으로 작성합니다.
- `null` 필드는 직렬화에서 제외합니다. (성공 시 `errors` 생략, 실패 시 `data` 생략)
- 실패 응답의 `code`에는 반드시 `ErrorCode.code` 값(예: `P001`)을 사용합니다.
  Enum 상수명(예: `PROJECT_NOT_FOUND`)을 외부에 노출하지 않습니다.
- `errors`는 검증 실패 등 **필드 단위 사유**가 있을 때만 `{field, reason}` 목록으로 담습니다.

**성공 응답 예시**

```json
{
  "success": true,
  "code": "PROJECT_CREATED",
  "message": "프로젝트가 생성되었습니다.",
  "data": { "id": 1 },
  "timestamp": "2026-06-26T20:30:00"
}
```

**실패 응답 예시**

```json
{
  "success": false,
  "code": "P001",
  "message": "프로젝트를 찾을 수 없습니다.",
  "errors": [
    { "field": "projectId", "reason": "invalid value" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

**성공 `code` 형식** — 별도 Enum으로 강제하지 않되, 형식은 통일합니다.

- UPPER_SNAKE_CASE로, 무슨 일이 일어났는지 식별되는 코드를 사용합니다.
  (예: `PROJECT_CREATED`, `OPINION_SUBMITTED`, `LOGIN_SUCCESS`)
- 단순 조회는 `OK`를 사용합니다.

### 5.5 페이지네이션 형식

목록 조회 API 중 데이터가 많아질 수 있는 API는 페이지네이션을 사용합니다.
공통 페이지 응답 클래스는 `global.response`에 두고 아래 계약을 따릅니다.

**요청 파라미터**

| 파라미터 | 설명 | 기본값 |
| --- | --- | --- |
| `page` | 0부터 시작하는 페이지 번호 | `0` |
| `size` | 페이지 크기 | `20`, **최대 100** (초과 요청은 서버가 100으로 제한) |
| `sort` | 정렬 기준 (`필드,방향`) | API별로 명세에 기본 정렬을 반드시 명시 (예: `createdAt,desc`) |

- 정렬을 지원하는 필드는 API별 명세에 명시하고, 허용 목록 밖의 필드 정렬 요청은
  `INVALID_INPUT`(`C001`)으로 거부합니다. (임의 필드 정렬 허용 금지)

**응답 예시** — `data`에 페이지 메타데이터를 함께 담습니다.

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 123,
    "totalPages": 7,
    "hasNext": true
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

### 5.6 공통 HTTP 상태 코드

| 코드 | 사용 상황 |
| --- | --- |
| `200 OK` | 정상 조회, 수정 성공 |
| `201 Created` | 생성 성공 |
| `202 Accepted` | 비동기 작업 요청 수락 (처리 완료 전이며 `requestId`로 상태·결과 조회) |
| `204 No Content` | 삭제 또는 해제 성공 (본문 없음 — `ApiResponse` 래퍼 미적용) |
| `400 Bad Request` | 잘못된 요청 (형식·필수값·타입 오류) |
| `401 Unauthorized` | 인증 실패 |
| `403 Forbidden` | 권한 없음 (멤버지만 역할 권한 부족) |
| `404 Not Found` | 대상 리소스 없음 · 존재 숨김 (비멤버의 접근) |
| `409 Conflict` | 리소스의 현재 상태·버전과 충돌 |
| `422 Unprocessable Entity` | 저장 상태와 무관한 요청 내용의 의미적 오류 |
| `500 Internal Server Error` | 서버 오류 |

- `202 Accepted`는 요청 처리가 완료되지 않은 **비동기 작업 API에만** 사용합니다. 응답에는 작업을
  조회할 `requestId`를 포함하고, 상태·결과 조회 API와 완료·실패 계약을 엔드포인트 명세에 함께
  기록합니다.

**409 vs 422 판단 기준** — 리뷰에서 자주 갈리는 지점이므로 기준을 고정합니다.

- `409` = **저장된 리소스의 현재 상태·버전과 충돌**하는 요청.
  (예: 마감된 의견 수집에 의견 제출, AI 사전 검토 미완료 상태에서 섹션 확정 시도,
  허용되지 않은 섹션 상태 전이, 프로젝트 정원 초과, `contentVersion` 충돌)
  단, 이미 멤버인 사용자의 초대 링크 재참여는 오류가 아니라 **멱등 성공**입니다. (정책서 §2.1)
- `422` = 저장 상태와 무관하게 **요청 내용 자체가 의미적으로 성립하지 않는** 요청
  (`BUSINESS_RULE_VIOLATION`, `C002`). (예: 기간 역전, 양립할 수 없는 입력 조합)
- **존재 숨김**: 접근 권한이 없는 사용자에게 리소스의 존재 자체를 숨겨야 하면 `403`이 아니라 `404`로
  응답합니다. (예: 비멤버의 프로젝트 상세 조회 → `404` `P001` — API 명세서 §3.2.3)
  멤버이지만 역할 권한이 부족한 경우에만 `403`을 사용합니다. (예: MEMBER의 초대 링크 생성 → `403`)

### 5.7 공통 Enum

문자열 Enum 저장 원칙을 따릅니다. (타입명은 UpperCamelCase, 상수는 UPPER_SNAKE_CASE — §2 참고)

**정본 위계** — 새 enum·상태를 구현할 때 다음을 기준으로 합니다.

1. **의미(무엇을 뜻하는가)**: `docs/제품 정책서.md` §8을 기본으로 합니다.
   다른 결정이 필요하면 **팀이 합의하고 기록한 최신 결정**을 우선하고, 이 표를 함께 갱신합니다.
2. **enum 이름(정책 상태 → 코드 이름 매핑)**: 아래 표가 팀 합의 결과입니다.
   구현 시 이 표의 이름으로 생성하고, 이름을 바꿀 때는 표와 코드를 함께 갱신합니다.
3. **BE 내부 enum**(계정 상태, AI 호출 로그, 토큰 타입 등 정책이 규정하지 않는 것):
   문서에 나열하지 않으며 BE 재량으로 코드에서만 관리합니다.

**제품 상태 Enum 이름 매핑 (정책서 §8 기준)**

| Enum | 값 | 비고 |
| --- | --- | --- |
| `ProjectStatus` | `DRAFT`, `ACTIVE`, `COMPLETED`, `ARCHIVED` | |
| `OutputType` | `PROPOSAL`, `PRESENTATION` | 결과물 유형 → 고정 섹션 구성 결정 (정책서 §2.3) |
| `ProjectMemberRole` | `OWNER`, `MEMBER` | MVP 2역할. 팀장 위임·변경 불가. 외부 검토자는 role 아님 — 링크 기반 별도 (정책서 §1.3) |
| `ProjectSectionStatus` | `COLLECTING`, `SYNTHESIZING`, `DRAFTING`, `REVIEWING`, `CONFIRMED` | 섹션 내부 5단계(단일 값). 사용자 페이즈 3단계(`COLLECT`/`COMPOSE`/`REVIEW`)는 저장하지 않고 파생 (정책서 §3.1) |
| `OpinionStatus` | `DRAFT`, `SUBMITTED` | 의견 임시/제출 |
| `IssueType` | `CONFLICT`, `GAP` | 쟁점 유형 |
| `IssueStatus` | `PENDING`, `RESOLVED` | 쟁점 처리 |
| `TeamReviewStatus` | `PENDING`, `APPROVED`, `CHANGES_REQUESTED` | 팀(내부) 검토 |
| `UnderstandingSignal` | `CLEAR`, `PARTIAL`, `UNCLEAR` | 외부 검토 결과. UI 라벨(이해됨/애매함/이해 어려움)은 화면에서 매핑 |

- 의견 수집 게이트(`OPEN`/`CLOSED` — 정책서 §4.5)는 **별도 컬럼·enum으로 저장하지 않고**
  `sectionStatus == COLLECTING` 여부에서 파생합니다.
  (GAP 보충 근거 답변은 게이트가 `CLOSED`여도 가능한 별개 채널입니다 — 정책서 §5.1.4)

**섹션 오버레이 플래그** — `sectionStatus`와 **독립**이며 한 섹션이 동시에 여러 개 가질 수 있습니다 (정책서 §8).
`sectionStatus` 안에 넣지 말 것.

| 플래그 | 값 | 의미 |
| --- | --- | --- |
| `driftStatus` | `NONE`, `REVIEW_REQUIRED` | 상위 섹션 변경으로 재검토 필요 (정책서 §6.4) |
| `aiCheckStatus` | `CURRENT`, `OUTDATED` | AI 사전 검토가 현재 본문 기준 최신인지 (확정 필수 조건) |
| `synthesisStale` | `boolean` | 재정리 필요. **enum 아님** |

### 5.8 ErrorCode 단일 소스와 외부 코드 규칙

- 실패 코드의 단일 소스는
  `src/main/java/com/wevo/backend/global/exception/ErrorCode.java`입니다.
  `CLAUDE.md`나 API 명세에 enum 전체 목록을 복사해 별도의 정본을 만들지 않습니다.
- Enum 상수명은 개발자가 의미를 이해하기 쉬운 `UPPER_SNAKE_CASE`로 작성하고, 외부 API의 `code`는
  도메인 prefix와 3자리 숫자로 작성합니다. 두 값은 역할이 다릅니다.
- 외부 실패 코드는 `^[A-Z]{1,3}\d{3}$` 형식을 만족하고 전체 `ErrorCode`에서 유일해야 합니다.
- 이미 외부에 공개된 코드는 다른 의미로 재사용하거나 임의로 변경하지 않습니다.

| prefix | 도메인 |
| --- | --- |
| `C` | 공통(Common) |
| `A` | 인증(Auth) |
| `U` | 사용자(User) |
| `P` | 프로젝트(Project) |
| `S` | 섹션(Section) |
| `O` | 의견(Opinion) |
| `I` | 쟁점(Issue) |
| `AI` | AI |
| `R` | 리뷰(Review) |
| `E` | 내보내기(Export) |

**공통 코드와 도메인 코드 선택**

- 요청 형식·필수값 검증 실패는 `INVALID_INPUT`(`C001`)을 사용합니다.
- 클라이언트가 원인을 구분해 별도 행동을 할 필요가 없는 일반 충돌은 `CONFLICT`(`C003`)을 재사용합니다.
- 클라이언트가 해당 원인을 독립적으로 분기해야 할 때만 도메인 전용 `ErrorCode`를 추가합니다.
  HTTP 상태가 같다는 이유만으로 모든 오류를 하나의 코드로 합치거나, 메시지만 다른 전용 코드를
  무분별하게 추가하지 않습니다.

예를 들어 섹션 템플릿 생성 기능을 추가하면서 `(resultType, sectionKey)` 중복을 클라이언트가 별도로
처리해야 한다면 의미 중심의 상수명과 `S` prefix 외부 코드를 함께 정의합니다.

```java
SECTION_TEMPLATE_DUPLICATED(
        HttpStatus.CONFLICT,
        "S010",
        "중복된 섹션 템플릿입니다."
);
```

별도 처리가 필요 없다면 새 코드를 만들지 않고 `CONFLICT`(`C003`)을 사용합니다.

**구현과 API 명세 동기화**

- API 명세(`docs/API_SPEC.md`)의 실패 응답에는 Enum 상수명이 아니라 실제 외부 코드(`C001`, `S003`)를 기록합니다.
- 계약 우선 개발을 위해 **구현 전에 명세를 먼저 작성할 수 있습니다.** 대신 각 API에 상태 표기가
  **필수**입니다: `초안(Draft)` → `확정(Confirmed)` → `구현됨(Implemented)`.
  상태 표기가 누락된 명세는 `초안(Draft)`으로 간주합니다 — 미구현 API가 구현된 것처럼
  읽히는 사고를 막기 위한 안전한 기본값입니다.
  **상태 전환 조건**: `Draft` 계약은 구현에 착수하지 않습니다. FE 합의(또는 명시적 팀 승인)로
  `Confirmed`가 된 뒤 구현을 시작하고, 구현이 머지될 때 해당 API의 상태를 `Implemented`로 갱신합니다.
- 새 오류를 추가할 때는 `ErrorCode`, 예외 발생 지점, 전역 예외 매핑, API 명세를 같은 변경에서 갱신합니다.
- 중복처럼 데이터 무결성에 의존하는 오류는 애플리케이션의 사전 검사만으로 보장하지 않습니다.
  DB 유니크 제약을 함께 두고, 동시 요청에서 발생하는 제약 위반도 합의한 `ErrorCode`로 변환합니다.
- `ErrorCode`의 모든 외부 코드가 위 정규식을 만족하고 서로 중복되지 않는지 검증하는 테스트를 유지합니다.

### 5.9 전역 예외 처리

- 비즈니스 예외는 `ErrorCode`를 담은 `BusinessException`으로 던집니다.
  필드 단위 사유가 있으면 `BusinessException(ErrorCode, List<FieldError>)`를 사용합니다.
- 컨트롤러에서 예외를 HTTP 응답으로 직접 변환하지 않습니다.
  서비스·게이트웨이는 재시도, 복구, 인프라 예외의 도메인 예외(`BusinessException`) 변환을 위해
  예외를 처리할 수 있습니다.
- 최종 HTTP 응답 변환은 `global.exception.GlobalExceptionHandler`(`@RestControllerAdvice`)가
  한 곳에서 담당하며, §5.4의 실패 응답 포맷으로 변환합니다.
- `@Valid` 검증 실패(`MethodArgumentNotValidException`)도 핸들러에서
  `INVALID_INPUT`(`C001`) + 필드별 `FieldError` 목록으로 변환합니다.
- HTTP 상태 코드는 `ErrorCode.status`를 사용해 `ResponseEntity`에 실어 반환합니다.

---

## 6. 계층 / 트랜잭션 규칙

- **컨트롤러**: 요청 검증(`@Valid`)과 DTO ↔ 서비스 호출만 담당합니다.
  엔티티를 응답으로 직접 노출하지 않고 반드시 응답 DTO로 변환합니다. 비즈니스 로직 금지.
- **서비스**: 비즈니스 로직과 트랜잭션 경계를 담당합니다.
  `@Transactional`은 서비스 계층에만 선언하고, 조회 전용 메서드는 `@Transactional(readOnly = true)`를 사용합니다.
- **리포지토리**: 데이터 접근만 담당합니다. 비즈니스 판단을 쿼리 계층에 넣지 않습니다.
- **DTO**: `dto/request`, `dto/response`에 두고 도메인 간 DTO 재사용을 지양합니다.
- **도메인 간 접근**: 타 도메인의 리포지토리·엔티티를 직접 참조하지 않습니다.
  해당 도메인이 공개한 서비스 또는 조회 전용 인터페이스를 통해서만 접근합니다.
  서비스 간 **순환 의존이 생기면** 그대로 두지 않고, 인터페이스 분리나 도메인 이벤트로
  의존 방향을 한쪽으로 정리합니다.
- **엔티티**: 무분별한 `@Setter`/`@Data`를 금지하고, 상태 변경은 의도가 드러나는 메서드로 제공합니다.
  생성·수정 시각은 `global.common.BaseTimeEntity` 상속으로 처리합니다.

---

## 7. 보안 · AI 데이터 처리

- **로깅 금지 대상**: Access/Refresh 토큰, `Authorization` 헤더, 비밀 값(API 키·비밀번호)은
  어떤 로그 레벨에서도 남기지 않습니다. 이메일 등 개인 식별정보는 로그에 필요한 경우 마스킹합니다.
- **행위자 식별**: 행위자(요청 사용자) ID는 JWT/`SecurityContext`(`AuthPrincipal`)에서만 가져옵니다.
  요청 본문·쿼리 파라미터로 전달된 `userId`를 행위자로 신뢰하지 않습니다.
- **객체 단위 권한 검사**: 인증(로그인 여부)만으로 충분하지 않습니다. 리소스를 다루는 서비스는
  "요청 사용자가 이 프로젝트/섹션의 멤버(또는 OWNER)인가"를 반드시 검증합니다.
  권한 없는 접근의 응답은 §5.6의 존재 숨김 규칙(`403`/`404`)을 따릅니다.
- **AI 전송 최소화**: 프롬프트에는 해당 기능에 필요한 데이터만 담습니다.
  이메일 등 개인 식별정보와 토큰·비밀 값은 AI에 전송하지 않습니다.
- **AI 출력 검증**: AI 구조화 출력은 스키마 검증을 통과한 뒤에만 사용하고, 실패 시 재시도·오류
  처리합니다. AI 출력을 검증 없이 저장하거나 클라이언트 응답에 그대로 싣지 않습니다.
- **AI 로깅**: 프롬프트·응답 원문은 로그에 남기지 않습니다
  (Spring AI observation의 `log-prompt` / `log-completion`은 `false` 유지).
  사용량 메타데이터(호출 횟수·토큰·비용)만 기록합니다.

---

## 8. 테스트 컨벤션

- 테스트는 `src/test/java`에 프로덕션 코드와 동일한 패키지 구조로 둡니다.
- 클래스 네이밍(역할이 이름에서 드러나게):

| 유형 | 접미사 | 예시 |
| --- | --- | --- |
| 서비스 단위 테스트 | `{대상}Test` | `ProjectServiceTest` |
| 컨트롤러 슬라이스 테스트 | `{대상}WebMvcTest` | `OpinionControllerWebMvcTest` |
| 통합 테스트 | `{대상}IntegrationTest` | `AiUsageLifecycleIntegrationTest` |

- 테스트 메서드명은 검증하는 동작이 드러나게 작성하고, given-when-then 구조를 권장합니다.
- **테스트 계층별 DB 사용 기준**:
  - 단위 테스트(서비스 등)와 컨트롤러 슬라이스(`@WebMvcTest`)는 DB를 사용하지 않습니다(모킹).
  - H2는 **DB 동작 자체를 검증하지 않는 경량 통합 테스트에 한정**해 사용합니다.
  - DB 동작(SQL·제약조건·동시성·잠금 등)을 검증하는 저장소·통합 테스트는
    **Testcontainers 기반 PostgreSQL**을 사용합니다.
- 새 기능·버그 수정에는 해당 동작을 검증하는 테스트를 함께 추가합니다.
- **PR 생성 전 `./gradlew test`가 통과해야 합니다.**

---

## 9. Git 컨벤션

### 9-1. 브랜치 전략

- `dev` = 기본(default) 브랜치, 개발 통합용 — **직접 push 금지**
- 기능 브랜치는 `dev`에서 분기 후 PR로 `dev`에 병합
- 머지 방식: **Merge commit** (PR 단위 이력·개별 커밋 보존)

```bash
git switch dev
git pull --ff-only origin dev
git switch -c feat/12-notice-card   # 타입/이슈번호-기능명
```

### 9-2. 작업 순서 (Step by Step)

1. **이슈 발행** — 하나의 이슈 = 하나의 기능. 템플릿(`.github/ISSUE_TEMPLATE`) 사용, Assignee·Label·체크리스트 작성
2. **로컬 최신화** — `git switch dev && git pull --ff-only origin dev`
3. **브랜치 생성** — `타입/이슈번호-기능명`
4. **개발 & 커밋** — 컨벤션 준수
5. **푸시 & PR 생성** — `./gradlew test` 통과 확인 후 푸시, PR 템플릿 작성, `Closes #이슈번호` 연결
6. **CI 통과** — `dev` 대상 PR은 GitHub Actions(`.github/workflows/ci.yml`)가 `./gradlew build`를 실행합니다. 실패 시 머지 불가.
7. **코드 리뷰** — 2명 이상 Approve 필수

### 9-3. 네이밍 컨벤션

| 항목 | 규칙 | 예시 |
| --- | --- | --- |
| 브랜치 | `타입/이슈번호-기능명` | `feat/79-mypage-features` |
| 커밋 메시지 | `타입: 설명 (#이슈번호)` | `feat: 북마크 토글 (#84)` |
| PR 제목 | `타입(#이슈번호): 핵심 내용` | `fix(#83): 북마크 즉시 반영` |

**사용 타입**

| 타입 | 용도 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 구조 개선(동작 변화 없음) |
| `test` | 테스트 추가·수정(프로덕션 코드 변화 없음) |
| `style` | 포맷/스타일(코드 동작 영향 없음) |
| `chore` | 설정/빌드/기타 잡무 |
| `docs` | 문서 |
