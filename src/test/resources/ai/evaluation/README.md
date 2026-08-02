# AI evaluation dataset and report

## Fixture contract

평가 fixture는 Provider 중립 JSON이며 기능별로
`src/test/resources/ai/evaluation/{feature}/`에 둔다. 현재 schema는
`schema/fixture-v1.schema.json`, 초기 dataset index는
`issue-detection/dataset-v1.index.json`이다. ID는 DB ID가 아닌 안정적인 문자열만 사용한다.
모든 live fixture는 `metadata.synthetic=true`여야 하며 실제 사용자 데이터, 개인정보, 비밀값을
넣지 않는다.

fixture는 다음 두 종류로 나눈다.

- `CONTRACT`: JSON/schema/enum/evidence allowlist처럼 결정적으로 판정할 수 있는 계약
- `QUALITY`: 의미상 충돌·공백·중립성처럼 자동 지표와 사람 평가를 함께 요구하는 사례

`requiredFacts`, `expectedIssues`, `expectedEvidenceIds`, `forbiddenClaims`는 전체 문장 exact match를
요구하지 않는다. 자동 평가는 issue type, evidence ID, 핵심어 포함 여부와 구조를 사용한다.

schema version은 필드의 필수 여부, 타입 또는 enum 계약이 바뀔 때 증가시킨다. 같은 major schema
version 안에서는 optional 필드 추가만 하위 호환으로 허용한다. required 필드 추가·삭제, 의미 변경,
enum 값 변경은 새 schema version과 새 dataset version을 만든다. 배포된 fixture는 의미를 바꾸지 않고
교정이 필요하면 dataset version을 증가시킨다.

## Metric semantics

- `schemaValidRate`: skip/budget 차단을 제외하고 Provider 호출이 시작된 실행 중 보정 재시도 후 최종
  typed entity가 만들어진 비율이다. 분모가 0이면 `NOT_MEASURABLE`이다.
- missing required field와 invalid enum은 schema failure의 세부 원인이므로 error distribution과
  의도적으로 중복 집계될 수 있다. 공통 gateway가 세부 진단을 제공하지 않은 live 실패는 0건으로
  바꾸지 않고 `NOT_MEASURABLE`로 둔다. unknown evidence ID, forbidden claim도 한 결과에서 동시에 집계된다.
- evidence coverage는 evidence가 필요한 핵심 claim 중 입력 allowlist의 ID가 하나 이상 연결된 비율이다.
  대상 claim이 0건이면 100%가 아니라 `NOT_MEASURABLE`이다.
- issue precision/recall은 현재 초기 dataset에서 `CONFLICT`/`GAP` 유형의 multiset을 gold와 비교한다.
  AI-06 이후 기능별 stable issue key가 생기면 schema version을 올려 더 세밀한 matching을 추가한다.
- nullable token은 실행 하나라도 값을 제공하지 않으면 aggregate total을 `null`로 둔다. 일부 실행만
  값을 제공하면 `PARTIALLY_MEASURED`, 전혀 제공하지 않으면 `NOT_MEASURABLE`로 구분하며 cache token이
  없는 Provider를 0으로 간주하지 않는다.
- 비용은 모든 실행이 pricing snapshot을 가질 때만 합산한다. 단가가 없는 NVIDIA Trial 결과는
  `estimatedCost=null`, `UNPRICED`다.
- latency는 Provider 호출 시작부터 공통 JSON parse → schema → record → semantic validation 완료까지의
  runner 경계 시간이다. 시작된 성공·실패 실행은 percentile에 포함하고 호출 전 skip/budget 차단은
  제외한다. p50/p95는 nearest-rank 방식이며 0건은 `null`이다.
- 공통 응답은 transport retry와 schema correction을 합산한 전체 `attemptCount`만 제공하므로 둘을
  report에서 분리하거나 `attemptCount`로 보정 재시도 소진을 추론하지 않는다. 현재
  `correctionExhaustedCount`는 `NOT_MEASURABLE`이며, 공통 응답에 두 시도를 구분하는 metadata가 추가된
  뒤에만 집계한다.

기본 자동 gate 제안은 schema valid rate 99%, unknown evidence 0건, evidence coverage 100%,
미확인/금지 사실 0건이다. 비율과 percentile은 실행 표본 20건 미만이면 gate에 사용하지 않고
`INSUFFICIENT_SAMPLES`로 표시한다. `QUALITY` fixture가 있으면 자동 gate를 통과해도 최종 상태는
`REVIEW_REQUIRED`다.

사람 평가는 각 QUALITY fixture를 1~5점으로 채점한다.

