# AI 관측·알림·모델 rollout 운영 계약

## 1. 운영 구성과 데이터 계약

- exporter/backend: Spring Boot Actuator + Micrometer Prometheus, Prometheus, Grafana.
- scrape endpoint: 운영 management loopback 포트의 `/actuator/prometheus`. 외부 공개 경로가 아니다.
- dashboard: `ops/monitoring/ai-dashboard.json`.
- alert rules: `ops/monitoring/ai-alert-rules.yml`.
- 기본 소유자: AI Backend. 인프라 scrape/rule 배포 소유자는 Infra.
- 수신 채널: warning은 `#wevo-ai-alerts`, critical은 같은 채널과 운영 on-call 호출을 함께 사용한다.
- 보존 기간: Prometheus 30일, Grafana dashboard 변경 이력 1년, `AiUsageLog` 감사 로그는 DB 보존
  정책에 따라 1년. 감사 로그와 시계열 metric은 서로 대체하지 않는다.

metric tag는 `feature`, `provider`, `model_family`, `prompt_version`, `schema_version`, `outcome`,
정규화된 `error_type`, token/event/guardrail 분류만 허용한다. project/section/user/request ID,
예외 메시지, prompt/completion, Provider 오류 body는 tag에 넣지 않는다. 알 수 없는 model은 `other`로
정규화해 외부 입력으로 시계열이 무한히 늘지 않게 한다.

## 2. 추적과 안전한 로그

한 논리 job의 연결 기준은 다음과 같다.

1. `AiJob.requestId`는 비동기 제품 요청의 공개 추적 ID다.
2. 각 논리 Provider 호출은 별도 `AiUsageLog.requestId`를 가진다. `AiUsageLog.aiJob_id`가 job을 연결한다.
3. Provider request ID는 응답 metadata에서 추출해 `AiUsageLog.providerRequestId`에 저장한다.
4. 애플리케이션 내부 correlation ID가 있는 요청은 HTTP 로그 context에서 유지하되 위 ID를 대신하지 않는다.

request ID는 구조화 로그 field로만 기록하고 metric tag로 사용하지 않는다. 완료 로그에는 ID 연결 field와
feature, 정규화 상태/오류, 실제 응답 model, prompt/schema version, latency, attempt만 기록한다. 예외 객체와
메시지는 로그로 넘기지 않는다. `spring.ai.chat.client.observations.log-prompt`와 `log-completion`은 모든
프로파일에서 `false`가 기본이며 운영에서 override하지 않는다. health indicator는 kill switch와 circuit
메모리 상태만 읽고 실 모델을 호출하지 않는다.

## 3. 초기 alert 기준

AI-12R의 schema valid rate 99% gate와 AI-13의 기능별 p95/쿼터 정책을 초기 기준으로 사용한다.

| 신호 | warning/critical | window·최소 표본 |
| --- | --- | --- |
| 401/403 | critical, 1건 이상 | 5분, 즉시 구성 확인 |
| upstream 429 | warning, 5건 이상 | 5분 |
| 5xx/timeout | critical, 실패율 10% 초과 | 10분, 최소 20건 |
| schema invalid | warning, 1% 초과 | 15분, 최소 30건 |
| end-to-end p95 | warning, 12초 초과 | 15분, 최소 30건 |
| 비용 속도 | warning, 최근 6시간 평균의 2배 | 15분, 성공 최소 20건 |
| quota/budget 거절 | warning, 10건 이상 | 10분 |
| Redis/정산 오류 | critical, 1건 이상 | 5분 |
| queue/RUNNING | warning, QUEUED 20건 또는 RUNNING 180초 | 5분 지속 |

최소 표본 미달 구간은 품질 저하로 단정하지 않는다. 기능별 승인 baseline이 추가되면 공통 12초 기준을
기능별 `baseline p95 + 합의한 회귀 폭`으로 교체한다. false positive는 alert를 즉시 끄지 않고 발생 시각,
표본 수, 배포/트래픽 변화와 원인을 기록한 뒤 window → 최소 표본 → threshold 순으로 조정한다.

## 4. 장애 대응

모든 alert는 확인(ack) → 영향 범위 확인 → 완화 → 회복 조건 확인 → 종료와 회고 순서로 처리한다.

### Provider 인증/권한

- 배포된 secret의 존재와 model 접근 권한을 secret 값 출력 없이 확인한다.
- 오류가 계속되면 `AI_OPERATIONS_ENABLED=false`로 전역 AI 실행을 중지한다.
- 새 AI 실행이 503 `AI008`로 거절되고 기존 결과 조회와 비AI 기능이 정상인지 확인한다.
- 올바른 secret/권한 적용 뒤 synthetic project 한 건으로 복구를 확인하고 switch를 되돌린다.

### 429/5xx/timeout

