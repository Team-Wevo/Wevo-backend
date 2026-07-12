# CLAUDE.md

이 문서는 본 프로젝트(`com.wevo.backend`)의 코드/협업 컨벤션을 정의합니다.
코드를 작성·수정할 때는 아래 규칙을 항상 준수합니다.

---

## 1. 패키지 / 폴더 구조 — 도메인형

기능(도메인) 단위로 패키지를 분리합니다. 공통 인프라성 코드는 `global` 하위에 둡니다.

```
com.wevo.backend
├─ global
│  ├─ config        # 스프링 설정, Bean 등록
│  ├─ security      # 인증/인가, 시큐리티 설정
│  ├─ exception     # 공통 예외, 전역 예외 핸들러, ErrorCode
│  └─ response      # 공통 응답 포맷(ApiResponse 등)
├─ auth
├─ user
├─ project
├─ section
├─ opinion
├─ ai
├─ review
└─ export
```

### 도메인 패키지 내부 구조(권장)

각 도메인 패키지는 계층별로 하위 패키지를 둡니다.

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
- 도메인별 Enum은 해당 도메인의 `domain` 패키지에 둡니다. (공통 Enum은 §5.7 참고)

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

계층/역할이 이름에서 드러나도록 접미사를 통일합니다.

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

## 4. DB 네이밍

- 테이블·컬럼 모두 **snake_case**를 사용합니다. (예: `created_at`, `total_price`, `user_id`)
- 자바 엔티티 필드는 lowerCamelCase로 두고, 매핑은 ORM 설정(또는 `@Column(name = "...")`)으로 처리합니다.
- Enum 컬럼은 **문자열로 저장**합니다. (예: JPA `@Enumerated(EnumType.STRING)`)

---

## 5. API 공통 규칙

### 5.1 Base URL

```
/api
```

- 공개 리뷰 제출용 API는 인증 없이 접근해야 하므로 별도 경로를 사용합니다.

```
/public
```

### 5.2 인증 방식

- 인증 방식: `Bearer JWT`
- 헤더 예시:

```
Authorization: Bearer {accessToken}
```

- 인증이 필요한 API는 별도 표기하지 않는 한 **기본적으로 로그인 사용자만 호출 가능**합니다.
- **공개 리뷰 API(`/public`)만 예외적으로 인증 없이 접근 가능**합니다.

### 5.3 Content-Type

```
Content-Type: application/json
```

### 5.4 공통 응답 형식

모든 API 응답은 `global.response.ApiResponse<T>` 래퍼로 감싸 동일한 형태로 반환합니다.
모든 응답에는 `timestamp`(ISO-8601)를 포함합니다.

**성공 응답** — `data`에 결과를 담고 `errors`는 생략합니다.