| 항목 | 1점 | 3점 | 5점 |
| --- | --- | --- | --- |
| 정확성 | 핵심 의미 왜곡 | 일부 누락 | gold 의미를 정확히 보존 |
| 근거성 | 근거 없이 단정 | 일부만 추적 가능 | 모든 핵심 항목이 입력 근거로 추적 가능 |
| 중립성 | 특정 의견을 임의 채택 | 소수 의견 표현이 약함 | 합의·다수·소수·충돌을 공정하게 구분 |
| 명확성 | 행동 불가능하거나 모호 | 추가 해석 필요 | 팀이 바로 검토·결정할 수 있음 |

## Running and reports

offline parser, metric, budget, reporter 테스트는 기본 테스트에 포함된다.

```bash
./gradlew test --tests 'com.wevo.backend.ai.evaluation.*'
```

NVIDIA Trial live evaluation은 기본 `test`에서 제외되며 다음 세 조건이 모두 필요하다.

```bash
NVIDIA_API_KEY=... \
NVIDIA_EVALUATION_ENABLED=true \
./gradlew nvidiaEvaluationTest
```

기본 개발·평가 모델은 `mistralai/mistral-medium-3.5-128b`, temperature는 `0.1`, reasoning effort는
`none`이다. 구조화 호출은 JSON object 모드를 요청한 뒤에도 공통 JSON Schema·record·semantic 검증을
반드시 통과해야 한다. 다른 모델이나 옵션을 평가할 때는 `NVIDIA_API_MODEL` 등 환경 변수로 명시적으로
override하고 report metadata가 실제 실행 설정과 일치하는지 확인한다.

`NVIDIA_INTEGRATION_ENABLED`는 smoke 전용이므로 evaluation을 활성화하지 않는다. live runner에는
항상 fixture 수, Provider 요청 수, 출력 token, deadline을 설정하고 pricing이 있는 모델에서만 비용
budget을 추가한다. 현재 integration scaffold는 synthetic fixture 1개, 최대 Provider 시도 3회,
출력 token 256개, deadline 60초로 제한한다.

OpenAI 다건 평가는 smoke opt-in과 분리된 `OPENAI_EVALUATION_ENABLED=true`가 필요하다. 먼저
`medium` 기준선을 만들고, 같은 git commit·model·dataset·prompt·schema·max output token 조건에서
reasoning effort만 `low`로 바꿔 challenger를 실행한다.

```bash
OPENAI_API_KEY=... \
OPENAI_EVALUATION_ENABLED=true \
OPENAI_API_REASONING_EFFORT=medium \
GIT_COMMIT=$(git rev-parse HEAD) \
./gradlew openAiEvaluationTest

OPENAI_API_KEY=... \
OPENAI_EVALUATION_ENABLED=true \
OPENAI_API_REASONING_EFFORT=low \
GIT_COMMIT=$(git rev-parse HEAD) \
./gradlew openAiEvaluationTest
```

실행기는 네 dataset 전체에 하나의 전역 fixture/request/output-token/deadline/cost budget을 적용한다.
한 fixture의 최악 재시도 횟수와 출력 token을 다음 호출 전에 예약하므로 남은 budget으로 완료할 수 없는
fixture는 Provider 호출을 시작하지 않고 `BUDGET_EXHAUSTED`로 기록한다. 기본 상한은 `.env.example`의
`OPENAI_EVALUATION_*`가 정본이다. `OPENAI_INTEGRATION_ENABLED`만으로는 이 task가 실행되지 않는다.

report는 `build/reports/ai-evaluation/openai/{medium|low}/`에 기능별 JSON/Markdown으로 생성된다.
`low` 실행은 같은 위치의 `medium` report가 있고 report schema, dataset, output schema, fixture 수와 ID
집합이 모두 같을 때만 delta를 계산한다. 자동 gate와 사람 평가는 각각 `gate`, `humanReview`로 분리하며,
live 생성 직후 사람 평가는 `PENDING`이다. 승인자는 QUALITY fixture의 정확성·근거성·중립성·명확성을
각 1~5점으로 모두 기록한 뒤에만 `COMPLETED`로 바꿀 수 있다.

report는 `build/reports/ai-evaluation/` 아래 JSON과 Markdown으로 생성하며 Git에 포함하지 않는다.
JSON은 `schema/report-v1.schema.json`의 machine-readable schema version `1.0`을 갖고 Markdown은 리뷰 요약이다. report에는 fixture ID와
정규화된 실패 유형만 포함하며 prompt, completion, 의견 전문, Provider error body, Authorization header,
API key를 절대 포함하지 않는다. 새 Provider·model·prompt 또는 temperature/max output token 변경 PR은
동일 dataset version의 baseline report와 현재 report를 첨부한다. baseline delta는 report schema,
dataset version, output schema version, fixture 수와 fixture ID 집합이 모두 같을 때만 계산한다.
Provider·model·prompt와 실행 옵션은 비교 대상이므로 동일하지 않아도 된다. NVIDIA Trial baseline은
개발·테스트 비교용이며 운영 Provider 출시 승인으로 간주하지 않는다.