- provider 상태와 feature/model별 오류율, queue를 확인한다.
- 연속 실패가 threshold에 도달하면 circuit이 open되어 새 Provider 호출을 막는다. 30초 뒤 half-open은
  기본 1건만 허용한다.
- 장애가 길면 kill switch를 적용한다. 평가되지 않은 다른 Provider/model로 자동 fallback하지 않는다.
- 10분 동안 실패율이 1% 미만이고 half-open probe가 성공하면 종료한다.

### schema/품질 회귀

- model family, prompt/schema version과 rollout ID별로 비교한다.
- 최소 표본을 충족했다면 canary percentage를 0으로 되돌리거나 직전 승인 조합을 route baseline으로
  원자 교체한 뒤 재배포한다.
- 동일 AI-12R dataset gate와 사람 리뷰를 다시 통과하기 전에는 후보를 재확대하지 않는다.

### latency/비용 회귀

- Provider와 end-to-end를 비교해 queue/애플리케이션과 upstream을 구분한다.
- attempt, output/reasoning/cache token, 성공 1건당 비용을 baseline과 비교한다.
- 비용 급증 시 canary를 중단하고 필요하면 kill switch를 적용한다. 단가 누락은 0원으로 간주하지 않는다.

### quota/예산/Redis

- `request_quota`, `project_quota`, `budget`, `redis_fail_closed`, `settlement_error`를 구분한다.
- Redis 장애는 fail-closed가 정상이다. Redis key를 로그/alert label에 복사하지 않는다.
- AI 결과 조회·의견 작성·수동 초안 편집/검토가 Redis guardrail을 호출하지 않는지 확인한다.
- 정산 오류는 ledger 상태를 확인하고 중복 환급 없이 재처리한 뒤 종료한다.

### queue/worker

- QUEUED 수, oldest RUNNING, heartbeat recovery, orphan recovery, stale 폐기 추이를 함께 본다.
- worker/DB 상태를 확인하고 새 요청 폭주가 원인이면 kill switch로 유입만 멈춘다.
- RUNNING age가 180초 아래이고 backlog가 10분 동안 계속 감소하면 종료한다.

## 5. 승인된 rollout과 rollback

rollout 설정은 `wevo.ai.rollout` 아래의 불변 combination과 기능 route로 관리한다. combination은 model,
prompt/schema, reasoning, pricing, guardrail policy를 한 묶음으로 만들며 `evaluated=true`와
`human-reviewed=true`가 모두 아니면 애플리케이션 시작이 실패한다. model pricing, 현재 policy,
classpath prompt와 서버 schema version 중 하나라도 누락/불일치하면 적용하지 않는다.

```yaml
wevo:
  ai:
    rollout:
      enabled: true
      combinations:
        stable-luna-medium-v2:
          model-id: gpt-5.6-luna
          prompt-version: draft-generation:v2
          schema-version: draft-generation:v1
          reasoning-effort: medium
          pricing-version: openai-2026-07-31-standard
          policy-version: ai-guardrail-2026-08-02-v1
          evaluated: true
          human-reviewed: true
        candidate-luna-medium-v1:
          model-id: gpt-5.6-luna
          prompt-version: draft-generation:v1
          schema-version: draft-generation:v1
          reasoning-effort: medium
          pricing-version: openai-2026-07-31-standard
          policy-version: ai-guardrail-2026-08-02-v1
          evaluated: true
          human-reviewed: true
      routes:
        draft-generation:
          baseline: stable-luna-medium-v2
          candidate: candidate-luna-medium-v1
          canary-percent: 5
          synthetic-project-ids: [1001]
```

절차는 AI-12R 동일 dataset 자동 gate와 사람 리뷰 → synthetic project → 5% canary → 25% → 50% →
100% 순서다. 각 단계는 최소 표본과 alert window를 채운 뒤 확대한다. route는 feature/project/section의
안정적 hash로 고정되며 retry는 최초 `AiJob` snapshot을 복사하므로 실행 중 설정 변경에도 model/prompt가
바뀌지 않는다. 실제 응답 model은 `AiUsageLog`에 별도로 기록한다.

rollback은 route의 baseline/candidate/percentage를 직전 승인 combination으로 한 번에 바꿔 재배포한다.
부분 field만 되돌리지 않는다. 이미 생성된 job은 기존 snapshot으로 끝나고, 새 job부터 rollback 조합을
선택한다. Provider 자동 fallback은 없다.

## 6. kill switch 허용 범위

전역 `AI_OPERATIONS_ENABLED=false` 또는 기능별 `wevo.ai.operations.features.<feature>=false`는 새 job
생성/명시적 retry와 Provider 실행만 503 `AI008`로 차단한다. 기존 AI 결과 조회, 의견 작성, 수동 초안
편집·검토·확정은 이 제어 컴포넌트를 호출하지 않으므로 유지된다. circuit open도 새 실행만 막으며 기존
결과를 삭제하거나 숨기지 않는다.