```json
{
  "success": true,
  "code": "PROJECT_CREATED",
  "message": "프로젝트가 생성되었습니다.",
  "data": {
    "id": 1
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**실패 응답** — `errors`에 상세 사유를 담고 `data`는 생략(`null`)합니다.

```json
{
  "success": false,
  "code": "PROJECT_NOT_FOUND",
  "message": "프로젝트를 찾을 수 없습니다.",
  "errors": [
    {
      "field": "projectId",
      "reason": "invalid value"
    }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

- `code`는 단순 `SUCCESS`/`ERROR`가 아닌 **상황을 식별할 수 있는 의미 있는 코드**를 사용합니다.
  (예: `PROJECT_CREATED`, `OK`, `PROJECT_NOT_FOUND`)
- 성공 코드는 별도 Enum으로 강제하지 않고 의미 있는 문자열로 전달합니다.
- `errors`는 검증 실패 등 필드 단위 사유가 있을 때 사용하며, 사유가 없으면 생략합니다.

### 5.5 페이지네이션 형식

목록 조회 API 중 데이터가 많아질 수 있는 API는 페이지네이션을 사용합니다.

**요청 파라미터**

| 파라미터 | 설명 | 예시 |
| --- | --- | --- |
| `page` | 0부터 시작하는 페이지 번호 | `0` |
| `size` | 페이지 크기 | `20` |
| `sort` | 정렬 기준 (`필드,방향`) | `createdAt,desc` |

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
| `204 No Content` | 삭제 또는 해제 성공 |
| `400 Bad Request` | 잘못된 요청 |
| `401 Unauthorized` | 인증 실패 |
| `403 Forbidden` | 권한 없음 |
| `404 Not Found` | 대상 리소스 없음 |
| `409 Conflict` | 상태 충돌, 중복 참여, 버전 충돌 |
| `422 Unprocessable Entity` | 업무 규칙 위반 |
| `500 Internal Server Error` | 서버 오류 |

### 5.7 공통 Enum

문자열 Enum 저장 원칙을 따릅니다. (타입명은 UpperCamelCase, 상수는 UPPER_SNAKE_CASE — §2 참고)

> **기준**: 제품 "동작"에 해당하는 값은 **제품 정책서(§8 상태값)가 단일 기준(source of truth)** 입니다.
> 아래 표는 정책서에 맞춰 확정한 코드 enum 이름이며, 정책과 코드가 갈리면 **정책서 의미를 따릅니다.**
> 정책이 규정하지 않는 **BE 내부 상태**(계정·AI 호출 로그 등)는 BE 재량으로 관리합니다.
> 이 표가 코드 enum의 **단일 정본**입니다. 상태 추가·변경 시 여기와 엔티티를 함께 갱신하세요.
> (⏳ = 해당 도메인 구현 시 도메인 오너가 생성)

**제품 상태 Enum (정책서 §8 기준)**

| Enum | 값 | 비고 |
| --- | --- | --- |
| `ProjectStatus` | `DRAFT`, `ACTIVE`, `COMPLETED`, `ARCHIVED` | |
| `OutputType` | `PROPOSAL`, `PRESENTATION` | 결과물 유형 → 고정 섹션 구성 결정 (§2.3) |
| `ProjectMemberRole` | `OWNER`, `MEMBER` | MVP 2역할. 팀장 위임·변경 불가. 외부 검토자는 role 아님 — 링크 기반 별도 (§1.3) |
| `ProjectSectionStatus` | `COLLECTING`, `SYNTHESIZING`, `DRAFTING`, `REVIEWING`, `CONFIRMED` | 섹션 내부 5단계(단일 값). 화면 4단계(시작전/작성중/작성완료/문제있음)는 저장 X → 파생 |
| `OpinionStatus` ⏳ | `DRAFT`, `SUBMITTED` | 의견 임시/제출 |
| `CollectGate` ⏳ | `OPEN`, `CLOSED` | 의견 수집 게이트 |
| `IssueType` ⏳ | `CONFLICT`, `GAP` | 쟁점 유형 |
| `IssueStatus` ⏳ | `PENDING`, `RESOLVED` | 쟁점 처리 |
| `TeamReviewStatus` ⏳ | `PENDING`, `APPROVED`, `CHANGES_REQUESTED` | 팀(내부) 검토 |
| `UnderstandingSignal` | `CLEAR`, `PARTIAL`, `UNCLEAR` | 외부 검토 결과. **UI 라벨**: 이해됨/애매함/이해 어려움 (코드=영문, 화면=한글 매핑) |

**섹션 오버레이 플래그** — `sectionStatus`와 **독립**이며 한 섹션이 동시에 여러 개 가질 수 있음 (§8). `sectionStatus` 안에 넣지 말 것.

| 플래그 | 값 | 의미 |
| --- | --- | --- |
| `driftStatus` ⏳ | `NONE`, `REVIEW_REQUIRED` | 상위 섹션 변경으로 재검토 필요 (§6.4) |
| `aiCheckStatus` ⏳ | `CURRENT`, `OUTDATED` | AI 사전 검토가 현재 본문 기준 최신인지 (확정 필수 조건) |
| `synthesisStale` ⏳ | `boolean` | 재정리 필요(기존 AI 정리·초안이 헌 것이 됨). **enum 아님** |

**BE 내부 상태 Enum (정책 무관 — BE 재량으로 유지)**

| Enum | 값 | 비고 |
| --- | --- | --- |
| `UserStatus` | `ACTIVE`, `INACTIVE`, `WITHDRAWN` | 계정 상태 |
| `AuthProvider` | `GOOGLE`, `KAKAO` | 소셜 제공자 |
| `AiRequestStatus` | `REQUESTED`, `SUCCEEDED`, `FAILED` | AI 호출 로그·재시도 |
| `TemplateDependencyType` | `REQUIRES`, `BLOCKS`, `RECOMMENDS` | ⚠️ **단순화 검토 대상** — 우리 의존(`dependsOn`)은 드리프트 단일 종류라 타입 구분이 불필요할 수 있음 |

### 5.8 ApiResponse / ErrorCode 구현

**ApiResponse** (`global.response`) — `null` 필드는 직렬화에서 제외합니다.

```java
package com.wevo.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.global.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;                  // 성공 시
    private final List<FieldError> errors; // 실패 시
    private final LocalDateTime timestamp;

    private ApiResponse(boolean success, String code, String message,
                        T data, List<FieldError> errors) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.errors = errors;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, List<FieldError> errors) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, errors);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }
}
```

**FieldError** (`global.response`) — 필드 단위 오류 사유를 표현합니다.

```java
package com.wevo.backend.global.response;

import lombok.Getter;

@Getter
public class FieldError {

    private final String field;
    private final String reason;

    public FieldError(String field, String reason) {
        this.field = field;
        this.reason = reason;
    }
}
```

**ErrorCode** (`global.exception`) — 코드/메시지/HTTP 상태를 한 곳에서 관리합니다.
코드 prefix는 도메인 단위로 구분합니다. (공통 `C`, auth `A`, user `U`, project `P` …)

```java
package com.wevo.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력입니다."),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "C002", "업무 규칙을 위반했습니다."),
    CONFLICT(HttpStatus.CONFLICT, "C003", "요청이 현재 상태와 충돌합니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 오류가 발생했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),

    // Project
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "프로젝트를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### 5.9 전역 예외 처리

- 비즈니스 예외는 `ErrorCode`를 담은 커스텀 예외(`BusinessException` 등)로 던집니다.
- 필드 단위 사유가 있으면 `BusinessException(ErrorCode, List<FieldError>)`를 사용합니다.
- `@RestControllerAdvice`(`global.exception.GlobalExceptionHandler`)에서 한 곳에 모아 처리하고,
  위 실패 응답 포맷(`success: false`, `errors`, `timestamp`)으로 변환합니다.
- HTTP 상태 코드는 `ErrorCode.status`를 사용해 `ResponseEntity`에 실어 반환합니다.

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    return ResponseEntity
            .status(errorCode.getStatus())
            .body(ApiResponse.error(errorCode, e.getErrors()));
}
```

---

## 6. Git 컨벤션

### 6-1. 브랜치 전략

- `dev` = 기본(default) 브랜치, 개발 통합용 — **직접 push 금지**
- 기능 브랜치는 `dev`에서 분기 후 PR로 `dev`에 병합
- 머지 방식: **Merge commit** (PR 단위 이력·개별 커밋 보존)

```bash
git switch dev
git pull origin dev
git switch -c feat/12-notice-card   # 타입/이슈번호-기능명
```

### 6-2. 작업 순서 (Step by Step)

1. **이슈 발행** — 하나의 이슈 = 하나의 기능. 템플릿(`.github/ISSUE_TEMPLATE`) 사용, Assignee·Label·체크리스트 작성
2. **로컬 최신화** — `git switch dev && git pull origin dev`
3. **브랜치 생성** — `타입/이슈번호-기능명`
4. **개발 & 커밋** — 컨벤션 준수 (pre-commit 훅이 lint/format 자동 적용)
5. **푸시 & PR 생성** — PR 템플릿 작성, `Closes #이슈번호` 연결
6. **코드 리뷰** — 2명 이상 Approve 필수

### 6-3. 네이밍 컨벤션

| 항목 | 규칙 | 예시 |
| --- | --- | --- |
| 브랜치 | `타입/이슈번호-기능명` | `feat/79-mypage-features` |
| 커밋 메시지 | `타입: 설명 (#이슈번호)` | `feat: 북마크 토글 (#84)` |
| PR 제목 | `타입(#이슈번호): 핵심 내용` | `Fix(#83): 북마크 즉시 반영` |

**사용 타입**

| 타입 | 용도 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 구조 개선(동작 변화 없음) |
| `style` | 포맷/스타일(코드 동작 영향 없음) |
| `chore` | 설정/빌드/기타 잡무 |
| `docs` | 문서 |