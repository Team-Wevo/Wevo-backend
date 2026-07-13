# Wevo API 명세서 (노션 DB 전체 export)

> 노션 페이지 "API 명세서" 하위 데이터베이스 "Wevo API 명세"의 64개 엔드포인트 전체를 하나의 Markdown으로 정리한 문서입니다.
> ⚠️ 이 문서는 노션 DB에 등록된 내용을 그대로 옮긴 것입니다. `ProjectMemberRole`(OWNER/EDITOR/VIEWER), `ProjectSectionStatus`(NOT_STARTED/COLLECTING_OPINIONS/...) 등 일부 필드는 최신 제품 정책서 v2(OWNER/MEMBER, COLLECTING→SYNTHESIZING→DRAFTING→REVIEWING→CONFIRMED)와 다를 수 있습니다 — 팀 컨벤션 정합화는 별도 작업입니다.
> 생성일: 2026-07-13

## 1. Auth

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/auth/logout` | 로그아웃 |
| POST | `/api/auth/social-login` | 소셜 로그인 (구글/카카오) |
| POST | `/api/auth/token/refresh` | 토큰 재발급 |

#### POST /api/auth/logout — 로그아웃

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/auth/logout` |
| **도메인** | auth |
| **설명** | 현재 로그인 세션을 종료하고 서버에 저장된 리프레시 토큰을 무효화합니다. |
| **인증** | ✅ 필요 |
| **권한** | 로그인 사용자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `refreshToken` | `String` | ✅ | not blank | 무효화할 리프레시 토큰 |

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data` | `null` | 추가 반환 데이터 없음 |

```json
{
  "success": true,
  "code": "LOGOUT_SUCCESS",
  "message": "로그아웃되었습니다.",
  "data": null,
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | `refreshToken` 누락 또는 빈 문자열 |
| `401` | `UNAUTHORIZED` | 인증이 필요합니다. | 액세스 토큰 누락 또는 만료 |
| `401` | `INVALID_REFRESH_TOKEN` | 유효하지 않은 리프레시 토큰입니다. | 잘못된 형식 또는 위조 토큰 |
| `404` | `REFRESH_TOKEN_NOT_FOUND` | 저장된 리프레시 토큰이 없습니다. | 이미 로그아웃되었거나 무효화된 토큰 |

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다.",
  "errors": [
    { "field": "Authorization", "reason": "missing or expired token" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### POST /api/auth/social-login — 소셜 로그인

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/auth/social-login` |
| **도메인** | auth |
| **설명** | 소셜 로그인 토큰을 검증해 사용자 계정을 식별하고 액세스 토큰을 발급합니다. |
| **인증** | ❌ 불필요 |
| **권한** | 공개 |

**Request Header**
```
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `provider` | `AuthProvider` | ✅ | `GOOGLE`, `KAKAO` | 소셜 로그인 제공자 |
| `providerAccessToken` | `String` | ✅ | not blank | 제공자 액세스 토큰 |

```json
{
  "provider": "GOOGLE",
  "providerAccessToken": "google-access-token"
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.accessToken` | `String` | 액세스 토큰 |
| `data.refreshToken` | `String` | 리프레시 토큰 |
| `data.user.id` | `Long` | 사용자 ID |
| `data.user.name` | `String` | 사용자 이름 |
| `data.user.email` | `String` | 사용자 이메일 |
| `data.user.status` | `UserStatus` | 사용자 상태 |
| `data.authAccount.provider` | `AuthProvider` | 연동 제공자 |

```json
{
  "success": true,
  "code": "LOGIN_SUCCESS",
  "message": "로그인에 성공했습니다.",
  "data": {
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "user": {
      "id": 1,
      "name": "홍길동",
      "email": "user@example.com",
      "status": "ACTIVE"
    },
    "authAccount": {
      "id": 10,
      "provider": "GOOGLE",
      "providerUserId": "google-123"
    }
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `UNSUPPORTED_PROVIDER` | 지원하지 않는 로그인 제공자입니다. | 허용되지 않은 provider 값 |
| `401` | `INVALID_SOCIAL_TOKEN` | 소셜 토큰 검증에 실패했습니다. | providerAccessToken이 유효하지 않음 |
| `403` | `USER_WITHDRAWN` | 탈퇴한 사용자입니다. | 상태가 `WITHDRAWN`인 계정 |

```json
{
  "success": false,
  "code": "INVALID_SOCIAL_TOKEN",
  "message": "소셜 토큰 검증에 실패했습니다.",
  "errors": [
    { "field": "providerAccessToken", "reason": "invalid token" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### POST /api/auth/token/refresh — 토큰 재발급

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/auth/token/refresh` |
| **도메인** | auth |
| **설명** | 리프레시 토큰을 검증해 새 액세스 토큰을 발급합니다. |
| **인증** | ❌ 불필요 |
| **권한** | 유효한 리프레시 토큰 보유자 |

**Request Header**
```
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `refreshToken` | `String` | ✅ | not blank | 재발급에 사용할 리프레시 토큰 |

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.accessToken` | `String` | 새로 발급된 액세스 토큰 |

```json
{
  "success": true,
  "code": "TOKEN_REFRESHED",
  "message": "토큰이 재발급되었습니다.",
  "data": {
    "accessToken": "new-jwt-access-token"
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | `refreshToken` 누락 또는 빈 문자열 |
| `401` | `INVALID_REFRESH_TOKEN` | 유효하지 않은 리프레시 토큰입니다. | 토큰 형식 오류 또는 위조 토큰 |
| `401` | `REFRESH_TOKEN_EXPIRED` | 만료된 리프레시 토큰입니다. | 토큰 만료 |
| `404` | `REFRESH_TOKEN_NOT_FOUND` | 저장된 리프레시 토큰이 없습니다. | 서버에 저장된 토큰이 없거나 이미 무효화됨 |

```json
{
  "success": false,
  "code": "REFRESH_TOKEN_EXPIRED",
  "message": "만료된 리프레시 토큰입니다.",
  "errors": [
    { "field": "refreshToken", "reason": "expired token" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

## 2. User

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/users/me` | 내 프로필 조회 |
| GET | `/api/users/{userId}` | 특정 사용자 조회 |
| GET | `/api/users/{userId}/auth-accounts` | 연동 소셜 계정 목록 조회 |
| PATCH | `/api/users/me` | 내 프로필 수정 |

#### GET /api/users/me — 내 정보 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/users/me` |
| **도메인** | user |
| **설명** | 현재 로그인한 사용자의 기본 정보와 연동 계정 목록을 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 로그인 사용자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 사용자 ID |
| `data.name` | `String` | 사용자 이름 |
| `data.email` | `String` | 사용자 이메일 |
| `data.profileImageUrl` | `String` | 프로필 이미지 URL |
| `data.status` | `UserStatus` | 사용자 상태 |
| `data.authAccounts` | `Array` | 연동 계정 목록 |

```json
{
  "success": true,
  "code": "USER_PROFILE_FETCHED",
  "message": "내 정보를 조회했습니다.",
  "data": {
    "id": 1,
    "name": "홍길동",
    "email": "user@example.com",
    "profileImageUrl": "https://cdn.example.com/profile.png",
    "status": "ACTIVE",
    "authAccounts": [
      {
        "id": 10,
        "provider": "GOOGLE",
        "providerUserId": "google-123"
      }
    ]
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 누락 또는 만료 |
| `404` | `USER_NOT_FOUND` | 사용자를 찾을 수 없습니다. | 토큰의 사용자 정보가 존재하지 않음 |

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다.",
  "errors": [
    { "field": "Authorization", "reason": "missing or expired token" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### GET /api/users/{userId} — 사용자 기본 정보 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/users/{userId}` |
| **도메인** | user |
| **설명** | 특정 사용자의 기본 정보를 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 본인 또는 같은 프로젝트 멤버 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | `Long` | ✅ | 조회 대상 사용자 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 사용자 ID |
| `data.name` | `String` | 사용자 이름 |
| `data.email` | `String` | 이메일 |
| `data.profileImageUrl` | `String` | 프로필 이미지 URL |
| `data.status` | `UserStatus` | 사용자 상태 |

```json
{
  "success": true,
  "code": "USER_FETCHED",
  "message": "사용자 정보를 조회했습니다.",
  "data": {
    "id": 2,
    "name": "김민수",
    "email": "minsu@example.com",
    "profileImageUrl": null,
    "status": "ACTIVE"
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 누락 또는 만료 |
| `403` | `FORBIDDEN` | 조회 권한이 없습니다. | 본인/동일 프로젝트 멤버가 아님 |
| `404` | `USER_NOT_FOUND` | 사용자를 찾을 수 없습니다. | userId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "USER_NOT_FOUND",
  "message": "사용자를 찾을 수 없습니다.",
  "errors": [
    { "field": "userId", "reason": "not found" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### GET /api/users/{userId}/auth-accounts — 사용자 연동 계정 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/users/{userId}/auth-accounts` |
| **도메인** | user |
| **설명** | 특정 사용자의 소셜 연동 계정 목록을 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 본인 또는 관리자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | `Long` | ✅ | 조회 대상 사용자 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.userId` | `Long` | 사용자 ID |
| `data.authAccounts` | `Array` | 연동 계정 목록 |

```json
{
  "success": true,
  "code": "AUTH_ACCOUNTS_FETCHED",
  "message": "연동 계정을 조회했습니다.",
  "data": {
    "userId": 1,
    "authAccounts": [
      {
        "id": 10,
        "provider": "GOOGLE",
        "providerUserId": "google-123"
      }
    ]
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 누락 또는 만료 |
| `403` | `FORBIDDEN` | 조회 권한이 없습니다. | 본인/관리자가 아님 |
| `404` | `USER_NOT_FOUND` | 사용자를 찾을 수 없습니다. | userId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "조회 권한이 없습니다.",
  "errors": [
    { "field": "userId", "reason": "not allowed" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### PATCH /api/users/me — 내 정보 수정

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/users/me` |
| **도메인** | user |
| **설명** | 현재 로그인한 사용자의 이름과 프로필 이미지를 수정합니다. |
| **인증** | ✅ 필요 |
| **권한** | 로그인 사용자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `name` | `String` | ❌ | 1~100자 | 수정할 사용자 이름 |
| `profileImageUrl` | `String` | ❌ | URL 형식 | 수정할 프로필 이미지 URL |

```json
{
  "name": "홍길동",
  "profileImageUrl": "https://cdn.example.com/profile-new.png"
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 사용자 ID |
| `data.name` | `String` | 수정된 이름 |
| `data.profileImageUrl` | `String` | 수정된 프로필 이미지 URL |

```json
{
  "success": true,
  "code": "USER_PROFILE_UPDATED",
  "message": "내 정보가 수정되었습니다.",
  "data": {
    "id": 1,
    "name": "홍길동",
    "profileImageUrl": "https://cdn.example.com/profile-new.png"
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | 이름 길이, URL 형식 오류 |
| `401` | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 누락 또는 만료 |

```json
{
  "success": false,
  "code": "INVALID_INPUT",
  "message": "잘못된 입력입니다.",
  "errors": [
    { "field": "name", "reason": "size must be between 1 and 100" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

## 3. Project

| Method | Path | 설명 |
| --- | --- | --- |
| DELETE | `/api/projects/{projectId}` | 프로젝트 삭제 |
| DELETE | `/api/projects/{projectId}/members/{memberId}` | 프로젝트 멤버 추방 |
| GET | `/api/projects` | 프로젝트 목록 조회 |
| GET | `/api/projects/{projectId}` | 프로젝트 상세 조회 |
| GET | `/api/projects/{projectId}/invite-links` | 초대 링크 목록 조회 |
| GET | `/api/projects/{projectId}/members` | 프로젝트 멤버 목록 조회 |
| PATCH | `/api/invite-links/{inviteLinkId}/deactivate` | 초대 링크 비활성화 |
| PATCH | `/api/projects/{projectId}` | 프로젝트 수정 |
| PATCH | `/api/projects/{projectId}/members/{memberId}/role` | 프로젝트 멤버 역할 변경 |
| POST | `/api/invite-links/{token}/accept` | 초대 링크로 프로젝트 참여 |
| POST | `/api/projects` | 프로젝트 생성 |
| POST | `/api/projects/{projectId}/invite-links` | 초대 링크 생성 |

#### DELETE /api/projects/{projectId} — 프로젝트 삭제

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `DELETE /api/projects/{projectId}` |
| **도메인** | project |
| **설명** | 프로젝트와 관련 하위 데이터를 삭제합니다. |
| **인증** | ✅ 필요 |
| **권한** | OWNER |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 삭제 대상 프로젝트 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 삭제 API |

```json
{}
```

**Response — 성공 (`204 No Content`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| 없음 | - | 응답 본문 없음 |

```json
Response body 없음
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `403` | `FORBIDDEN` | 삭제 권한이 없습니다. | OWNER가 아님 |
| `404` | `PROJECT_NOT_FOUND` | 프로젝트를 찾을 수 없습니다. | projectId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "PROJECT_NOT_FOUND",
  "message": "프로젝트를 찾을 수 없습니다.",
  "errors": [
    { "field": "projectId", "reason": "not found" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### DELETE /api/projects/{projectId}/members/{memberId} — 프로젝트 멤버 제거

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `DELETE /api/projects/{projectId}/members/{memberId}` |
| **도메인** | project |
| **설명** | 프로젝트에서 특정 멤버를 제거합니다. |
| **인증** | ✅ 필요 |
| **권한** | OWNER |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 프로젝트 ID |
| `memberId` | `Long` | ✅ | 프로젝트 멤버 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 삭제 API |

```json
{}
```

**Response — 성공 (`204 No Content`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| 없음 | - | 응답 본문 없음 |

```json
Response body 없음
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `403` | `FORBIDDEN` | 멤버 제거 권한이 없습니다. | OWNER가 아님 |
| `404` | `PROJECT_MEMBER_NOT_FOUND` | 프로젝트 멤버를 찾을 수 없습니다. | memberId가 존재하지 않음 |
| `409` | `CANNOT_REMOVE_LAST_OWNER` | 마지막 OWNER는 제거할 수 없습니다. | 유일한 OWNER 제거 시도 |

```json
{
  "success": false,
  "code": "PROJECT_MEMBER_NOT_FOUND",
  "message": "프로젝트 멤버를 찾을 수 없습니다.",
  "errors": [
    { "field": "memberId", "reason": "not found" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### GET /api/projects — 프로젝트 목록 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/projects` |
| **도메인** | project |
| **설명** | 현재 로그인한 사용자가 참여 중인 프로젝트 목록을 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 로그인 사용자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `status` | `ProjectStatus` | ❌ | - | 프로젝트 상태 필터 |
| `page` | `int` | ❌ | `0` | 페이지 번호 |
| `size` | `int` | ❌ | `20` | 페이지 크기 |
| `sort` | `String` | ❌ | `createdAt,desc` | 정렬 기준 |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.content` | `Array` | 프로젝트 목록 |
| `data.content[].id` | `Long` | 프로젝트 ID |
| `data.content[].title` | `String` | 프로젝트 제목 |
| `data.content[].resultType` | `String` | 결과물 유형 |
| `data.content[].status` | `ProjectStatus` | 프로젝트 상태 |
| `data.content[].myRole` | `ProjectMemberRole` | 내 역할 |
| `data.page` | `int` | 페이지 번호 |
| `data.totalElements` | `long` | 전체 개수 |

```json
{
  "success": true,
  "code": "PROJECT_LIST_FETCHED",
  "message": "프로젝트 목록을 조회했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "title": "서비스 소개서",
        "resultType": "PROPOSAL",
        "status": "ACTIVE",
        "myRole": "OWNER"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 누락 또는 만료 |

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다.",
  "errors": [
    { "field": "Authorization", "reason": "missing or expired token" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### GET /api/projects/{projectId} — 프로젝트 상세 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/projects/{projectId}` |
| **도메인** | project |
| **설명** | 프로젝트 기본 정보, 내 역할, 섹션 진행 현황 요약을 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 프로젝트 멤버 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 프로젝트 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 프로젝트 ID |
| `data.title` | `String` | 프로젝트 제목 |
| `data.resultType` | `String` | 결과물 유형 |
| `data.audience` | `String` | 전달 대상 |
| `data.status` | `ProjectStatus` | 프로젝트 상태 |
| `data.myRole` | `ProjectMemberRole` | 내 역할 |
| `data.sectionSummary.totalCount` | `int` | 전체 섹션 수 |

```json
{
  "success": true,
  "code": "PROJECT_FETCHED",
  "message": "프로젝트를 조회했습니다.",
  "data": {
    "id": 1,
    "title": "서비스 소개서",
    "description": "초안 작성용 프로젝트",
    "resultType": "PROPOSAL",
    "audience": "투자자",
    "status": "ACTIVE",
    "myRole": "OWNER",
    "sectionSummary": {
      "totalCount": 6,
      "confirmedCount": 2
    }
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 누락 또는 만료 |
| `403` | `FORBIDDEN` | 프로젝트 접근 권한이 없습니다. | 프로젝트 멤버가 아님 |
| `404` | `PROJECT_NOT_FOUND` | 프로젝트를 찾을 수 없습니다. | projectId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "PROJECT_NOT_FOUND",
  "message": "프로젝트를 찾을 수 없습니다.",
  "errors": [
    { "field": "projectId", "reason": "not found" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### GET /api/projects/{projectId}/invite-links — 프로젝트 초대 링크 목록 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/projects/{projectId}/invite-links` |
| **도메인** | project |
| **설명** | 프로젝트에 생성된 초대 링크 목록을 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 프로젝트 멤버 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 프로젝트 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `includeInactive` | `boolean` | ❌ | `false` | 비활성 링크 포함 여부 |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.inviteLinks` | `Array` | 초대 링크 목록 |
| `data.inviteLinks[].id` | `Long` | 초대 링크 ID |
| `data.inviteLinks[].token` | `String` | 토큰 |
| `data.inviteLinks[].expiresAt` | `String` | 만료 시각 |
| `data.inviteLinks[].isActive` | `boolean` | 활성화 여부 |

```json
{
  "success": true,
  "code": "INVITE_LINKS_FETCHED",
  "message": "초대 링크 목록을 조회했습니다.",
  "data": {
    "inviteLinks": [
      {
        "id": 100,
        "token": "invite-token-value",
        "expiresAt": "2026-07-10T23:59:59",
        "isActive": true
      }
    ]
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `403` | `FORBIDDEN` | 조회 권한이 없습니다. | 프로젝트 멤버가 아님 |
| `404` | `PROJECT_NOT_FOUND` | 프로젝트를 찾을 수 없습니다. | projectId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "PROJECT_NOT_FOUND",
  "message": "프로젝트를 찾을 수 없습니다.",
  "errors": [
    { "field": "projectId", "reason": "not found" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### GET /api/projects/{projectId}/members — 프로젝트 멤버 목록 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/projects/{projectId}/members` |
| **도메인** | project |
| **설명** | 프로젝트 멤버 목록과 각 멤버의 역할을 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 프로젝트 멤버 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 프로젝트 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.members` | `Array` | 멤버 목록 |
| `data.members[].memberId` | `Long` | 멤버 엔트리 ID |
| `data.members[].userId` | `Long` | 사용자 ID |
| `data.members[].role` | `ProjectMemberRole` | 프로젝트 역할 |
| `data.members[].joinedAt` | `String` | 참여 시각 |

```json
{
  "success": true,
  "code": "PROJECT_MEMBERS_FETCHED",
  "message": "프로젝트 멤버를 조회했습니다.",
  "data": {
    "members": [
      {
        "memberId": 11,
        "userId": 1,
        "name": "홍길동",
        "email": "user@example.com",
        "role": "OWNER",
        "joinedAt": "2026-07-01T10:00:00"
      }
    ]
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `403` | `FORBIDDEN` | 조회 권한이 없습니다. | 프로젝트 멤버가 아님 |
| `404` | `PROJECT_NOT_FOUND` | 프로젝트를 찾을 수 없습니다. | projectId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "조회 권한이 없습니다.",
  "errors": [
    { "field": "projectId", "reason": "not a project member" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### PATCH /api/invite-links/{inviteLinkId}/deactivate — 초대 링크 비활성화

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/invite-links/{inviteLinkId}/deactivate` |
| **도메인** | project |
| **설명** | 특정 초대 링크를 비활성화합니다. |
| **인증** | ✅ 필요 |
| **권한** | OWNER, EDITOR |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `inviteLinkId` | `Long` | ✅ | 초대 링크 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 상태 변경 API |

```json
{}
```

**Response — 성공 (`204 No Content`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| 없음 | - | 응답 본문 없음 |

```json
Response body 없음
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `403` | `FORBIDDEN` | 비활성화 권한이 없습니다. | OWNER/EDITOR가 아님 |
| `404` | `INVITE_LINK_NOT_FOUND` | 초대 링크를 찾을 수 없습니다. | inviteLinkId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "INVITE_LINK_NOT_FOUND",
  "message": "초대 링크를 찾을 수 없습니다.",
  "errors": [
    { "field": "inviteLinkId", "reason": "not found" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### PATCH /api/projects/{projectId} — 프로젝트 수정

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/projects/{projectId}` |
| **도메인** | project |
| **설명** | 프로젝트 제목, 설명, 결과물 유형, 전달 대상, 상태를 수정합니다. |
| **인증** | ✅ 필요 |
| **권한** | OWNER, EDITOR |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 수정 대상 프로젝트 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `title` | `String` | ❌ | 1~200자 | 프로젝트 제목 |
| `description` | `String` | ❌ | 최대 5000자 | 프로젝트 설명 |
| `ideaText` | `String` | ❌ | 최대 10000자 | 아이디어 |
| `resultType` | `String` | ❌ | 예: `PROPOSAL` | 결과물 유형 |
| `audience` | `String` | ❌ | 1~200자 | 전달 대상 |
| `status` | `ProjectStatus` | ❌ | enum | 프로젝트 상태 |

```json
{
  "title": "서비스 소개서 v2",
  "description": "투자자용 소개서",
  "resultType": "PROPOSAL",
  "audience": "투자자",
  "status": "ACTIVE"
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 프로젝트 ID |
| `data.title` | `String` | 수정된 제목 |
| `data.status` | `ProjectStatus` | 수정된 상태 |

```json
{
  "success": true,
  "code": "PROJECT_UPDATED",
  "message": "프로젝트가 수정되었습니다.",
  "data": {
    "id": 1,
    "title": "서비스 소개서 v2",
    "status": "ACTIVE"
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | 잘못된 상태값, 길이 초과 |
| `403` | `FORBIDDEN` | 수정 권한이 없습니다. | OWNER/EDITOR가 아님 |
| `404` | `PROJECT_NOT_FOUND` | 프로젝트를 찾을 수 없습니다. | projectId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "수정 권한이 없습니다.",
  "errors": [
    { "field": "projectId", "reason": "insufficient role" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### PATCH /api/projects/{projectId}/members/{memberId}/role — 프로젝트 멤버 역할 변경

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/projects/{projectId}/members/{memberId}/role` |
| **도메인** | project |
| **설명** | 프로젝트 멤버의 역할을 변경합니다. |
| **인증** | ✅ 필요 |
| **권한** | OWNER |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 프로젝트 ID |
| `memberId` | `Long` | ✅ | 프로젝트 멤버 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `role` | `ProjectMemberRole` | ✅ | `OWNER`, `EDITOR`, `VIEWER` | 변경할 역할 |

```json
{
  "role": "EDITOR"
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.memberId` | `Long` | 멤버 ID |
| `data.role` | `ProjectMemberRole` | 변경된 역할 |

```json
{
  "success": true,
  "code": "PROJECT_MEMBER_ROLE_UPDATED",
  "message": "프로젝트 멤버 역할이 변경되었습니다.",
  "data": {
    "memberId": 11,
    "role": "EDITOR"
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 역할 값입니다. | 허용되지 않은 role 값 |
| `403` | `FORBIDDEN` | 역할 변경 권한이 없습니다. | OWNER가 아님 |
| `404` | `PROJECT_MEMBER_NOT_FOUND` | 프로젝트 멤버를 찾을 수 없습니다. | memberId가 존재하지 않음 |
| `409` | `CANNOT_CHANGE_LAST_OWNER` | 마지막 OWNER는 변경할 수 없습니다. | 유일한 OWNER의 역할 하향 |

```json
{
  "success": false,
  "code": "CANNOT_CHANGE_LAST_OWNER",
  "message": "마지막 OWNER는 변경할 수 없습니다.",
  "errors": [
    { "field": "role", "reason": "last owner protection" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### POST /api/invite-links/{token}/accept — 초대 링크 수락

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/invite-links/{token}/accept` |
| **도메인** | project |
| **설명** | 초대 링크를 수락하고 현재 사용자를 프로젝트 멤버로 등록합니다. |
| **인증** | ✅ 필요 |
| **권한** | 로그인 사용자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `token` | `String` | ✅ | 초대 링크 토큰 |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 수락 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.projectId` | `Long` | 참여한 프로젝트 ID |
| `data.memberId` | `Long` | 생성된 멤버 ID |
| `data.role` | `ProjectMemberRole` | 부여된 역할 |

```json
{
  "success": true,
  "code": "INVITE_ACCEPTED",
  "message": "프로젝트 초대를 수락했습니다.",
  "data": {
    "projectId": 1,
    "memberId": 15,
    "role": "VIEWER"
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 누락 또는 만료 |
| `404` | `INVITE_LINK_NOT_FOUND` | 초대 링크를 찾을 수 없습니다. | token이 존재하지 않음 |
| `409` | `INVITE_LINK_INACTIVE` | 비활성화된 초대 링크입니다. | is_active가 false |
| `409` | `INVITE_LINK_EXPIRED` | 만료된 초대 링크입니다. | expires_at이 현재 시각 이전 |
| `409` | `ALREADY_PROJECT_MEMBER` | 이미 프로젝트에 참여 중입니다. | 중복 참여 시도 |

```json
{
  "success": false,
  "code": "INVITE_LINK_EXPIRED",
  "message": "만료된 초대 링크입니다.",
  "errors": [
    { "field": "token", "reason": "expired" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### POST /api/projects — 프로젝트 생성

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/projects` |
| **도메인** | project |
| **설명** | 새 프로젝트를 생성하고 생성자를 `OWNER` 멤버로 등록합니다. |
| **인증** | ✅ 필요 |
| **권한** | 로그인 사용자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `title` | `String` | ✅ | 1~200자 | 프로젝트 제목 |
| `description` | `String` | ❌ | 최대 5000자 | 프로젝트 설명 |
| `ideaText` | `String` | ❌ | 최대 10000자 | 프로젝트 아이디어 |
| `resultType` | `String` | ✅ | 예: `PROPOSAL` | 결과물 유형 |
| `audience` | `String` | ✅ | 1~200자 | 전달 대상 |

```json
{
  "title": "서비스 소개서",
  "description": "초안 작성용 프로젝트",
  "ideaText": "AI 협업 기반 제안서 작성 툴",
  "resultType": "PROPOSAL",
  "audience": "투자자"
}
```

**Response — 성공 (`201 Created`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 생성된 프로젝트 ID |
| `data.title` | `String` | 프로젝트 제목 |
| `data.status` | `ProjectStatus` | 초기 상태 |
| `data.myRole` | `ProjectMemberRole` | 생성자 역할 |

```json
{
  "success": true,
  "code": "PROJECT_CREATED",
  "message": "프로젝트가 생성되었습니다.",
  "data": {
    "id": 1,
    "title": "서비스 소개서",
    "status": "DRAFT",
    "myRole": "OWNER"
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | 필수값 누락, 길이 초과 |
| `401` | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 누락 또는 만료 |

```json
{
  "success": false,
  "code": "INVALID_INPUT",
  "message": "잘못된 입력입니다.",
  "errors": [
    { "field": "title", "reason": "must not be blank" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### POST /api/projects/{projectId}/invite-links — 프로젝트 초대 링크 생성

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/projects/{projectId}/invite-links` |
| **도메인** | project |
| **설명** | 프로젝트 참여를 위한 초대 링크를 생성합니다. |
| **인증** | ✅ 필요 |
| **권한** | OWNER, EDITOR |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 프로젝트 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `expiresAt` | `String` | ✅ | ISO-8601 | 링크 만료 시각 |

```json
{
  "expiresAt": "2026-07-10T23:59:59"
}
```

**Response — 성공 (`201 Created`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 초대 링크 ID |
| `data.projectId` | `Long` | 프로젝트 ID |
| `data.token` | `String` | 초대 토큰 |
| `data.inviteUrl` | `String` | 초대 URL |
| `data.expiresAt` | `String` | 만료 시각 |
| `data.isActive` | `boolean` | 활성화 여부 |

```json
{
  "success": true,
  "code": "INVITE_LINK_CREATED",
  "message": "초대 링크가 생성되었습니다.",
  "data": {
    "id": 100,
    "projectId": 1,
    "token": "invite-token-value",
    "inviteUrl": "https://wevo.app/invite/invite-token-value",
    "expiresAt": "2026-07-10T23:59:59",
    "isActive": true
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | expiresAt 형식 오류, 과거 시각 |
| `403` | `FORBIDDEN` | 초대 링크 생성 권한이 없습니다. | OWNER/EDITOR가 아님 |
| `404` | `PROJECT_NOT_FOUND` | 프로젝트를 찾을 수 없습니다. | projectId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "INVALID_INPUT",
  "message": "잘못된 입력입니다.",
  "errors": [
    { "field": "expiresAt", "reason": "must be a future datetime" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

## 4. Section

| Method | Path | 설명 |
| --- | --- | --- |
| DELETE | `/api/section-labels/{sectionLabelId}` | 라벨 삭제 |
| DELETE | `/api/section-templates/{templateId}` | 섹션 템플릿 삭제 |
| DELETE | `/api/template-dependencies/{dependencyId}` | 템플릿 의존 관계 삭제 |
| GET | `/api/project-sections/{projectSectionId}` | 프로젝트 섹션 단건 조회 |
| GET | `/api/project-sections/{projectSectionId}/labels` | 라벨 목록 조회 |
| GET | `/api/project-sections/{projectSectionId}/review-links` | 프로젝트 섹션의 리뷰 링크 목록 조회 |
| GET | `/api/project-sections/{projectSectionId}/status-histories` | 프로젝트 섹션 상태 변경 이력 조회 |
| GET | `/api/projects/{projectId}/sections` | 프로젝트 섹션 목록 조회 |
| GET | `/api/section-templates` | 섹션 템플릿 목록 조회 |
| GET | `/api/section-templates/{templateId}` | 섹션 템플릿 상세 조회 |
| GET | `/api/section-templates/{templateId}/dependencies` | 템플릿 의존 관계 조회 |
| PATCH | `/api/project-sections/{projectSectionId}` | 프로젝트 섹션 수정 |
| PATCH | `/api/section-labels/{sectionLabelId}` | 라벨 수정 |
| PATCH | `/api/section-templates/{templateId}` | 섹션 템플릿 수정 |
| POST | `/api/project-sections/{projectSectionId}/confirm` | 프로젝트 섹션 버전 확정 |
| POST | `/api/project-sections/{projectSectionId}/labels` | 라벨 생성 |
| POST | `/api/project-sections/{projectSectionId}/review-links` | 외부 검토 링크 발급 |
| POST | `/api/projects/{projectId}/sections` | 프로젝트 섹션 생성 |
| POST | `/api/section-templates` | 섹션 템플릿 생성 |
| POST | `/api/template-dependencies` | 템플릿 의존 관계 추가 |

#### DELETE /api/section-labels/{sectionLabelId} — 라벨 삭제

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `DELETE /api/section-labels/{sectionLabelId}` |
| **도메인** | section |
| **설명** | 라벨을 삭제한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `sectionLabelId` | `Long` | ✅ | 섹션 라벨 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 요청 본문 없음 |

```json
{}
```

**Response — 성공 (`204 No Content`)**

응답 본문이 없습니다.

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | `VIEWER` 등 삭제 권한 없는 멤버 |
| `404` | `S003` | 라벨을 찾을 수 없습니다. | `sectionLabelId`에 해당하는 라벨 없음 |
| `409` | `S004` | 사용 중인 라벨은 삭제할 수 없습니다. | 라벨에 연결된 의견 블록이 존재 (`SECTION_LABEL_IN_USE`) |

```json
{
  "success": false,
  "code": "S004",
  "message": "사용 중인 라벨은 삭제할 수 없습니다.",
  "errors": [
    { "field": "sectionLabelId", "reason": "label is in use" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

#### DELETE /api/section-templates/{templateId} — 섹션 템플릿 삭제

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `DELETE /api/section-templates/{templateId}` |
| **도메인** | section |
| **설명** | 섹션 템플릿을 삭제합니다. |
| **인증** | ✅ 필요 |
| **권한** | 관리자, 운영자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `templateId` | `Long` | ✅ | 템플릿 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 삭제 API |

```json
{}
```

**Response — 성공 (`204 No Content`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| 없음 | - | 응답 본문 없음 |

```json
Response body 없음
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `SECTION_TEMPLATE_NOT_FOUND` | 섹션 템플릿을 찾을 수 없습니다. | templateId가 존재하지 않음 |
| `409` | `TEMPLATE_IN_USE` | 사용 중인 템플릿은 삭제할 수 없습니다. | `project_sections`에서 참조 중 |

```json
{
  "success": false,
  "code": "TEMPLATE_IN_USE",
  "message": "사용 중인 템플릿은 삭제할 수 없습니다.",
  "errors": [
    { "field": "templateId", "reason": "referenced by project section" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### DELETE /api/template-dependencies/{dependencyId} — 템플릿 의존성 삭제

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `DELETE /api/template-dependencies/{dependencyId}` |
| **도메인** | section |
| **설명** | 템플릿 의존성 관계를 삭제합니다. |
| **인증** | ✅ 필요 |
| **권한** | 관리자, 운영자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `dependencyId` | `Long` | ✅ | 의존성 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 삭제 API |

```json
{}
```

**Response — 성공 (`204 No Content`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| 없음 | - | 응답 본문 없음 |

```json
Response body 없음
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `TEMPLATE_DEPENDENCY_NOT_FOUND` | 템플릿 의존성을 찾을 수 없습니다. | dependencyId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "TEMPLATE_DEPENDENCY_NOT_FOUND",
  "message": "템플릿 의존성을 찾을 수 없습니다.",
  "errors": [
    { "field": "dependencyId", "reason": "not found" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### GET /api/project-sections/{projectSectionId} — 프로젝트 섹션 단건 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{projectSectionId}` |
| **도메인** | section |
| **설명** | 섹션 상세, 라벨, 최신 초안, 리뷰 필요 여부 등을 조회한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`, `VIEWER`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 요청 본문 없음 |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 섹션 ID |
| `data.projectId` | `Long` | 프로젝트 ID |
| `data.templateId` | `Long` | 섹션 템플릿 ID |
| `data.title` | `String` | 섹션 제목 |
| `data.sectionOrder` | `Integer` | 섹션 정렬 순서 |
| `data.status` | `ProjectSectionStatus` | 섹션 상태 |
| `data.confirmedVersion` | `Integer` | 확정된 초안 버전 |
| `data.needsReReview` | `Boolean` | 재검토 필요 여부 |
| `data.labels[].id` | `Long` | 라벨 ID |
| `data.labels[].name` | `String` | 라벨 이름 |
| `data.labels[].sortOrder` | `Integer` | 라벨 정렬 순서 |
| `data.latestDraft.id` | `Long` | 최신 초안 ID (초안 없으면 `null`) |
| `data.latestDraft.version` | `Integer` | 초안 버전 |
| `data.latestDraft.content` | `String` | 초안 본문 |
| `data.latestDraft.lastEditorUserId` | `Long` | 마지막 수정자 사용자 ID |
| `data.latestDraft.createdAt` | `String (ISO-8601)` | 초안 생성 일시 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "id": 21,
    "projectId": 1,
    "templateId": 4,
    "title": "문제 정의",
    "sectionOrder": 1,
    "status": "COLLECTING_OPINIONS",
    "confirmedVersion": 0,
    "needsReReview": false,
    "labels": [
      { "id": 101, "name": "핵심 문제", "sortOrder": 1 }
    ],
    "latestDraft": {
      "id": 501,
      "version": 3,
      "content": "최신 초안 본문입니다.",
      "lastEditorUserId": 1,
      "createdAt": "2026-06-26T20:30:00"
    }
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 프로젝트 멤버가 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | `projectSectionId`에 해당하는 섹션 없음 |

```json
{
  "success": false,
  "code": "S001",
  "message": "섹션을 찾을 수 없습니다.",
  "errors": [
    { "field": "projectSectionId", "reason": "invalid value" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

#### GET /api/project-sections/{projectSectionId}/labels — 라벨 목록 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{projectSectionId}/labels` |
| **도메인** | section |
| **설명** | 섹션에 속한 라벨 목록을 조회한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`, `VIEWER`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters**

*섹션당 라벨 수가 제한적이므로 페이지네이션을 사용하지 않는다.*

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 요청 본문 없음 |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.labels[].id` | `Long` | 라벨 ID |
| `data.labels[].projectSectionId` | `Long` | 프로젝트 섹션 ID |
| `data.labels[].name` | `String` | 라벨 이름 |
| `data.labels[].sortOrder` | `Integer` | 라벨 정렬 순서 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "labels": [
      { "id": 101, "projectSectionId": 21, "name": "핵심 문제", "sortOrder": 1 }
    ]
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 프로젝트 멤버가 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | `projectSectionId`에 해당하는 섹션 없음 |

```json
{
  "success": false,
  "code": "S001",
  "message": "섹션을 찾을 수 없습니다.",
  "errors": [
    { "field": "projectSectionId", "reason": "invalid value" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

#### GET /api/project-sections/{projectSectionId}/review-links — 프로젝트 섹션의 리뷰 링크 목록 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{projectSectionId}/review-links` |
| **도메인** | section |
| **설명** | 해당 프로젝트 섹션에 달린 모든 리뷰 링크 목록 조회 |
| **인증** | ✅ 필요 |
| **권한** | OWNER, EDITOR, VIEWER |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | Y | 리뷰 링크 목록을 조회할 프로젝트 섹션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | `Integer` | N | `0` | 페이지 번호 |
| `size` | `Integer` | N | `20` | 페이지 크기 |
| `sort` | `String` | N | `desc` | 정렬 기준 |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Response — 성공 (`200`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.content[].id` | `Long` | 리뷰 링크 ID |
| `data.content[].reviewType` | `ReviewType` | 리뷰 유형 |
| `data.content[].isActive` | `Boolean` | 활성화 상태 |

```json
{
  "success": true,
  "code": "REVIEW_LINKS_FETCHED",
  "message": "리뷰 링크 목록을 조회했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "reviewType": "INTERNAL",
        "isActive": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "timestamp": "2026-07-02T20:35:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `S001` | 프로젝트 섹션을 찾을 수 없습니다. | 존재하지 않는 섹션 ID |

```json
{
  "success": false,
  "code": "S001",
  "message": "해당 프로젝트 섹션을 찾을 수 없습니다.",
  "errors": {
    "field": "projectSectionId",
    "reason": "not found"
  },
  "timestamp": "2026-07-02T20:35:00"
}
```

#### GET /api/project-sections/{projectSectionId}/status-histories — 프로젝트 섹션 상태 변경 이력 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{projectSectionId}/status-histories` |
| **도메인** | section |
| **설명** | 프로젝트 섹션의 과거 상태 변경 이력 조회 |
| **인증** | ✅ 필요 |
| **권한** | OWNER, EDITOR, VIEWER |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | Y | 이력을 조회할 프로젝트 섹션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | `Integer` | N | `0` | 페이지 번호 |
| `size` | `Integer` | N | `20` | 페이지 크기 |
| `sort` | `String` | N | `desc` | 정렬 기준 |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Response — 성공 (`200`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.content[].id` | `Long` | 이력 ID |
| `data.content[].actorUserId` | `Long` | 변경한 사용자 ID |
| `data.content[].eventType` | `String` | 이벤트 타입 |
| `data.content[].fromStatus` | `ProjectSectionStatus` | 이전 상태 |
| `data.content[].toStatus` | `ProjectSectionStatus` | 변경 후 상태 |
| `data.content[].version` | `Integer` | 변경 버전 |
| `data.content[].createdAt` | `LocalDateTime` | 변경 일시 |

```json
{
  "success": true,
  "code": "STATUS_HISTORY_FETCHED",
  "message": "상태 변경 이력 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 3,
        "actorUserId": 42,
        "eventType": "STATUS_UPDATE",
        "fromStatus": "OPINIONS_DRAFTING",
        "toStatus": "IN_REVIEW",
        "version": 1,
        "createdAt": "2026-07-02T20:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "timestamp": "2026-07-02T21:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `S001` | 프로젝트 섹션을 찾을 수 없습니다. | 존재하지 않는 섹션 ID |

```json
{
  "success": false,
  "code": "S001",
  "message": "해당 프로젝트 섹션을 찾을 수 없습니다.",
  "errors": {
    "field": "projectSectionId",
    "reason": "not found"
  },
  "timestamp": "2026-07-02T21:30:00"
}
```

#### GET /api/projects/{projectId}/sections — 프로젝트 섹션 목록 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/projects/{projectId}/sections` |
| **도메인** | section |
| **설명** | 프로젝트에 속한 실제 섹션 목록을 조회한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`, `VIEWER`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 프로젝트 ID |

**Query Parameters**

*프로젝트당 섹션 수가 제한적이므로 페이지네이션을 사용하지 않는다.*

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 요청 본문 없음 |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.sections[].id` | `Long` | 섹션 ID |
| `data.sections[].projectId` | `Long` | 프로젝트 ID |
| `data.sections[].templateId` | `Long` | 섹션 템플릿 ID |
| `data.sections[].title` | `String` | 섹션 제목 |
| `data.sections[].sectionOrder` | `Integer` | 섹션 정렬 순서 |
| `data.sections[].status` | `ProjectSectionStatus` | 섹션 상태 |
| `data.sections[].confirmedVersion` | `Integer` | 확정된 초안 버전 |
| `data.sections[].needsReReview` | `Boolean` | 재검토 필요 여부 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "sections": [
      {
        "id": 21,
        "projectId": 1,
        "templateId": 4,
        "title": "문제 정의",
        "sectionOrder": 1,
        "status": "COLLECTING_OPINIONS",
        "confirmedVersion": 0,
        "needsReReview": false
      }
    ]
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 프로젝트 멤버가 아님 |
| `404` | `P001` | 프로젝트를 찾을 수 없습니다. | `projectId`에 해당하는 프로젝트 없음 |

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

#### GET /api/section-templates — 섹션 템플릿 목록 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/section-templates` |
| **도메인** | section |
| **설명** | 결과물 유형별 섹션 템플릿 목록을 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 관리자, 운영자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `resultType` | `String` | ❌ | - | 결과물 유형 필터 |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.sectionTemplates` | `Array` | 템플릿 목록 |
| `data.sectionTemplates[].id` | `Long` | 템플릿 ID |
| `data.sectionTemplates[].resultType` | `String` | 결과물 유형 |
| `data.sectionTemplates[].sectionKey` | `String` | 섹션 키 |
| `data.sectionTemplates[].title` | `String` | 섹션 제목 |

```json
{
  "success": true,
  "code": "SECTION_TEMPLATES_FETCHED",
  "message": "섹션 템플릿을 조회했습니다.",
  "data": {
    "sectionTemplates": [
      {
        "id": 1,
        "resultType": "PROPOSAL",
        "sectionKey": "problem-definition",
        "title": "문제 정의",
        "orderNo": 1,
        "isRequired": true
      }
    ]
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `403` | `FORBIDDEN` | 조회 권한이 없습니다. | 관리자/운영자가 아님 |

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "조회 권한이 없습니다.",
  "errors": [
    { "field": "Authorization", "reason": "insufficient role" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### GET /api/section-templates/{templateId} — 섹션 템플릿 단건 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/section-templates/{templateId}` |
| **도메인** | section |
| **설명** | 특정 섹션 템플릿의 상세 정보를 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 관리자, 운영자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `templateId` | `Long` | ✅ | 템플릿 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 템플릿 ID |
| `data.resultType` | `String` | 결과물 유형 |
| `data.sectionKey` | `String` | 섹션 키 |
| `data.title` | `String` | 제목 |
| `data.description` | `String` | 설명 |
| `data.guideText` | `String` | 작성 가이드 |
| `data.orderNo` | `int` | 정렬 순서 |
| `data.isRequired` | `boolean` | 필수 여부 |

```json
{
  "success": true,
  "code": "SECTION_TEMPLATE_FETCHED",
  "message": "섹션 템플릿을 조회했습니다.",
  "data": {
    "id": 1,
    "resultType": "PROPOSAL",
    "sectionKey": "problem-definition",
    "title": "문제 정의",
    "description": "현재 사용자가 겪는 문제를 정의합니다.",
    "guideText": "문제 상황과 기존 대안을 중심으로 정리합니다.",
    "orderNo": 1,
    "isRequired": true
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `SECTION_TEMPLATE_NOT_FOUND` | 섹션 템플릿을 찾을 수 없습니다. | templateId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "SECTION_TEMPLATE_NOT_FOUND",
  "message": "섹션 템플릿을 찾을 수 없습니다.",
  "errors": [
    { "field": "templateId", "reason": "not found" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### GET /api/section-templates/{templateId}/dependencies — 템플릿 의존성 목록 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/section-templates/{templateId}/dependencies` |
| **도메인** | section |
| **설명** | 특정 템플릿의 선행/후행 의존성 목록을 조회합니다. |
| **인증** | ✅ 필요 |
| **권한** | 관리자, 운영자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `templateId` | `Long` | ✅ | 기준 템플릿 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 조회 API |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.dependencies` | `Array` | 의존성 목록 |
| `data.dependencies[].id` | `Long` | 의존성 ID |
| `data.dependencies[].fromTemplateId` | `Long` | 출발 템플릿 ID |
| `data.dependencies[].toTemplateId` | `Long` | 도착 템플릿 ID |
| `data.dependencies[].dependencyType` | `TemplateDependencyType` | 의존성 유형 |

```json
{
  "success": true,
  "code": "TEMPLATE_DEPENDENCIES_FETCHED",
  "message": "템플릿 의존성을 조회했습니다.",
  "data": {
    "dependencies": [
      {
        "id": 1,
        "fromTemplateId": 1,
        "toTemplateId": 2,
        "dependencyType": "REQUIRES"
      }
    ]
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `SECTION_TEMPLATE_NOT_FOUND` | 섹션 템플릿을 찾을 수 없습니다. | templateId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "SECTION_TEMPLATE_NOT_FOUND",
  "message": "섹션 템플릿을 찾을 수 없습니다.",
  "errors": [
    { "field": "templateId", "reason": "not found" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### PATCH /api/project-sections/{projectSectionId} — 프로젝트 섹션 수정

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/project-sections/{projectSectionId}` |
| **도메인** | section |
| **설명** | 섹션 제목, 순서, 리뷰 필요 여부 등을 수정한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

> 전달된 필드만 수정하며, 최소 1개 이상의 필드를 포함해야 합니다.

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `title` | `String` | ❌ | 1~200자 | 섹션 제목 |
| `sectionOrder` | `Integer` | ❌ | 1 이상 | 섹션 정렬 순서 |
| `needsReReview` | `Boolean` | ❌ | | 재검토 필요 여부 |

```json
{
  "title": "문제 정의 및 맥락",
  "sectionOrder": 2,
  "needsReReview": true
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 섹션 ID |
| `data.title` | `String` | 섹션 제목 |
| `data.sectionOrder` | `Integer` | 섹션 정렬 순서 |
| `data.status` | `ProjectSectionStatus` | 섹션 상태 (변경되지 않음) |
| `data.confirmedVersion` | `Integer` | 확정된 초안 버전 |
| `data.needsReReview` | `Boolean` | 재검토 필요 여부 |

```json
{
  "success": true,
  "code": "SECTION_UPDATED",
  "message": "섹션이 수정되었습니다.",
  "data": {
    "id": 21,
    "title": "문제 정의 및 맥락",
    "sectionOrder": 2,
    "status": "COLLECTING_OPINIONS",
    "confirmedVersion": 0,
    "needsReReview": true
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | 수정 필드 형식 위반, 수정 필드 없음 |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | `VIEWER` 등 수정 권한 없는 멤버 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | `projectSectionId`에 해당하는 섹션 없음 |

```json
{
  "success": false,
  "code": "S001",
  "message": "섹션을 찾을 수 없습니다.",
  "errors": [
    { "field": "projectSectionId", "reason": "invalid value" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

#### PATCH /api/section-labels/{sectionLabelId} — 라벨 수정

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/section-labels/{sectionLabelId}` |
| **도메인** | section |
| **설명** | 라벨 이름, 정렬 순서를 수정한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `sectionLabelId` | `Long` | ✅ | 섹션 라벨 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

> 전달된 필드만 수정하며, 최소 1개 이상의 필드를 포함해야 합니다.

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `name` | `String` | ❌ | 1~100자 | 라벨 이름 |
| `sortOrder` | `Integer` | ❌ | 1 이상 | 라벨 정렬 순서 |

```json
{
  "name": "핵심 불편",
  "sortOrder": 2
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 라벨 ID |
| `data.projectSectionId` | `Long` | 프로젝트 섹션 ID |
| `data.name` | `String` | 라벨 이름 |
| `data.sortOrder` | `Integer` | 라벨 정렬 순서 |

```json
{
  "success": true,
  "code": "SECTION_LABEL_UPDATED",
  "message": "라벨이 수정되었습니다.",
  "data": {
    "id": 101,
    "projectSectionId": 21,
    "name": "핵심 불편",
    "sortOrder": 2
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | 수정 필드 형식 위반, 수정 필드 없음 |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | `VIEWER` 등 수정 권한 없는 멤버 |
| `404` | `S003` | 라벨을 찾을 수 없습니다. | `sectionLabelId`에 해당하는 라벨 없음 |

```json
{
  "success": false,
  "code": "S003",
  "message": "라벨을 찾을 수 없습니다.",
  "errors": [
    { "field": "sectionLabelId", "reason": "invalid value" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

#### PATCH /api/section-templates/{templateId} — 섹션 템플릿 수정

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/section-templates/{templateId}` |
| **도메인** | section |
| **설명** | 섹션 템플릿의 제목, 설명, 가이드, 순서를 수정합니다. |
| **인증** | ✅ 필요 |
| **권한** | 관리자, 운영자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `templateId` | `Long` | ✅ | 템플릿 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `title` | `String` | ❌ | 1~255자 | 섹션 제목 |
| `description` | `String` | ❌ | - | 설명 |
| `guideText` | `String` | ❌ | - | 작성 가이드 |
| `orderNo` | `int` | ❌ | 1 이상 | 정렬 순서 |
| `isRequired` | `boolean` | ❌ | - | 필수 여부 |

```json
{
  "title": "문제 정의와 배경",
  "guideText": "문제 상황과 사용자 맥락까지 포함합니다.",
  "orderNo": 2,
  "isRequired": true
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 템플릿 ID |
| `data.title` | `String` | 수정된 제목 |
| `data.orderNo` | `int` | 수정된 순서 |

```json
{
  "success": true,
  "code": "SECTION_TEMPLATE_UPDATED",
  "message": "섹션 템플릿이 수정되었습니다.",
  "data": {
    "id": 1,
    "title": "문제 정의와 배경",
    "orderNo": 2
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | 제목 누락, orderNo 오류 |
| `404` | `SECTION_TEMPLATE_NOT_FOUND` | 섹션 템플릿을 찾을 수 없습니다. | templateId가 존재하지 않음 |

```json
{
  "success": false,
  "code": "INVALID_INPUT",
  "message": "잘못된 입력입니다.",
  "errors": [
    { "field": "orderNo", "reason": "must be greater than 0" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### POST /api/project-sections/{projectSectionId}/confirm — 프로젝트 섹션 버전 확정

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/confirmed-version` (문서상 표기, 목록의 실제 경로는 `/confirm`) |
| **도메인** | section |
| **설명** | 프로젝트 섹션의 버전 확정 |
| **인증** | ✅ 필요 |
| **권한** | OWNER |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | Y | 버전을 확정할 프로젝트 섹션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `confirmedVersion` | `Integer` | Y | - | 확정 시 버전 |

```json
{
  "confirmedVersion": 1
}
```

**Response — 성공 (`200`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 프로젝트 섹션 ID |
| `data.confirmedVersion` | `Integer` | 변경된 섹션 버전 |

```json
{
  "success": true,
  "code": "VERSION_CHANGED",
  "message": "섹션의 버전이 확정되었습니다.",
  "data": {
    "id": 1,
    "confirmedVersion": 1
  },
  "timestamp": "2026-07-02T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `403` | `A001` | 권한이 없습니다. | OWNER가 요청하지 않은 경우 |
| `404` | `S001` | 프로젝트 섹션을 찾을 수 없습니다. | 존재하지 않는 섹션 ID |
| `400` | `C001` | 잘못된 입력입니다. | 유효하지 않은 version 값 |

```json
{
  "success": false,
  "code": "C001",
  "message": "잘못된 입력입니다.",
  "errors": {
    "field": "confirmedVersion",
    "reason": "must be integer"
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

#### POST /api/project-sections/{projectSectionId}/labels — 라벨 생성

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/labels` |
| **도메인** | section |
| **설명** | 섹션에 새 라벨을 생성한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `name` | `String` | ✅ | 1~100자 | 라벨 이름 |
| `sortOrder` | `Integer` | ❌ | 1 이상, 미지정 시 마지막 순번 + 1 | 라벨 정렬 순서 |

```json
{
  "name": "핵심 문제",
  "sortOrder": 1
}
```

**Response — 성공 (`201 Created`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 생성된 라벨 ID |
| `data.projectSectionId` | `Long` | 프로젝트 섹션 ID |
| `data.name` | `String` | 라벨 이름 |
| `data.sortOrder` | `Integer` | 라벨 정렬 순서 |

```json
{
  "success": true,
  "code": "SECTION_LABEL_CREATED",
  "message": "라벨이 생성되었습니다.",
  "data": {
    "id": 101,
    "projectSectionId": 21,
    "name": "핵심 문제",
    "sortOrder": 1
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | `name` 누락/길이 위반 |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | `VIEWER` 등 생성 권한 없는 멤버 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | `projectSectionId`에 해당하는 섹션 없음 |

```json
{
  "success": false,
  "code": "C001",
  "message": "잘못된 입력입니다.",
  "errors": [
    { "field": "name", "reason": "must not be blank" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

#### POST /api/project-sections/{projectSectionId}/review-links — 외부 검토 링크 발급

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/review-links` |
| **도메인** | section |
| **설명** | 외부 검토 링크 발급 (팀장 전용) |
| **인증** | ✅ 필요 |
| **권한** | OWNER |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | Y | 외부 검토 대상 프로젝트 섹션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Response — 성공 (`201`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 생성된 링크 ID |
| `data.token` | `String` | 공개 접근용 고유 토큰 |

```json
{
  "success": true,
  "code": "REVIEW_LINK_CREATED",
  "message": "외부 검토 링크가 생성되었습니다.",
  "data": {
    "id": 1,
    "token": "asdfsss"
  },
  "timestamp": "2026-07-02T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `S001` | 프로젝트 섹션을 찾을 수 없습니다. | 존재하지 않는 섹션 ID |
| `400` | `C001` | 잘못된 입력입니다. | 유효하지 않은 입력값 |
| `403` | `A001` | 권한이 없습니다. | OWNER가 요청하지 않은 경우 |

```json
{
  "success": false,
  "code": "S001",
  "message": "해당 프로젝트 섹션을 찾을 수 없습니다.",
  "errors": {
    "field": "projectSectionId",
    "reason": "not found"
  },
  "timestamp": "2026-07-02T20:30:00"
}
```

#### POST /api/projects/{projectId}/sections — 프로젝트 섹션 생성

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/projects/{projectId}/sections` |
| **도메인** | section |
| **설명** | 프로젝트에 새 섹션을 수동 생성한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 프로젝트 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `templateId` | `Long` | ❌ | 존재하는 섹션 템플릿 ID | 기반이 되는 섹션 템플릿. 미지정 시 템플릿 없이 생성 |
| `title` | `String` | ✅ | 1~200자 | 섹션 제목 |
| `sectionOrder` | `Integer` | ✅ | 1 이상 | 섹션 정렬 순서 |

```json
{
  "templateId": 4,
  "title": "문제 정의",
  "sectionOrder": 1
}
```

**Response — 성공 (`201 Created`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 생성된 섹션 ID |
| `data.projectId` | `Long` | 프로젝트 ID |
| `data.templateId` | `Long` | 섹션 템플릿 ID |
| `data.title` | `String` | 섹션 제목 |
| `data.sectionOrder` | `Integer` | 섹션 정렬 순서 |
| `data.status` | `ProjectSectionStatus` | 초기값 `NOT_STARTED` |
| `data.confirmedVersion` | `Integer` | 초기값 `0` |
| `data.needsReReview` | `Boolean` | 초기값 `false` |

```json
{
  "success": true,
  "code": "SECTION_CREATED",
  "message": "섹션이 생성되었습니다.",
  "data": {
    "id": 21,
    "projectId": 1,
    "templateId": 4,
    "title": "문제 정의",
    "sectionOrder": 1,
    "status": "NOT_STARTED",
    "confirmedVersion": 0,
    "needsReReview": false
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | `title`/`sectionOrder` 누락 또는 형식 위반 |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | `VIEWER` 등 생성 권한 없는 멤버 |
| `404` | `P001` | 프로젝트를 찾을 수 없습니다. | `projectId`에 해당하는 프로젝트 없음 |
| `404` | `S002` | 섹션 템플릿을 찾을 수 없습니다. | `templateId`에 해당하는 템플릿 없음 |

```json
{
  "success": false,
  "code": "C001",
  "message": "잘못된 입력입니다.",
  "errors": [
    { "field": "title", "reason": "must not be blank" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

#### POST /api/section-templates — 섹션 템플릿 생성

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/section-templates` |
| **도메인** | section |
| **설명** | 결과물 유형에 대한 섹션 템플릿을 생성합니다. |
| **인증** | ✅ 필요 |
| **권한** | 관리자, 운영자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `resultType` | `String` | ✅ | not blank | 결과물 유형 |
| `sectionKey` | `String` | ✅ | slug 형식 | 템플릿 키 |
| `title` | `String` | ✅ | 1~255자 | 섹션 제목 |
| `description` | `String` | ❌ | - | 섹션 설명 |
| `guideText` | `String` | ❌ | - | 작성 가이드 |
| `orderNo` | `int` | ✅ | 1 이상 | 정렬 순서 |
| `isRequired` | `boolean` | ✅ | - | 필수 여부 |

```json
{
  "resultType": "PROPOSAL",
  "sectionKey": "problem-definition",
  "title": "문제 정의",
  "description": "현재 사용자가 겪는 문제를 정의합니다.",
  "guideText": "문제 상황과 기존 대안을 중심으로 정리합니다.",
  "orderNo": 1,
  "isRequired": true
}
```

**Response — 성공 (`201 Created`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 생성된 템플릿 ID |
| `data.sectionKey` | `String` | 섹션 키 |
| `data.resultType` | `String` | 결과물 유형 |

```json
{
  "success": true,
  "code": "SECTION_TEMPLATE_CREATED",
  "message": "섹션 템플릿이 생성되었습니다.",
  "data": {
    "id": 1,
    "resultType": "PROPOSAL",
    "sectionKey": "problem-definition"
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | 필수값 누락, 정렬값 오류 |
| `409` | `SECTION_TEMPLATE_DUPLICATED` | 중복된 섹션 템플릿입니다. | `(resultType, sectionKey)` 중복 |

```json
{
  "success": false,
  "code": "SECTION_TEMPLATE_DUPLICATED",
  "message": "중복된 섹션 템플릿입니다.",
  "errors": [
    { "field": "sectionKey", "reason": "duplicated in result type" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

#### POST /api/template-dependencies — 템플릿 의존 관계 추가

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/template-dependencies` |
| **도메인** | section |
| **설명** | 템플릿 간 의존성 관계를 생성합니다. |
| **인증** | ✅ 필요 |
| **권한** | 관리자, 운영자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| 없음 | - | - | - |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `fromTemplateId` | `Long` | ✅ | - | 출발 템플릿 ID |
| `toTemplateId` | `Long` | ✅ | - | 도착 템플릿 ID |
| `dependencyType` | `TemplateDependencyType` | ✅ | `REQUIRES`, `BLOCKS`, `RECOMMENDS` | 의존성 유형 |

```json
{
  "fromTemplateId": 1,
  "toTemplateId": 2,
  "dependencyType": "REQUIRES"
}
```

**Response — 성공 (`201 Created`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 생성된 의존성 ID |
| `data.dependencyType` | `TemplateDependencyType` | 의존성 유형 |

```json
{
  "success": true,
  "code": "TEMPLATE_DEPENDENCY_CREATED",
  "message": "템플릿 의존성이 생성되었습니다.",
  "data": {
    "id": 1,
    "dependencyType": "REQUIRES"
  },
  "timestamp": "2026-07-03T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `INVALID_INPUT` | 잘못된 입력입니다. | 동일 템플릿 연결, 잘못된 enum |
| `404` | `SECTION_TEMPLATE_NOT_FOUND` | 템플릿을 찾을 수 없습니다. | from/to template가 존재하지 않음 |
| `409` | `TEMPLATE_DEPENDENCY_DUPLICATED` | 중복된 의존성입니다. | 같은 관계가 이미 존재 |

```json
{
  "success": false,
  "code": "TEMPLATE_DEPENDENCY_DUPLICATED",
  "message": "중복된 의존성입니다.",
  "errors": [
    { "field": "fromTemplateId", "reason": "same dependency already exists" }
  ],
  "timestamp": "2026-07-03T12:00:00"
}
```

## 5. Opinion

> ⚠️ **이 섹션은 제품 정책서 v2 + `wevo-api-add-remove-analysis.md`(2026-07-08) 기준으로 재설계되었습니다.** 노션 DB에 등록된 원본 14개 엔드포인트(opinion-blocks CRUD + 구 라벨 구조)는 아래 부록 5.Z에 원문 그대로 보존했습니다 — 실제 개발은 이 섹션(정책 v2 기준) 을 따르세요.
>
> 재설계 근거:
> - 의견은 **멤버당 섹션당 1개**(`OpinionStatus`: `DRAFT`/`SUBMITTED`), 20~1,000자(앱 레벨 검증) — 구모델의 라벨(SectionLabel) 그룹핑·게시판형 다중 작성 구조는 정책서에 없음
> - **공개 게이트**: 본인이 한 번이라도 제출(`everSubmitted`)해야 타인 의견 열람 가능 — 구모델 목록 조회는 게이트 없이 전체 노출됨(정책 위반)
> - **삭제**: 정책서 v2 원칙은 "삭제 미지원 — 수정으로 갈음"이지만, 실무 편의를 위해 `DELETE .../my-opinion`(본인용)을 다시 추가했다(구모델 `DELETE /api/opinion-blocks/{id}`의 대체). 구모델은 이 API에 "OWNER 강제 삭제" 권한도 포함돼 있었는데, "본인만" 삭제로 좁히면 그 권한이 빠지므로 `DELETE /api/opinions/{opinionId}`(OWNER 전용)를 별도로 추가해 보존했다. `SUBMITTED` 의견 삭제 시 수집 마감 조건에 영향을 줄 수 있어 팀 정책 확인 필요
> - 역할은 `OWNER`/`MEMBER` 2종만(구모델의 `EDITOR`/`VIEWER`는 현재 `ProjectMemberRole` enum에 존재하지 않아 그대로 쓰면 컴파일 불가)
> - draft-lease(편집 잠금)는 **5분 무활동 자동 해제 + heartbeat**로 정책 변경(구모델은 60초 유효/30초 갱신) — API 형태(acquire/renew/release/조회)는 유지
> - section-draft 저장 시 **`contentVersion` 증가 → 기존 AI 사전검토·팀검토 OUTDATED 처리**를 명시(정책서 §7-8) — 구모델엔 이 부수효과가 없었음
> - **수집 게이트 마감/재오픈**(`opinion-gate/close`, `opinion-gate/reopen`)을 신규 추가 — 정책서 §5 "OWNER만 마감(제출 1개 이상 필요), 재오픈 시 COLLECTING 복귀 + synthesisStale 표시" 요구사항. 노션 원본 14개에는 없던 기능이라 최초 재설계 때 누락했다가 뒤늦게 추가함(`wevo-api-add-remove-analysis.md` 1·2순위 항목)
> - **collectGate는 별도 컬럼이 아니라 `sectionStatus`에서 파생한다** — 게이트 OPEN ⇔ 섹션이 `COLLECTING` 단계, 게이트 CLOSED ⇔ 그 외 단계. 마감/재오픈 액션이 곧 상태 전이(`COLLECTING`↔`SYNTHESIZING`)이므로 별도 필드를 두면 두 값이 어긋날 위험만 생긴다. ⚠️ CLAUDE.md §5.7의 `CollectGate` enum(⏳) 항목은 이 파생 설계로 대체 — 팀 확인 후 CLAUDE.md 갱신 필요

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/project-sections/{projectSectionId}/my-opinion` | 내 의견 상태 조회 |
| PATCH | `/api/project-sections/{projectSectionId}/my-opinion/draft` | 내 의견 임시저장 (upsert) |
| POST | `/api/project-sections/{projectSectionId}/my-opinion/submit` | 내 의견 제출 (DRAFT→SUBMITTED) |
| DELETE | `/api/project-sections/{projectSectionId}/my-opinion` | 내 의견 삭제 |
| DELETE | `/api/opinions/{opinionId}` | 의견 강제 삭제 (OWNER 전용) |
| GET | `/api/project-sections/{projectSectionId}/opinions` | 제출된 팀원 의견 목록 (공개 게이트) |
| POST | `/api/project-sections/{projectSectionId}/opinion-gate/close` | 의견 수집 마감 (COLLECTING→SYNTHESIZING) |
| POST | `/api/project-sections/{projectSectionId}/opinion-gate/reopen` | 의견 수집 재오픈 (synthesisStale 표시) |
| POST | `/api/project-sections/{projectSectionId}/draft-lease/acquire` | 편집 잠금 획득 (5분 무활동 자동 해제) |
| GET | `/api/project-sections/{projectSectionId}/draft-lease` | 편집 잠금 상태 조회 |
| PATCH | `/api/draft-leases/{leaseId}/renew` | 편집 잠금 갱신 (heartbeat) |
| DELETE | `/api/draft-leases/{leaseId}` | 편집 잠금 해제 |
| GET | `/api/project-sections/{projectSectionId}/drafts` | 섹션 초안 버전 목록 조회 |
| GET | `/api/project-sections/{projectSectionId}/drafts/latest` | 최신 섹션 초안 조회 |
| GET | `/api/section-drafts/{draftId}` | 섹션 초안 단건(특정 버전) 조회 |
| POST | `/api/project-sections/{projectSectionId}/drafts` | 섹션 초안 버전 스냅샷 생성 |
| PATCH | `/api/section-drafts/{draftId}` | 섹션 초안 자동저장 (contentVersion 증가) |

### 5.1 Opinion (my-opinion)

#### GET /api/project-sections/{projectSectionId}/my-opinion — 내 의견 상태 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{projectSectionId}/my-opinion` |
| **도메인** | opinion |
| **설명** | 현재 로그인 사용자가 이 섹션에 작성한 의견(있다면 1개)을 조회한다. 아직 작성 전이면 404가 아니라 `exists=false`로 200 응답한다 — 화면 초기 진입 시 "작성 전" 상태를 그려야 하기 때문. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER) |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.exists` | `Boolean` | 작성한 의견이 있는지 여부 |
| `data.id` | `Long \| null` | 의견 ID (없으면 null) |
| `data.content` | `String \| null` | 의견 본문 |
| `data.status` | `OpinionStatus \| null` | `DRAFT` \| `SUBMITTED` |
| `data.updatedAt` | `DateTime \| null` | 마지막 저장 시각 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "exists": true,
    "id": 501,
    "content": "타겟을 대학생 팀으로 좁히는 게 좋겠습니다.",
    "status": "DRAFT",
    "updatedAt": "2026-07-13T10:20:00"
  },
  "timestamp": "2026-07-13T12:00:00"
}
```

의견을 아직 작성하지 않은 경우:

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "exists": false,
    "id": null,
    "content": null,
    "status": null,
    "updatedAt": null
  },
  "timestamp": "2026-07-13T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 해당 프로젝트 멤버 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | projectSectionId 없음 |

```json
{
  "success": false,
  "code": "S001",
  "message": "섹션을 찾을 수 없습니다.",
  "timestamp": "2026-07-13T12:00:00"
}
```

#### PATCH /api/project-sections/{projectSectionId}/my-opinion/draft — 내 의견 임시저장

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/project-sections/{projectSectionId}/my-opinion/draft` |
| **도메인** | opinion |
| **설명** | 내 의견을 `DRAFT` 상태로 저장한다(upsert) — 아직 레코드가 없으면 새로 만들고, 있으면 덮어쓴다. 멤버당 섹션당 의견 1개 원칙이라 별도의 "생성" API 없이 이 엔드포인트가 생성·수정을 겸한다. 이미 `SUBMITTED` 상태인 의견도 이 API로 다시 수정할 수 있으나, 상태는 `SUBMITTED`로 유지된다(재제출 절차 없음 — 정책서 §5 "삭제 미지원, 수정으로 갈음"). |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER), 본인 의견만 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `content` | `String` | ✅ | 20~1,000자 | 의견 본문 |

```json
{
  "content": "기존 도구는 의견 통합을 지원하지 않는다는 점을 강조하면 좋겠습니다."
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 의견 ID |
| `data.content` | `String` | 저장된 본문 |
| `data.status` | `OpinionStatus` | 저장 후 상태 (`DRAFT` 또는 기존 `SUBMITTED` 유지) |
| `data.updatedAt` | `DateTime` | 저장 시각 |

```json
{
  "success": true,
  "code": "OPINION_DRAFT_SAVED",
  "message": "의견이 임시저장되었습니다.",
  "data": {
    "id": 501,
    "content": "기존 도구는 의견 통합을 지원하지 않는다는 점을 강조하면 좋겠습니다.",
    "status": "DRAFT",
    "updatedAt": "2026-07-13T12:00:00"
  },
  "timestamp": "2026-07-13T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | content 20~1,000자 위반, 공백만 입력 |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 해당 프로젝트 멤버 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 섹션 없음 |
| `422` | `O003` | 의견 수집이 마감되었습니다. | 섹션이 `COLLECTING` 단계가 아닌 상태에서 저장 시도 (collectGate는 sectionStatus에서 파생 — §5 안내문 참고) |

```json
{
  "success": false,
  "code": "C001",
  "message": "잘못된 입력입니다.",
  "errors": [
    { "field": "content", "reason": "size must be between 20 and 1000" }
  ],
  "timestamp": "2026-07-13T12:00:00"
}
```

#### POST /api/project-sections/{projectSectionId}/my-opinion/submit — 내 의견 제출

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/my-opinion/submit` |
| **도메인** | opinion |
| **설명** | 현재 저장된 `DRAFT` 의견을 `SUBMITTED`로 전환해 팀에 공개한다. 제출 즉시 본인은 `everSubmitted=true`가 되어 다른 팀원의 제출 의견을 열람할 수 있게 된다(공개 게이트). 이미 `SUBMITTED` 상태에서 다시 호출하면 멱등하게 200을 반환한다(별도 재제출 절차 없음). |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER), 본인 의견만 |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body** — 없음 (임시저장된 content를 그대로 제출)

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 의견 ID |
| `data.status` | `OpinionStatus` | `SUBMITTED` |
| `data.submittedAt` | `DateTime` | 제출 시각 |

```json
{
  "success": true,
  "code": "OPINION_SUBMITTED",
  "message": "의견이 제출되었습니다.",
  "data": {
    "id": 501,
    "status": "SUBMITTED",
    "submittedAt": "2026-07-13T12:05:00"
  },
  "timestamp": "2026-07-13T12:05:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 해당 프로젝트 멤버 아님 |
| `404` | `O001` | 의견을 찾을 수 없습니다. | 임시저장된 의견이 없는 상태에서 제출 시도 |
| `422` | `C002` | 업무 규칙을 위반했습니다. | content가 20자 미만인 상태로 제출 시도 |
| `422` | `O003` | 의견 수집이 마감되었습니다. | 섹션이 `COLLECTING` 단계가 아닌 상태 (수집 마감) |

```json
{
  "success": false,
  "code": "O001",
  "message": "의견을 찾을 수 없습니다.",
  "errors": [
    { "field": "projectSectionId", "reason": "no draft opinion to submit" }
  ],
  "timestamp": "2026-07-13T12:05:00"
}
```

#### DELETE /api/project-sections/{projectSectionId}/my-opinion — 내 의견 삭제

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `DELETE /api/project-sections/{projectSectionId}/my-opinion` |
| **도메인** | opinion |
| **설명** | 내가 작성한 의견을 삭제한다. `DRAFT`·`SUBMITTED` 상태 모두 삭제 가능하다. ⚠️ 정책서 v2 원칙은 "삭제 미지원 — 수정으로 갈음"이지만, 실무 편의를 위해 이번에 다시 추가한 API다. `SUBMITTED` 상태 의견이 삭제되면 섹션의 `totalSubmittedCount`가 줄어들어, 수집 마감 조건("제출 1개 이상")이나 이미 수집이 마감된 섹션에서의 삭제 처리 등 팀 정책 확인이 필요한 엣지케이스가 있다. 팀원의 의견을 OWNER가 강제 삭제하려면 아래 `DELETE /api/opinions/{opinionId}`를 사용한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 작성자 본인만 |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`204 No Content`)** — 응답 본문 없음

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 해당 프로젝트 멤버 아님 |
| `404` | `O001` | 의견을 찾을 수 없습니다. | 삭제할 의견이 없음(아직 작성 전) |
| `422` | `O003` | 의견 수집이 마감되었습니다. | 섹션이 `COLLECTING` 단계가 아닌 상태에서 삭제 시도 — 팀 정책에 따라 허용 여부 결정 필요 |

```json
{
  "success": false,
  "code": "O001",
  "message": "의견을 찾을 수 없습니다.",
  "timestamp": "2026-07-13T12:06:00"
}
```

#### DELETE /api/opinions/{opinionId} — 의견 강제 삭제 (OWNER 전용)

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `DELETE /api/opinions/{opinionId}` |
| **도메인** | opinion |
| **설명** | 프로젝트 OWNER가 팀원의 의견을 강제 삭제한다. 구모델 `DELETE /api/opinion-blocks/{opinionBlockId}`의 "OWNER 강제 삭제" 권한을 대체한다. `opinionId`는 `GET .../opinions` 목록 응답의 `data.opinions[].id`로 확인한다. 본인 의견 삭제는 위 `DELETE .../my-opinion`을 사용한다(이 API는 타인 의견 대상). |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 **OWNER만** |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `opinionId` | `Long` | ✅ | 삭제할 의견 ID (`GET .../opinions` 응답의 `id`) |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`204 No Content`)** — 응답 본문 없음

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 요청자가 프로젝트 OWNER가 아님 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 해당 프로젝트 멤버 아님 |
| `404` | `O001` | 의견을 찾을 수 없습니다. | opinionId 없음 |

```json
{
  "success": false,
  "code": "A002",
  "message": "접근 권한이 없습니다.",
  "errors": [
    { "field": "opinionId", "reason": "only project OWNER can force-delete" }
  ],
  "timestamp": "2026-07-13T12:06:00"
}
```

#### GET /api/project-sections/{projectSectionId}/opinions — 제출된 팀원 의견 목록 (공개 게이트)

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{projectSectionId}/opinions` |
| **도메인** | opinion |
| **설명** | 섹션에 제출(`SUBMITTED`)된 팀원 의견 목록을 조회한다. **요청자 본인이 한 번도 제출하지 않았다면(`everSubmitted=false`) 내용은 숨기고 전체 건수(`totalSubmittedCount`)만 반환한다** — 베끼기 방지를 위한 공개 게이트(정책서 §5). |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER) |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음 (페이지네이션 미적용 — 프로젝트 인원 1~4명이라 목록이 항상 작음)

**Request Body** — 없음

**Response — 성공 (`200 OK`)** — 본인이 제출한 경우

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.everSubmitted` | `Boolean` | 요청자 본인의 제출 여부 |
| `data.totalSubmittedCount` | `Integer` | 제출된 의견 총 개수 |
| `data.opinions[]` | `Array` | `everSubmitted=true`일 때만 채워짐 |
| `data.opinions[].id` | `Long` | 의견 ID |
| `data.opinions[].author` | `Object` | 작성자 (id, name, profileImageUrl) |
| `data.opinions[].content` | `String` | 의견 본문 |
| `data.opinions[].submittedAt` | `DateTime` | 제출 시각 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "everSubmitted": true,
    "totalSubmittedCount": 2,
    "opinions": [
      {
        "id": 501,
        "author": { "id": 7, "name": "김민준", "profileImageUrl": "https://..." },
        "content": "타겟을 대학생 팀으로 좁히는 게 좋겠습니다.",
        "submittedAt": "2026-07-13T10:20:00"
      },
      {
        "id": 508,
        "author": { "id": 9, "name": "이서연", "profileImageUrl": "https://..." },
        "content": "기존 도구는 의견 통합을 지원하지 않는다는 점을 강조하면 좋겠습니다.",
        "submittedAt": "2026-07-13T11:00:00"
      }
    ]
  },
  "timestamp": "2026-07-13T12:00:00"
}
```

본인이 아직 제출하지 않은 경우(공개 게이트 적용):

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "everSubmitted": false,
    "totalSubmittedCount": 2,
    "opinions": []
  },
  "timestamp": "2026-07-13T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 해당 프로젝트 멤버 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 섹션 없음 |

```json
{
  "success": false,
  "code": "S001",
  "message": "섹션을 찾을 수 없습니다.",
  "timestamp": "2026-07-13T12:00:00"
}
```

#### POST /api/project-sections/{projectSectionId}/opinion-gate/close — 의견 수집 마감

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/opinion-gate/close` |
| **도메인** | opinion |
| **설명** | 의견 수집을 마감하고 섹션 상태를 `COLLECTING` → `SYNTHESIZING`으로 전환한다(정책서 §5). 노션 원본 14개엔 없던 API로, `wevo-api-add-remove-analysis.md`의 1순위 신규 항목이다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 **OWNER만** |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.sectionStatus` | `ProjectSectionStatus` | 전환된 상태 (`SYNTHESIZING`) |
| `data.closedAt` | `DateTime` | 마감 시각 |

```json
{
  "success": true,
  "code": "OPINION_GATE_CLOSED",
  "message": "의견 수집이 마감되었습니다.",
  "data": {
    "sectionStatus": "SYNTHESIZING",
    "closedAt": "2026-07-13T12:20:00"
  },
  "timestamp": "2026-07-13T12:20:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 요청자가 OWNER가 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 섹션 없음 |
| `409` | `S008` | 섹션 상태가 올바르지 않습니다. | 이미 `COLLECTING`이 아닌 상태에서 마감 시도 |
| `422` | `O004` | 제출된 의견이 없습니다. | `SUBMITTED` 의견이 0건인 상태에서 마감 시도(최소 1건 필요) |

```json
{
  "success": false,
  "code": "O004",
  "message": "제출된 의견이 없습니다.",
  "timestamp": "2026-07-13T12:20:00"
}
```

#### POST /api/project-sections/{projectSectionId}/opinion-gate/reopen — 의견 수집 재오픈

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/opinion-gate/reopen` |
| **도메인** | opinion |
| **설명** | 마감된 의견 수집을 다시 열어 섹션 상태를 `COLLECTING`으로 되돌린다. 이미 AI가 정리한 합의점·쟁점이나 생성된 초안이 있다면 **`synthesisStale=true`**로 표시해 "재정리 필요" 상태를 알린다(정책서 §5). 노션 원본 14개엔 없던 API로, add-remove 분석의 2순위 신규 항목이다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 **OWNER만** |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.sectionStatus` | `ProjectSectionStatus` | 전환된 상태 (`COLLECTING`) |
| `data.synthesisStale` | `Boolean` | 기존 AI 정리·초안이 있어 재정리가 필요하면 `true` |
| `data.reopenedAt` | `DateTime` | 재오픈 시각 |

```json
{
  "success": true,
  "code": "OPINION_GATE_REOPENED",
  "message": "의견 수집이 다시 열렸습니다.",
  "data": {
    "sectionStatus": "COLLECTING",
    "synthesisStale": true,
    "reopenedAt": "2026-07-13T12:30:00"
  },
  "timestamp": "2026-07-13T12:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 요청자가 OWNER가 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 섹션 없음 |
| `409` | `S008` | 섹션 상태가 올바르지 않습니다. | 이미 `COLLECTING` 상태이거나 `CONFIRMED` 이후 단계에서 재오픈 시도 |

```json
{
  "success": false,
  "code": "S008",
  "message": "섹션 상태가 올바르지 않습니다.",
  "timestamp": "2026-07-13T12:30:00"
}
```

### 5.2 Draft Lease (편집 잠금)

> 정책서 §7: 초안 편집은 한 번에 1명만 가능하다. `편집 시작` 시 lease를 획득(acquire)하고, 클라이언트가 주기적으로 `renew`를 호출하는 heartbeat로 활성 상태를 유지한다. **5분 무활동 시 서버가 자동 해제**한다(구모델의 "60초 유효/30초 갱신"은 폐기).

#### POST /api/project-sections/{projectSectionId}/draft-lease/acquire — 편집 잠금 획득

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/draft-lease/acquire` |
| **도메인** | section (초안 편집 잠금 — 이 문서에서는 opinion 카테고리로 분류) |
| **설명** | 섹션 초안 편집권을 획득한다. lease가 없거나 만료됐으면 새로 발급하고, 본인이 이미 보유 중이면 만료 시각을 연장해 반환한다(멱등). 유효 시간은 서버 정책 **5분**이며, 이후 heartbeat(`renew`)로 연장하지 않으면 자동 해제된다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER) |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`201 Created`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.leaseId` | `Long` | 편집 잠금 ID |
| `data.projectSectionId` | `Long` | 대상 섹션 ID |
| `data.holder` | `Object` | 보유자 (id, name) |
| `data.leaseUntil` | `DateTime` | 만료 시각 (현재+5분) |

```json
{
  "success": true,
  "code": "LEASE_ACQUIRED",
  "message": "편집권을 획득했습니다.",
  "data": {
    "leaseId": 91,
    "projectSectionId": 11,
    "holder": { "id": 7, "name": "김민준" },
    "leaseUntil": "2026-07-13T12:05:00"
  },
  "timestamp": "2026-07-13T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 멤버 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 섹션 없음 |
| `409` | `S005` | 다른 사용자가 편집 중입니다. | 타인이 미만료 lease 보유 중 (errors에 보유자·leaseUntil 포함) |

```json
{
  "success": false,
  "code": "S005",
  "message": "다른 사용자가 편집 중입니다.",
  "errors": [
    { "field": "holder", "reason": "이서연 (leaseUntil: 2026-07-13T12:04:40)" }
  ],
  "timestamp": "2026-07-13T12:00:00"
}
```

#### GET /api/project-sections/{projectSectionId}/draft-lease — 편집 잠금 상태 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{projectSectionId}/draft-lease` |
| **도메인** | section (opinion 카테고리로 분류) |
| **설명** | 섹션의 현재 편집 잠금 상태를 조회한다. 잠금이 없거나 만료됐으면 `locked=false`, `lease=null`로 반환한다(404 아님). |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER) |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.locked` | `Boolean` | 유효한 잠금 존재 여부 |
| `data.lease` | `Object \| null` | 잠금 정보 (leaseId, holder, leaseUntil). 없거나 만료 시 null |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "locked": true,
    "lease": {
      "leaseId": 91,
      "holder": { "id": 7, "name": "김민준" },
      "leaseUntil": "2026-07-13T12:05:00"
    }
  },
  "timestamp": "2026-07-13T12:00:30"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 멤버 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 섹션 없음 |

```json
{
  "success": false,
  "code": "S001",
  "message": "섹션을 찾을 수 없습니다.",
  "timestamp": "2026-07-13T12:00:30"
}
```

#### PATCH /api/draft-leases/{leaseId}/renew — 편집 잠금 갱신 (heartbeat)

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/draft-leases/{leaseId}/renew` |
| **도메인** | section (opinion 카테고리로 분류) |
| **설명** | 보유 중인 편집 잠금의 만료 시각을 현재 시각 + 5분으로 연장한다. 편집 중인 클라이언트가 heartbeat로 주기적으로(예: 1~2분 간격) 호출해 무활동 자동 해제를 막는다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | lease 보유자 본인만 |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `leaseId` | `Long` | ✅ | 편집 잠금 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.leaseId` | `Long` | 편집 잠금 ID |
| `data.leaseUntil` | `DateTime` | 연장된 만료 시각 (현재+5분) |

```json
{
  "success": true,
  "code": "LEASE_RENEWED",
  "message": "편집권이 연장되었습니다.",
  "data": {
    "leaseId": 91,
    "leaseUntil": "2026-07-13T12:10:00"
  },
  "timestamp": "2026-07-13T12:05:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 보유자가 아닌 사용자의 갱신 시도 |
| `404` | `S004` | 편집 잠금을 찾을 수 없습니다. | leaseId 없음 (이미 해제됨) |
| `409` | `S007` | 편집 잠금이 만료되었습니다. | 5분 무활동으로 이미 만료됨 → acquire로 재획득 필요 |

```json
{
  "success": false,
  "code": "S007",
  "message": "편집 잠금이 만료되었습니다.",
  "errors": [
    { "field": "leaseId", "reason": "expired at 2026-07-13T12:04:50, re-acquire required" }
  ],
  "timestamp": "2026-07-13T12:05:00"
}
```

#### DELETE /api/draft-leases/{leaseId} — 편집 잠금 해제

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `DELETE /api/draft-leases/{leaseId}` |
| **도메인** | section (opinion 카테고리로 분류) |
| **설명** | 편집 잠금을 해제한다. 편집 종료·저장 완료 시 호출하며, 보유자 본인 또는 프로젝트 OWNER가 강제 해제할 수 있다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | lease 보유자 본인 또는 프로젝트 OWNER |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `leaseId` | `Long` | ✅ | 편집 잠금 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`204 No Content`)** — 응답 본문 없음

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 보유자도 OWNER도 아닌 사용자 |
| `404` | `S004` | 편집 잠금을 찾을 수 없습니다. | leaseId 없음 (이미 해제됨) — 멱등하게 204 처리할지는 팀 확인 필요 |

```json
{
  "success": false,
  "code": "A002",
  "message": "접근 권한이 없습니다.",
  "errors": [
    { "field": "leaseId", "reason": "only the holder or project OWNER can release" }
  ],
  "timestamp": "2026-07-13T12:00:30"
}
```

### 5.3 Section Draft (섹션 초안)

> 정책서 §7-8: 초안 저장 시 `contentVersion`이 증가하며, 이 시점에 기존 AI 사전검토(`aiCheckStatus`)와 팀 검토는 모두 `OUTDATED` 처리된다. 아래 자동저장 API에 이 부수효과를 명시한다.

#### GET /api/project-sections/{projectSectionId}/drafts — 섹션 초안 버전 목록 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{projectSectionId}/drafts` |
| **도메인** | section (opinion 카테고리로 분류) |
| **설명** | 섹션의 초안 버전 이력을 페이지네이션으로 조회한다. 목록에서는 본문 앞 100자(`contentPreview`)만 내려준다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER) |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** (공통 페이지네이션 §1.5)

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | `Integer` | ❌ | `0` | 0부터 시작하는 페이지 번호 |
| `size` | `Integer` | ❌ | `20` | 페이지 크기 |
| `sort` | `String` | ❌ | `version,desc` | 정렬 기준 |

**Request Body** — 없음

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.content[]` | `Array` | 초안 요약 목록 (id, version, contentPreview, lastEditor, createdAt) |
| `data.content[].contentPreview` | `String` | 본문 앞 100자 |
| `data.page / size` | `Integer` | 현재 페이지 번호(0부터) / 페이지 크기 |
| `data.totalElements / totalPages` | `Long / Integer` | 전체 건수 / 전체 페이지 수 |
| `data.hasNext` | `Boolean` | 다음 페이지 존재 여부 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 310,
        "version": 5,
        "contentPreview": "기존 문서 도구는 공동 작성은 지원하지만 이해의 차이까지 확인하지 않는다...",
        "lastEditor": { "id": 7, "name": "김민준" },
        "createdAt": "2026-07-13T11:40:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1,
    "hasNext": false
  },
  "timestamp": "2026-07-13T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | page/size 음수 등 형식 위반 |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 멤버 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 섹션 없음 |

```json
{
  "success": false,
  "code": "C001",
  "message": "잘못된 입력입니다.",
  "errors": [
    { "field": "page", "reason": "must be greater than or equal to 0" }
  ],
  "timestamp": "2026-07-13T12:00:00"
}
```

#### GET /api/project-sections/{projectSectionId}/drafts/latest — 최신 섹션 초안 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{projectSectionId}/drafts/latest` |
| **도메인** | section (opinion 카테고리로 분류) |
| **설명** | 섹션의 최신 버전 초안 전문을 조회한다. 편집 화면 진입 시 기본으로 호출된다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER) |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 초안 ID |
| `data.projectSectionId` | `Long` | 소속 섹션 ID |
| `data.version` | `Integer` | 초안 버전 (최신) |
| `data.content` | `String` | 초안 본문 전문 |
| `data.aiCheckStatus` | `AiCheckStatus` | `CURRENT` \| `OUTDATED` — AI 사전검토가 이 본문 기준 최신인지 |
| `data.lastEditor` | `Object` | 마지막 편집자 (id, name) |
| `data.createdAt / updatedAt` | `DateTime` | 생성·수정 시각 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "id": 310,
    "projectSectionId": 11,
    "version": 5,
    "content": "기존 문서 도구는 공동 작성은 지원하지만... (전문)",
    "aiCheckStatus": "OUTDATED",
    "lastEditor": { "id": 7, "name": "김민준" },
    "createdAt": "2026-07-13T11:40:00",
    "updatedAt": "2026-07-13T11:55:00"
  },
  "timestamp": "2026-07-13T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 멤버 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 섹션 없음 |
| `404` | `S002` | 초안을 찾을 수 없습니다. | 아직 초안이 한 번도 생성되지 않은 섹션 |

```json
{
  "success": false,
  "code": "S002",
  "message": "초안을 찾을 수 없습니다.",
  "timestamp": "2026-07-13T12:00:00"
}
```

#### GET /api/section-drafts/{draftId} — 섹션 초안 단건(특정 버전) 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/section-drafts/{draftId}` |
| **도메인** | section (opinion 카테고리로 분류) |
| **설명** | 특정 버전의 초안 전문을 조회한다. 버전 이력 비교 화면에서 사용한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER) |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `draftId` | `Long` | ✅ | 초안 ID |

**Query Parameters** — 없음

**Request Body** — 없음

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 초안 ID |
| `data.projectSectionId` | `Long` | 소속 섹션 ID |
| `data.version` | `Integer` | 초안 버전 |
| `data.content` | `String` | 초안 본문 전문 |
| `data.lastEditor` | `Object` | 마지막 편집자 (id, name) |
| `data.createdAt / updatedAt` | `DateTime` | 생성·수정 시각 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "id": 309,
    "projectSectionId": 11,
    "version": 4,
    "content": "(해당 버전의 초안 전문)",
    "lastEditor": { "id": 7, "name": "김민준" },
    "createdAt": "2026-07-13T11:20:00",
    "updatedAt": "2026-07-13T11:30:00"
  },
  "timestamp": "2026-07-13T12:00:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 멤버 아님 |
| `404` | `S002` | 초안을 찾을 수 없습니다. | draftId에 해당하는 초안 없음 |

```json
{
  "success": false,
  "code": "S002",
  "message": "초안을 찾을 수 없습니다.",
  "errors": [
    { "field": "draftId", "reason": "invalid value" }
  ],
  "timestamp": "2026-07-13T12:00:00"
}
```

#### POST /api/project-sections/{projectSectionId}/drafts — 섹션 초안 버전 스냅샷 생성

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/drafts` |
| **도메인** | section (opinion 카테고리로 분류) |
| **설명** | 새 버전의 초안 스냅샷을 생성한다(`version` = 최신+1). AI가 생성한 초안을 사용자가 명시적으로 적용할 때(자동 덮어쓰기 금지 — 정책서 §6) 또는 수동 버전 저장에 사용한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER) + 유효한 편집 잠금(lease) 보유자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters** — 없음

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `content` | `String` | ✅ | 1~20,000자 | 초안 본문 전문 |
| `baseVersion` | `Integer` | ❌ | 최신 version과 일치해야 함. 최초 생성 시 생략 | 분기 기준이 된 버전 (동시 생성 충돌 방지) |

```json
{
  "content": "기존 문서 도구는 공동 작성은 지원하지만 이해의 차이까지 확인하지 않는다...",
  "baseVersion": 5
}
```

**Response — 성공 (`201 Created`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 생성된 초안 ID |
| `data.projectSectionId` | `Long` | 소속 섹션 ID |
| `data.version` | `Integer` | 새 초안 버전 (직전 최신+1) |
| `data.aiCheckStatus` | `AiCheckStatus` | `OUTDATED`로 초기화 (새 버전이므로 재검토 필요) |
| `data.lastEditor` | `Object` | 작성자 (id, name) |
| `data.createdAt` | `DateTime` | 생성 시각 |

```json
{
  "success": true,
  "code": "DRAFT_CREATED",
  "message": "초안이 저장되었습니다.",
  "data": {
    "id": 311,
    "projectSectionId": 11,
    "version": 6,
    "aiCheckStatus": "OUTDATED",
    "lastEditor": { "id": 7, "name": "김민준" },
    "createdAt": "2026-07-13T12:10:00"
  },
  "timestamp": "2026-07-13T12:10:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | content 누락/길이 위반 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 멤버 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 섹션 없음 |
| `409` | `S006` | 섹션 편집권이 필요합니다. | 유효한 lease 미보유 (먼저 acquire 필요) |
| `409` | `S003` | 초안 버전이 충돌했습니다. | baseVersion ≠ 최신 version |

```json
{
  "success": false,
  "code": "S006",
  "message": "섹션 편집권이 필요합니다.",
  "timestamp": "2026-07-13T12:10:00"
}
```

#### PATCH /api/section-drafts/{draftId} — 섹션 초안 자동저장

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/section-drafts/{draftId}` |
| **도메인** | section (opinion 카테고리로 분류) |
| **설명** | 편집 중 초안 본문을 갱신한다(자동저장용). 최신 버전의 초안만 수정할 수 있다. **저장 시 `contentVersion`이 증가하고, 기존 AI 사전검토·팀 검토는 모두 `OUTDATED`로 전환된다(정책서 §7-8 — 확정 필수 조건에 영향).** |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (OWNER, MEMBER) + 유효한 편집 잠금(lease) 보유자 |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `draftId` | `Long` | ✅ | 초안 ID (최신 버전만 허용) |

**Query Parameters** — 없음

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `content` | `String` | ✅ | 1~20,000자 | 수정할 본문 전문 |

```json
{
  "content": "기존 문서 도구는 공동 작성은 지원하지만... (수정된 전문)"
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 초안 ID |
| `data.contentVersion` | `Integer` | 증가된 콘텐츠 버전 |
| `data.aiCheckStatus` | `AiCheckStatus` | `OUTDATED`로 전환됨 |
| `data.lastEditor` | `Object` | 마지막 편집자로 갱신됨 (id, name) |
| `data.updatedAt` | `DateTime` | 수정 시각 |

```json
{
  "success": true,
  "code": "DRAFT_UPDATED",
  "message": "초안이 수정되었습니다.",
  "data": {
    "id": 311,
    "contentVersion": 7,
    "aiCheckStatus": "OUTDATED",
    "lastEditor": { "id": 9, "name": "이서연" },
    "updatedAt": "2026-07-13T12:12:30"
  },
  "timestamp": "2026-07-13T12:12:30"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | content 누락/길이 위반 |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 멤버 아님 |
| `404` | `S002` | 초안을 찾을 수 없습니다. | draftId 없음 |
| `409` | `S003` | 초안 버전이 충돌했습니다. | 최신 버전이 아닌 초안 수정 시도 |
| `409` | `S006` | 섹션 편집권이 필요합니다. | lease 미보유 또는 만료 후 저장 시도 |

```json
{
  "success": false,
  "code": "S003",
  "message": "초안 버전이 충돌했습니다.",
  "errors": [
    { "field": "draftId", "reason": "not the latest draft (latest version: 7)" }
  ],
  "timestamp": "2026-07-13T12:12:30"
}
```

### 5.Z 부록 — 노션 DB 원본 (구모델, 참고용)

> 아래는 노션 "Wevo API 명세" DB에 등록된 원본 14개 엔드포인트입니다. `sectionLabelId` 기반 게시판형 다중 의견 작성, `OWNER/EDITOR/VIEWER` 권한, 공개 게이트 없는 전체 조회, 삭제(DELETE) 지원 등 **정책서 v2 이전(구모델) 설계**이므로 개발 기준으로 삼지 마세요. 위 5.1~5.3 섹션으로 대체되었습니다.

| Method | Path | 설명 | 대체 여부 |
| --- | --- | --- | --- |
| POST | `/api/project-sections/{projectSectionId}/opinion-blocks` | 의견 블록 생성 | → 5.1 `PATCH .../my-opinion/draft`로 대체 |
| GET | `/api/project-sections/{projectSectionId}/opinion-blocks` | 의견 블록 목록 조회 | → 5.1 `GET .../opinions`로 대체 |
| GET | `/api/opinion-blocks/{opinionBlockId}` | 의견 블록 단건 조회 | → 5.1 `GET .../my-opinion`으로 대체 |
| PATCH | `/api/opinion-blocks/{opinionBlockId}` | 의견 블록 수정 | → 5.1 `PATCH .../my-opinion/draft`로 대체 |
| DELETE | `/api/opinion-blocks/{opinionBlockId}` | 의견 블록 삭제 | → 5.1 `DELETE .../my-opinion`으로 대체 (정책서 원칙은 삭제 미지원이나 실무 편의상 재추가, 엣지케이스 있음) |
| POST | `/api/project-sections/{projectSectionId}/draft-lease/acquire` | 초안 편집 잠금 획득 | 5.2에서 TTL만 5분으로 변경, 유지 |
| GET | `/api/project-sections/{projectSectionId}/draft-lease` | 초안 편집 잠금 조회 | 5.2 유지 |
| PATCH | `/api/draft-leases/{leaseId}/renew` | 초안 편집 잠금 갱신 | 5.2에서 heartbeat 의미로 변경, 유지 |
| DELETE | `/api/draft-leases/{leaseId}` | 초안 편집 잠금 해제 | 5.2 유지 |
| POST | `/api/project-sections/{projectSectionId}/drafts` | 섹션 초안 생성 | 5.3 유지 |
| GET | `/api/project-sections/{projectSectionId}/drafts` | 섹션 초안 목록 조회 | 5.3 유지 |
| GET | `/api/project-sections/{projectSectionId}/drafts/latest` | 최신 섹션 초안 조회 | 5.3 유지 (aiCheckStatus 필드 추가) |
| GET | `/api/section-drafts/{draftId}` | 섹션 초안 단건 조회 | 5.3 유지 |
| PATCH | `/api/section-drafts/{draftId}` | 섹션 초안 수정 | 5.3에서 contentVersion/aiCheckStatus 부수효과 명시, 유지 |

원본 각 엔드포인트의 전체 Request/Response 상세는 이 파일의 최초 버전(git 이력) 또는 노션 "Wevo API 명세" DB에서 확인할 수 있습니다.


## 6. Ai

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/ai-usage-logs/{logId}` | AI 사용 로그 단건 조회 |
| GET | `/api/projects/{projectId}/ai-usage-logs` | 프로젝트 기준 AI 사용 로그 조회 |
| POST | `/api/project-sections/{projectSectionId}/ai/generate-draft` | 섹션 초안 ai 생성 |
| POST | `/api/project-sections/{projectSectionId}/ai/generate-summary` | 섹션 ai 요약 생성 |

#### GET /api/ai-usage-logs/{logId} — AI 사용 로그 단건 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/ai-usage-logs/{logId}` |
| **도메인** | ai |
| **설명** | AI 사용 로그 상세를 조회한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`, `VIEWER`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `logId` | `Long` | ✅ | AI 사용 로그 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 요청 본문 없음 |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | AI 사용 로그 ID |
| `data.projectId` | `Long` | 프로젝트 ID |
| `data.projectSectionId` | `Long` | 프로젝트 섹션 ID |
| `data.requestedByUserId` | `Long` | 요청한 사용자 ID |
| `data.featureName` | `String` | 호출 기능명 |
| `data.promptTokens` | `Integer` | 프롬프트 토큰 수 |
| `data.completionTokens` | `Integer` | 완성 토큰 수 |
| `data.estimatedCost` | `BigDecimal` | 추정 비용 |
| `data.requestStatus` | `AiRequestStatus` | AI 요청 상태 |
| `data.createdAt` | `String (ISO-8601)` | 로그 생성 일시 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "id": 901,
    "projectId": 1,
    "projectSectionId": 21,
    "requestedByUserId": 1,
    "featureName": "GENERATE_DRAFT",
    "promptTokens": 1200,
    "completionTokens": 600,
    "estimatedCost": 0.1325,
    "requestStatus": "SUCCEEDED",
    "createdAt": "2026-06-26T20:30:00"
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 프로젝트 멤버가 아님 |
| `404` | `AI002` | AI 사용 로그를 찾을 수 없습니다. | `logId`에 해당하는 로그 없음 |

```json
{
  "success": false,
  "code": "AI002",
  "message": "AI 사용 로그를 찾을 수 없습니다.",
  "errors": [
    { "field": "logId", "reason": "invalid value" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

#### GET /api/projects/{projectId}/ai-usage-logs — 프로젝트 기준 AI 사용 로그 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/projects/{projectId}/ai-usage-logs` |
| **도메인** | ai |
| **설명** | 프로젝트 기준 AI 사용 이력을 조회한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`, `VIEWER`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectId` | `Long` | ✅ | 프로젝트 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `projectSectionId` | `Long` | ❌ | - | 특정 섹션으로 필터링 |
| `featureName` | `String` | ❌ | - | 호출 기능명으로 필터링 (예: `GENERATE_DRAFT`) |
| `requestStatus` | `AiRequestStatus` | ❌ | - | 요청 상태로 필터링 |
| `page` | `Integer` | ❌ | `0` | 0부터 시작하는 페이지 번호 |
| `size` | `Integer` | ❌ | `20` | 페이지 크기 |
| `sort` | `String` | ❌ | `createdAt,desc` | 정렬 기준 (`필드,방향`) |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | 요청 본문 없음 |

```json
{}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.content[].id` | `Long` | AI 사용 로그 ID |
| `data.content[].projectId` | `Long` | 프로젝트 ID |
| `data.content[].projectSectionId` | `Long` | 프로젝트 섹션 ID |
| `data.content[].requestedByUserId` | `Long` | 요청한 사용자 ID |
| `data.content[].featureName` | `String` | 호출 기능명 |
| `data.content[].promptTokens` | `Integer` | 프롬프트 토큰 수 |
| `data.content[].completionTokens` | `Integer` | 완성 토큰 수 |
| `data.content[].estimatedCost` | `BigDecimal` | 추정 비용 |
| `data.content[].requestStatus` | `AiRequestStatus` | AI 요청 상태 |
| `data.content[].createdAt` | `String (ISO-8601)` | 로그 생성 일시 |
| `data.page` | `Integer` | 현재 페이지 번호 |
| `data.size` | `Integer` | 페이지 크기 |
| `data.totalElements` | `Long` | 전체 요소 수 |
| `data.totalPages` | `Integer` | 전체 페이지 수 |
| `data.hasNext` | `Boolean` | 다음 페이지 존재 여부 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 901,
        "projectId": 1,
        "projectSectionId": 21,
        "requestedByUserId": 1,
        "featureName": "GENERATE_DRAFT",
        "promptTokens": 1200,
        "completionTokens": 600,
        "estimatedCost": 0.1325,
        "requestStatus": "SUCCEEDED",
        "createdAt": "2026-06-26T20:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 프로젝트 멤버가 아님 |
| `404` | `P001` | 프로젝트를 찾을 수 없습니다. | `projectId`에 해당하는 프로젝트 없음 |

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

#### POST /api/project-sections/{projectSectionId}/ai/generate-draft — 섹션 초안 AI 생성

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/ai/generate-draft` |
| **도메인** | ai |
| **설명** | 현재 섹션의 의견 블록과 맥락을 기반으로 AI 초안을 생성한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `tone` | `String` | ❌ | `FORMAL`, `NEUTRAL`, `CASUAL` 중 하나, 기본값 `NEUTRAL` | 생성 문체 |
| `maxLength` | `Integer` | ❌ | 100~5000, 기본값 1000 | 생성 본문 최대 길이 |

```json
{
  "tone": "FORMAL",
  "maxLength": 1200
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.draftContent` | `String` | AI가 생성한 섹션 초안 본문 |
| `data.log.id` | `Long` | AI 사용 로그 ID |
| `data.log.featureName` | `String` | 호출 기능명 (`GENERATE_DRAFT`) |
| `data.log.promptTokens` | `Integer` | 프롬프트 토큰 수 |
| `data.log.completionTokens` | `Integer` | 완성 토큰 수 |
| `data.log.estimatedCost` | `BigDecimal` | 추정 비용 |
| `data.log.requestStatus` | `AiRequestStatus` | AI 요청 상태 |

```json
{
  "success": true,
  "code": "AI_DRAFT_GENERATED",
  "message": "AI 초안이 생성되었습니다.",
  "data": {
    "draftContent": "AI가 생성한 섹션 초안 본문입니다.",
    "log": {
      "id": 901,
      "featureName": "GENERATE_DRAFT",
      "promptTokens": 1200,
      "completionTokens": 600,
      "estimatedCost": 0.1325,
      "requestStatus": "SUCCEEDED"
    }
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | `tone`/`maxLength` 형식 위반 |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | `VIEWER` 등 생성 권한 없는 멤버 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | `projectSectionId`에 해당하는 섹션 없음 |
| `500` | `AI001` | AI 생성에 실패했습니다. | 외부 AI 호출 실패 (`ai_usage_logs.requestStatus`는 `FAILED`로 기록) |

```json
{
  "success": false,
  "code": "AI001",
  "message": "AI 생성에 실패했습니다.",
  "errors": [
    { "field": "projectSectionId", "reason": "ai generation failed" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

#### POST /api/project-sections/{projectSectionId}/ai/generate-summary — 섹션 AI 요약 생성

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /api/project-sections/{projectSectionId}/ai/generate-summary` |
| **도메인** | ai |
| **설명** | 의견 블록이나 리뷰 제출 내용을 AI로 요약한다. |
| **인증** | ✅ 필요 (Bearer JWT) |
| **권한** | 프로젝트 멤버 (`OWNER`, `EDITOR`) |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `projectSectionId` | `Long` | ✅ | 프로젝트 섹션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `source` | `String` | ✅ | `OPINIONS`, `REVIEWS` 중 하나 | 요약 대상 |
| `maxLength` | `Integer` | ❌ | 100~2000, 기본값 500 | 요약 본문 최대 길이 |

```json
{
  "source": "OPINIONS",
  "maxLength": 500
}
```

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.summaryContent` | `String` | AI가 생성한 요약 본문 |
| `data.log.id` | `Long` | AI 사용 로그 ID |
| `data.log.featureName` | `String` | 호출 기능명 (`GENERATE_SUMMARY`) |
| `data.log.promptTokens` | `Integer` | 프롬프트 토큰 수 |
| `data.log.completionTokens` | `Integer` | 완성 토큰 수 |
| `data.log.estimatedCost` | `BigDecimal` | 추정 비용 |
| `data.log.requestStatus` | `AiRequestStatus` | AI 요청 상태 |

```json
{
  "success": true,
  "code": "AI_SUMMARY_GENERATED",
  "message": "AI 요약이 생성되었습니다.",
  "data": {
    "summaryContent": "AI가 생성한 요약 본문입니다.",
    "log": {
      "id": 902,
      "featureName": "GENERATE_SUMMARY",
      "promptTokens": 800,
      "completionTokens": 200,
      "estimatedCost": 0.0525,
      "requestStatus": "SUCCEEDED"
    }
  },
  "timestamp": "2026-06-26T20:30:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `400` | `C001` | 잘못된 입력입니다. | `source` 누락/허용값 외 값, `maxLength` 형식 위반 |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | `VIEWER` 등 생성 권한 없는 멤버 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | `projectSectionId`에 해당하는 섹션 없음 |
| `500` | `AI001` | AI 생성에 실패했습니다. | 외부 AI 호출 실패 (`ai_usage_logs.requestStatus`는 `FAILED`로 기록) |

```json
{
  "success": false,
  "code": "C001",
  "message": "잘못된 입력입니다.",
  "errors": [
    { "field": "source", "reason": "must be one of OPINIONS, REVIEWS" }
  ],
  "timestamp": "2026-06-26T20:30:00"
}
```

## 7. Review

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/project-sections/{sectionId}/review-submissions` | 외부 검토 결과 조회 |
| GET | `/api/review-links/{reviewLinkId}` | 리뷰 링크 상세 조회 |
| GET | `/api/review-links/{reviewLinkId}/submissions` | 특정 리뷰 링크의 제출된 리뷰 조회 |
| GET | `/api/review-submissions/{reviewSubmissionId}` | 제출된 리뷰 상세 조회 |
| GET | `/public/review-links/{token}` | 외부 검토 - 섹션 초안 조회 |
| PATCH | `/api/review-links/{reviewLinkId}` | 리뷰 링크 정보 수정 |
| POST | `/public/review-links/{token}/submissions` | 외부 검토 - 이해도 제출 |

#### GET /api/project-sections/{sectionId}/review-submissions — 외부 검토 결과 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/project-sections/{sectionId}/review-submissions` (문서상 표기는 `/external-reviews`) |
| **도메인** | review |
| **설명** | 섹션의 외부 검토 결과(이해도 집계 + 개별 코멘트 목록)를 조회 |
| **인증** | ✅ 필요 |
| **권한** | OWNER |

**Request Header**
```
Authorization: Bearer {accessToken}
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `sectionId` | `Long` | ✅ | 조회할 섹션 ID |

**Query Parameters**

*없음 — 해당 섹션의 외부 검토 제출을 최신순으로 전체 반환합니다. (페이지네이션 미사용)*

**Response — 성공 (`200 OK`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.totalCount` | `Long` | 전체 제출 수 |
| `data.clearCount` | `Long` | 이해됨(CLEAR) 수 |
| `data.partialCount` | `Long` | 애매함(PARTIAL) 수 |
| `data.unclearCount` | `Long` | 이해 어려움(UNCLEAR) 수 |
| `data.items[].submissionId` | `Long` | 제출 ID |
| `data.items[].understandingSignal` | `UnderstandingSignal` | 이해도 |
| `data.items[].reviewerName` | `String` | 표시 이름 (없으면 null) |
| `data.items[].summary` | `String` | 코멘트 (없으면 null) |
| `data.items[].submittedAt` | `LocalDateTime` | 제출 시각 |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "totalCount": 2,
    "clearCount": 1,
    "partialCount": 1,
    "unclearCount": 0,
    "items": [
      {
        "submissionId": 6,
        "understandingSignal": "CLEAR",
        "reviewerName": "외부검토자B",
        "summary": "전반적으로 이해됩니다.",
        "submittedAt": "2026-07-10T20:31:00"
      },
      {
        "submissionId": 5,
        "understandingSignal": "PARTIAL",
        "reviewerName": "외부검토자A",
        "summary": "3번째 문단이 애매합니다.",
        "submittedAt": "2026-07-10T20:30:00"
      }
    ]
  },
  "timestamp": "2026-07-10T20:32:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `401` | `A001` | 인증이 필요합니다. | 토큰 없음/만료 |
| `403` | `A002` | 접근 권한이 없습니다. | 팀원(MEMBER)이 조회 시도 (팀장만 가능) |
| `403` | `P002` | 프로젝트 멤버가 아닙니다. | 해당 프로젝트 멤버가 아님 |
| `404` | `S001` | 섹션을 찾을 수 없습니다. | 존재하지 않는 `sectionId` |

```json
{
  "success": false,
  "code": "A002",
  "message": "접근 권한이 없습니다.",
  "timestamp": "2026-07-10T20:32:00"
}
```

#### GET /api/review-links/{reviewLinkId} — 리뷰 링크 상세 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/review-links/{reviewLinkId}` |
| **도메인** | review |
| **설명** | 단일 리뷰 링크의 상세 정보를 조회 |
| **인증** | ✅ 필요 |
| **권한** | OWNER, EDITOR, VIEWER |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reviewLinkId` | `Long` | Y | 조회할 리뷰 링크 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Response — 성공 (`200`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 리뷰 링크 ID |
| `data.projectSectionId` | `Long` | 연결된 프로젝트 섹션 ID |
| `data.token` | `String` | 고유 토큰 |
| `data.reviewType` | `ReviewType` | 리뷰 유형 |
| `data.isActive` | `Boolean` | 활성화 여부 |
| `data.expiresAt` | `LocalDateTime` | 만료 일시 |

```json
{
  "success": true,
  "code": "REVIEW_LINK_FETCHED",
  "message": "리뷰 링크 상세 조회에 성공했습니다.",
  "data": {
    "id": 1,
    "projectSectionId": 12,
    "token": "uuid-v4-string",
    "reviewType": "INTERNAL",
    "isActive": true,
    "expiresAt": "2026-08-02T20:30:00"
  },
  "timestamp": "2026-07-02T20:40:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `R001` | 리뷰 링크를 찾을 수 없습니다. | 존재하지 않는 리뷰 링크 ID |

```json
{
  "success": false,
  "code": "R001",
  "message": "해당 리뷰 링크를 찾을 수 없습니다.",
  "errors": {
    "field": "reviewLinkId",
    "reason": "not found"
  },
  "timestamp": "2026-07-02T20:40:00"
}
```

#### GET /api/review-links/{reviewLinkId}/submissions — 특정 리뷰 링크의 제출된 리뷰 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/review-links/{reviewLinkId}/submissions` |
| **도메인** | review |
| **설명** | 해당 링크에 제출된 리뷰 목록 조회 |
| **인증** | ✅ 필요 |
| **권한** | OWNER, EDITOR, VIEWER |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reviewLinkId` | `Long` | Y | 조회할 리뷰 링크 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | `Integer` | N | `0` | 페이지 번호 |
| `size` | `Integer` | N | `20` | 페이지 크기 |
| `sort` | `String` | N | `desc` | 정렬 기준 |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Response — 성공 (`200`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.content[].id` | `Long` | 제출된 리뷰 ID |
| `data.content[].reviewerName` | `String` | 제출자(검토자) 이름 |
| `data.content[].understandingSignal` | `UnderstandingSignal` | 이해도 신호 |
| `data.content[].summary` | `String` | 요약 내용 |
| `data.content[].createdAt` | `LocalDateTime` | 제출 일시 |

```json
{
  "success": true,
  "code": "REVIEW_SUBMISSIONS_FETCHED",
  "message": "리뷰 제출 내역 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 10,
        "reviewerName": "김리뷰",
        "understandingSignal": "PARTIAL",
        "summary": "일부 내용의 구체적인 명시가 필요합니다.",
        "createdAt": "2026-07-04T00:10:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "timestamp": "2026-07-02T21:15:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `R001` | 리뷰 링크를 찾을 수 없습니다. | 존재하지 않는 리뷰 링크 ID |

```json
{
  "success": false,
  "code": "R001",
  "message": "해당 리뷰 링크를 찾을 수 없습니다.",
  "errors": {
    "field": "reviewLinkId",
    "reason": "not found"
  },
  "timestamp": "2026-07-02T21:15:00"
}
```

#### GET /api/review-submissions/{reviewSubmissionId} — 제출된 리뷰 상세 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /api/review-submissions/{reviewSubmissionId}` |
| **도메인** | review |
| **설명** | 제출된 리뷰 1건의 상세 내역을 조회 |
| **인증** | ✅ 필요 |
| **권한** | OWNER, EDITOR, VIEWER |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reviewSubmissionId` | `Long` | Y | 조회할 리뷰 제출 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Response — 성공 (`200`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 제출된 리뷰 ID |
| `data.reviewLinkId` | `Long` | 연관된 리뷰 링크 ID |
| `data.reviewerName` | `String` | 제출자(검토자) 이름 |
| `data.understandingSignal` | `UnderstandingSignal` | 이해도 신호 |
| `data.createdAt` | `LocalDateTime` | 제출 일시 |

```json
{
  "success": true,
  "code": "REVIEW_SUBMISSION_FETCHED",
  "message": "해당 리뷰 제출 조회에 성공했습니다.",
  "data": {
    "id": 20,
    "reviewLinkId": 100,
    "reviewerName": "김나리",
    "understandingSignal": "CLEAR",
    "createdAt": "2026-07-02T21:00:00"
  },
  "timestamp": "2026-07-02T21:20:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `R003` | 리뷰 제출 내역을 찾을 수 없습니다. | 존재하지 않는 리뷰 제출 ID |

```json
{
  "success": false,
  "code": "R003",
  "message": "해당 리뷰 제출 내역을 찾을 수 없습니다.",
  "errors": {
    "field": "reviewSubmissionId",
    "reason": "not found"
  },
  "timestamp": "2026-07-02T21:20:00"
}
```

#### GET /public/review-links/{token} — 외부 검토 - 섹션 초안 조회

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `GET /public/review-links/{token}` |
| **도메인** | review |
| **설명** | 발급된 토큰을 통해 외부 검토자가 리뷰 링크를 조회합니다. |
| **인증** | ❌ 불필요 |
| **권한** | - |

**Request Header**
```
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `token` | `String` | Y | 리뷰 링크 고유 토큰 |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Response — 성공 (`200`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.sectionId` | `Long` | 프로젝트 섹션 ID |
| `data.title` | `String` | 프로젝트 섹션 제목 |
| `data.content` | `String` | 프로젝트 섹션 내용 (없으면 null) |

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "sectionId": 505,
    "title": "결제 도메인 화면 설계서 1차",
    "content": null
  }
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `R001` | 리뷰 링크를 찾을 수 없습니다. | 잘못된 리뷰 링크 토큰 |
| `403` | `R002` | 접근할 수 없는 링크입니다. | isActive가 false이거나 기한 만료 |

```json
{
  "success": false,
  "code": "R002",
  "message": "접근할 수 없는 링크입니다.",
  "errors": {
    "field": "isActive",
    "reason": "not True"
  },
  "timestamp": "2026-07-02T20:45:00"
}
```

#### PATCH /api/review-links/{reviewLinkId} — 리뷰 링크 정보 수정

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `PATCH /api/review-links/{reviewLinkId}` |
| **도메인** | review |
| **설명** | 단일 리뷰 링크의 정보(활성화 상태, 만료 일시) 수정 |
| **인증** | ✅ 필요 |
| **권한** | OWNER, EDITOR |

**Request Header**
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reviewLinkId` | `Long` | Y | 수정할 리뷰 링크 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `isActive` | `Boolean` | Y | - | 활성화 여부 |
| `expiresAt` | `LocalDateTime` | Y | - | 만료 일시 |

```json
{
  "isActive": false,
  "expiresAt": "2026-09-01T00:00:00"
}
```

**Response — 성공 (`200`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 리뷰 링크 ID |
| `data.isActive` | `Boolean` | 변경된 상태 |
| `data.expiresAt` | `LocalDateTime` | 변경된 만료 일시 |

```json
{
  "success": true,
  "code": "REVIEW_LINK_UPDATED",
  "message": "리뷰 링크 정보가 수정되었습니다.",
  "data": {
    "id": 1,
    "isActive": false,
    "expiresAt": "2026-09-01T00:00:00"
  },
  "timestamp": "2026-07-02T20:40:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `404` | `R001` | 리뷰 링크를 찾을 수 없습니다. | 존재하지 않는 리뷰 링크 ID |
| `400` | `C001` | 잘못된 입력입니다. | 유효하지 않은 입력값 |
| `403` | `A001` | 권한이 없습니다. | VIEWER가 요청한 경우 |

```json
{
  "success": false,
  "code": "INVALID_REQUEST_PARAMETER",
  "message": "잘못된 입력입니다.",
  "errors": {
    "field": "isActive",
    "reason": "must be Boolean"
  },
  "timestamp": "2026-07-02T20:40:00"
}
```

#### POST /public/review-links/{token}/submissions — 외부 검토 - 이해도 제출

| 항목 | 내용 |
| --- | --- |
| **Method / Path** | `POST /public/review-links/{token}/submissions` |
| **도메인** | review |
| **설명** | 외부 검토자가 리뷰를 제출 |
| **인증** | ❌ 불필요 |
| **권한** | - |

**Request Header**
```
Content-Type: application/json
```

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `token` | `String` | Y | 리뷰 링크 고유 토큰 |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| 없음 | - | - | - | - |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `understandingSignal` | `UnderstandingSignal` | Y | `CLEAR`, `PARTIAL`, `UNCLEAR` | 이해도 신호 |
| `reviewerName` | `String` | N | - | 제출자(검토자) 이름 |
| `summary` | `String` | N | - | 요약 및 검토 |

```json
{
  "understandingSignal": "CLEAR",
  "reviewerName": "홍길동",
  "summary": "전반적으로 이해하기 쉽습니다."
}
```

**Response — 성공 (`201`)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.id` | `Long` | 제출된 리뷰 ID |
| `data.understandingSignal` | `UnderstandingSignal` | 제출된 이해도 |

```json
{
  "success": true,
  "code": "REVIEW_SUBMITTED",
  "message": "리뷰가 성공적으로 제출되었습니다.",
  "data": {
    "id": 77,
    "understandingSignal": "CLEAR"
  },
  "timestamp": "2026-07-04T00:15:00"
}
```

**Response — 실패**

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| `403` | `R002` | 접근할 수 없는 링크입니다. | isActive가 false이거나 기한 만료 |
| `400` | `C001` | 잘못된 입력입니다. | 유효하지 않은 입력값 |

```json
{
  "success": false,
  "code": "R002",
  "message": "접근할 수 없는 링크입니다.",
  "errors": {
    "field": "isActive",
    "reason": "not True"
  },
  "timestamp": "2026-07-02T20:45:00"
}
```
