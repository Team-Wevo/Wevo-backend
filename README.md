# Wevo Backend

> 팀원들의 흩어진 의견을 AI가 정리하고 조율해 하나의 결과물로 완성하는 협업 워크스페이스

Wevo는 제안서와 발표 자료를 만드는 과정에서 팀원들의 의견을 실시간으로 수집하고,
AI가 의견 사이의 충돌과 공백을 찾아 근거가 추적되는 초안을 생성하도록 돕습니다.
팀 검토와 확정 과정을 거쳐 최종 결과물까지 하나의 흐름으로 관리하는 것이 목표입니다.

## 핵심 흐름

`프로젝트 생성` → `의견 수집` → `AI 퍼실리테이션` → `검토·확정` → `결과물 출력`

## 주요 기능

| 도메인 | 주요 역할 |
| --- | --- |
| 인증·팀·프로젝트 | Google·Kakao 로그인, 회원 및 팀 관리, 프로젝트와 섹션 생성 |
| 의견 수집·실시간 협업 | 의견 작성·제출, 수집 상태 관리, 상태 변화 실시간 공유 |
| AI 퍼실리테이터 | Provider 중립 AI gateway 기반 쟁점 감지, 의견 종합, 근거가 포함된 초안 생성 |
| 검토·확정·결과물 | 팀·외부 검토, 수정 요청, 섹션 확정, 결과물 내보내기 |
| 인프라·공통·DevOps | 공통 응답, 전역 예외 처리, API 문서, 로컬·배포 환경 관리 |

> 대표 기능과 API는 MVP 개발 과정에서 변경될 수 있습니다.

## 기술 스택

- Java 21
- Spring Boot, Spring Data JPA
- PostgreSQL 16, Redis 7
- Docker Compose
- Swagger / Springdoc OpenAPI
- Gradle, JUnit

구현 단계에서는 Spring Security·JWT·Google/Kakao OAuth, SSE 또는 WebSocket/STOMP와
서버 검증을 거치는 AI JSON 구조화 출력을 적용합니다.

## AI Provider

현재 운영 후보 기준선은 OpenAI `gpt-5.6-luna`와 명시적
`reasoning_effort=medium`이다. 기존 NVIDIA API Catalog 연동은 과거 개발·회귀 비교 기준선으로
유지하며 Provider 선택만으로 기능 서비스나 구조화 출력 검증 계약은 바뀌지 않는다.

```properties
AI_PROVIDER=openai
OPENAI_API_KEY=
OPENAI_API_BASE_URL=https://api.openai.com
OPENAI_API_MODEL=gpt-5.6-luna
OPENAI_API_TIMEOUT=60s
OPENAI_API_MAX_TOKENS=4096
OPENAI_API_REASONING_EFFORT=medium
```

OpenAI 연결과 opt-in synthetic smoke 절차는
[`docs/engineering/ai/openai-gpt-5-6-luna.md`](docs/engineering/ai/openai-gpt-5-6-luna.md)를 참고한다.

### NVIDIA 비교 기준선

로컬 개발과 명시적 통합 테스트에서는 NVIDIA API Catalog Free Endpoint의
`mistralai/mistral-medium-3.5-128b`를 사용합니다. 일반·구조화 호출은
`reasoning_effort=none`, temperature `0.1`을 기준으로 하며 구조화 호출은 JSON object 모드 이후에도
서버의 JSON Schema·record·semantic validation을 통과해야 합니다.

```properties
AI_PROVIDER=nvidia
NVIDIA_API_KEY=
NVIDIA_API_BASE_URL=https://integrate.api.nvidia.com
NVIDIA_API_MODEL=mistralai/mistral-medium-3.5-128b
NVIDIA_API_TEMPERATURE=0.1
NVIDIA_API_REASONING_EFFORT=none
```

NVIDIA Free Endpoint는 synthetic 데이터 기반 개발·평가에만 사용하며 운영 트래픽과 실제 사용자 의견을
전송하지 않습니다. 전체 설정은 `.env.example`을 기준으로 합니다.

## 패키지 구조

기능 단위의 도메인형 패키지 구조를 사용하며, 여러 도메인이 공유하는 코드는 `global`에서 관리합니다.

```text
com.wevo.backend
├── global
│   ├── config
│   ├── security
│   ├── exception
│   └── response
├── auth
├── user
├── project
├── section
├── opinion
├── ai
├── review
└── export
```

## 로컬 실행

로컬 데이터베이스 스키마는 Flyway가 생성하고 Hibernate는 엔티티 매핑 일치 여부만 검증합니다.
최초 실행은 `.env.example`을 `.env`로 복사해 값을 채운 뒤 진행합니다.

```powershell
docker compose up -d
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Flyway V1 도입 전에 `ddl-auto=update`로 만든 기존 로컬 DB가 있다면 필요한 데이터를 백업한 뒤
볼륨을 한 번 재생성해야 합니다. 자세한 절차와 이유는
`src/main/resources/db/migration/README.md`를 참고합니다.

## API 공통 규칙

- Base URL: `/api`
- 인증 방식: `Bearer JWT`
- Content-Type: `application/json`
- 모든 응답은 `ApiResponse<T>` 형식으로 통일
- 테이블과 컬럼은 `snake_case`, Enum은 문자열로 저장

## BE 역할

| 담당자 | 담당 도메인 |
| --- | --- |
| 한호석 | 인증·팀·프로젝트 기반 |
| 이윤호 | 의견 수집·실시간 협업 |
| 이종원 | AI 퍼실리테이터 |
| 조은솔 | 검토·확정·결과물 |
| 신진용 | 인프라·공통·DevOps |

## 협업 규칙

- `dev` 브랜치에서 작업 브랜치를 생성하고 Pull Request로 병합합니다.
- 브랜치명은 `타입/이슈번호-기능명` 형식을 사용합니다.
- 커밋 메시지는 `타입: 설명 (#이슈번호)` 형식을 사용합니다.
- Pull Request는 두 명 이상의 승인을 받은 뒤 Merge commit 방식으로 병합합니다.

## 운영 배포

AWS 운영 환경의 수동 배포, 상태 확인, 복구 및 롤백 절차는
[`DEPLOYMENT.md`](DEPLOYMENT.md)를 참고합니다.

## 관련 링크

- [Wevo Notion](https://app.notion.com/p/Wevo-34d400ecb7298049a641f56b59666de3)
- [Team Wevo GitHub](https://github.com/Team-Wevo)
- [Wevo Frontend](https://wevo-official.vercel.app/)
