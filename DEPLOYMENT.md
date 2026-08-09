# Wevo Backend 운영 배포 런북

이 문서는 AWS 운영 환경의 수동 배포, 상태 확인, 복구 및 롤백 절차를 정리합니다.
실제 비밀번호, 토큰, AWS 계정 ID, 리소스 ID와 엔드포인트는 기록하지 않습니다.

## 운영 구성

```text
Vercel Frontend (HTTPS)
        |
        v
EC2 Nginx (80/443)
        |
        v
Spring Boot Container (127.0.0.1:8080)
        |                   |
        v                   v
RDS PostgreSQL       Redis Container
```

- AWS 리전: 서울(`ap-northeast-2`)
- 배포 디렉터리: `/opt/wevo`
- Compose 파일: `/opt/wevo/compose.prod.yml`
- 운영 환경변수: `/opt/wevo/.env.prod`
- 컨테이너 이미지는 ECR에서 Pull합니다.
- Spring Boot 포트는 EC2의 `127.0.0.1`에만 공개하고 외부 요청은 Nginx를 통과시킵니다.
- Actuator 헬스 엔드포인트는 컨테이너 내부 `127.0.0.1:8081`에서만 접근할 수 있습니다.
- 운영 프로필의 코드 기본값은 Swagger UI와 OpenAPI 문서 비활성화입니다.
  평가 기간에만 §「평가 기간 임시 Swagger 공개」의 런타임 옵션으로 제한적으로 활성화할 수 있습니다.

## 보안 원칙

- 루트 계정이나 장기 액세스 키로 일반 배포 작업을 하지 않습니다.
- `.env.prod`는 EC2에만 두고 파일 권한 `600`을 유지합니다.
- `.env.prod`, 토큰, 비밀번호를 터미널에 출력하거나 화면 캡처하지 않습니다.
- RDS와 Redis는 서로 다른 비밀번호를 사용하며, `REDIS_PASSWORD`는 운영 배포의 필수 값입니다.
- `docker compose config`는 비밀 값이 출력될 수 있으므로 사용하지 않습니다.
  구문 검증에는 반드시 `docker compose config --quiet`을 사용합니다.
- 배포 로그와 문서에 이메일 등 개인 식별정보를 남기지 않습니다.

## 배포 전 확인

로컬에서 다음 작업을 완료합니다.

```powershell
.\gradlew.bat test
git status --short
git log -1 --oneline
```

운영 이미지에는 `latest`와 함께 커밋 SHA 기반의 변경 불가 태그를 부여하는 것을 권장합니다.
롤백 대상이 명확해질 때까지 이전 이미지를 삭제하지 않습니다.

## GitHub Actions 자동 배포

`dev` 브랜치에 변경이 반영되면 `.github/workflows/deploy.yml`이 다음 순서로 운영 배포를 수행합니다.

1. Gradle 테스트 실행
2. GitHub OIDC로 단기 AWS 자격 증명 발급
3. 커밋 SHA를 태그로 사용해 Docker 이미지 빌드 및 ECR Push
4. 같은 커밋의 `compose.prod.yml`과 배포 스크립트를 Systems Manager Run Command로 EC2에 전달
5. 서버의 `.env.prod`로 새 Compose를 사전 검증하고 필수 값이 빠졌으면 기존 app을 교체하지 않고 중단
6. 검증된 Compose를 `/opt/wevo/compose.prod.yml`에 원자적으로 설치하고 app 재생성
7. 컨테이너 내부 Actuator 응답이 `UP`인지 확인

GitHub 저장소의 `Settings > Secrets and variables > Actions > Variables`에는 다음 Repository variable이
등록되어 있어야 합니다. 이 값들은 비밀번호가 아니며 운영 비밀 값은 계속 EC2의 `.env.prod`에서만
관리합니다.

| 변수 | 용도 |
| --- | --- |
| `AWS_ROLE_ARN` | GitHub OIDC가 맡을 배포 역할 ARN |
| `AWS_REGION` | ECR, EC2, SSM 리전 |
| `ECR_REPOSITORY` | 이미지를 Push할 ECR 저장소 이름 |
| `EC2_INSTANCE_ID` | SSM 배포 대상 EC2 인스턴스 |

IAM 신뢰 정책은 `Team-Wevo/Wevo-backend` 저장소의 `dev` 브랜치로 제한합니다. 배포 역할에는 지정된
ECR 저장소 Push와 지정된 EC2에 대한 SSM 명령 실행·조회 권한만 부여하며, 정적 AWS Access Key를
GitHub Secrets에 등록하지 않습니다. EC2 인스턴스 역할에는 SSM Agent 통신과 ECR Pull 권한이
필요합니다.

수동 실행은 Actions의 `Production CD` 워크플로에서 할 수 있지만, OIDC 신뢰 조건과 동일하게 `dev`
브랜치에서만 배포 작업이 실행됩니다. 실패 시 해당 Actions 실행의 `Deploy through Systems Manager`
단계에서 SSM 상태, 실패 단계, 해당 배포 시작 이후 앱 로그와 root-cause 발췌를 확인합니다.

자동 배포는 이미지와 같은 커밋의 Compose SHA-256을 로그에 남기고, 기존 운영 Compose를
`/opt/wevo/compose.prod.yml.previous`에 보존합니다. 신규 Compose의 필수 환경변수 검증은 image pull과
app 재생성보다 먼저 수행하므로 설정 누락만으로 기존 정상 app을 내리지 않습니다. 자동 이미지 롤백은
Flyway 전진 호환성을 보장할 수 없어 수행하지 않으며 아래 수동 롤백 절차를 사용합니다.

CI의 `.github/scripts/test-production-deployment-assets.sh`는 정상 예시 환경에서 Compose 검증이
통과하는지, DB·Redis·JWT·프론트 주소·초대·AI Provider 필수값을 하나씩 제거한 환경에서는 배포 전
검증이 실패하는지, CD가 같은 commit의 Compose를 전달하는지 검사합니다.

## EC2 수동 배포

Session Manager로 EC2에 접속한 뒤 실행합니다.
새 필수 환경변수가 추가된 배포라면 먼저 `.env.prod`에 운영 값을 안전하게 등록하고 파일 권한 `600`을
유지합니다. 실제 값이나 `.env.prod` 전체 내용을 출력하지 않습니다. 수동 배포에서도 반드시 배포할
commit의 `compose.prod.yml`을 `/opt/wevo/compose.prod.yml`에 먼저 동기화해야 합니다.

```bash
cd /opt/wevo
sudo test "$(stat -c '%a' .env.prod)" = "600"
sudo docker compose --env-file .env.prod -f compose.prod.yml config --quiet
sudo docker compose --env-file .env.prod -f compose.prod.yml pull app
sudo docker compose --env-file .env.prod -f compose.prod.yml up -d --force-recreate redis app
sudo docker compose --env-file .env.prod -f compose.prod.yml ps
```

첫 운영 부팅은 Flyway와 애플리케이션 초기화 때문에 약 2분 걸릴 수 있습니다.
컨테이너가 `healthy`가 될 때까지 로그와 상태를 확인합니다.
Redis 인증을 최초 적용할 때는 위 명령처럼 Redis와 앱을 함께 재생성해야 합니다.

```bash
sudo docker compose --env-file .env.prod -f compose.prod.yml logs --tail=500 app
sudo docker compose --env-file .env.prod -f compose.prod.yml exec -T app \
  wget -qO- http://127.0.0.1:8081/actuator/health
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' http://127.0.0.1:8080/v3/api-docs
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' http://127.0.0.1/v3/api-docs
sudo nginx -t
sudo systemctl is-active nginx
```

정상 기준은 다음과 같습니다.

- 앱과 Redis 컨테이너가 `healthy`
- Flyway 마이그레이션 성공
- RDS 연결 성공
- Actuator 응답 `{"status":"UP"}` (DB와 Redis 상태 포함)
- Swagger 기본 비활성 상태에서는 Spring Boot 직접·Nginx 경유 API 문서 요청 HTTP 404
- 평가 기간 임시 공개 상태에서는 Spring Boot 직접·Nginx 경유 API 문서 요청 HTTP 200
- Nginx 설정 검사 성공

## HTTPS 적용 확인

백엔드 도메인의 A 레코드는 EC2 Elastic IP를 가리켜야 합니다.
도메인과 인증서를 적용한 뒤 다음 항목을 확인합니다.

```bash
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' https://<backend-domain>/v3/api-docs
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' https://<backend-domain>/swagger-ui/index.html
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' https://<backend-domain>/
curl -sS -I http://<backend-domain>
sudo certbot renew --dry-run
```

- Swagger 기본 비활성 상태에서는 `/v3/api-docs`와 `/swagger-ui/index.html`이 HTTP 404
- 평가 기간 임시 공개 상태에서는 `/v3/api-docs`와 `/swagger-ui/index.html`이 HTTP 200
- 루트 `/`는 헬스 엔드포인트가 아니며, 인증 없이 요청하면 정상적으로 HTTP 401(`A001`)
- HTTP 요청이 HTTPS로 301 또는 308 리다이렉트
- 인증서 자동 갱신 모의 실행 성공

## 평가 기간 임시 Swagger 공개

`application-prod.yml`은 기본적으로 `springdoc.api-docs.enabled=false`와
`springdoc.swagger-ui.enabled=false`를 적용합니다. 최종 평가자가 API 계약을 확인해야 하는 기간에만
EC2의 `.env.prod`에 이미 있는 `JAVA_TOOL_OPTIONS` 값을 보존하면서 다음 JVM 시스템 속성을 추가할 수
있습니다.

```text
-Dspringdoc.api-docs.enabled=true
-Dspringdoc.swagger-ui.enabled=true
```

`.env.prod` 또는 `JAVA_TOOL_OPTIONS` 전체를 화면이나 로그에 출력하지 않습니다. 다음과 같이 각 옵션의
존재 여부만 확인합니다.

```bash
sudo docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' wevo-backend-app-1 \
  | grep -q 'JAVA_TOOL_OPTIONS=.*-Dspringdoc.api-docs.enabled=true'
sudo docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' wevo-backend-app-1 \
  | grep -q 'JAVA_TOOL_OPTIONS=.*-Dspringdoc.swagger-ui.enabled=true'
```

2026년 8월 8일 운영 점검에서는 두 옵션이 모두 적용되어 있었고,
`https://api.wevo.kr/v3/api-docs`와 `https://api.wevo.kr/swagger-ui/index.html`이 HTTP 200이었습니다.
평가 종료 후에는 두 `springdoc` 옵션만 제거하고 app 컨테이너를 재생성한 뒤 두 경로가 다시 HTTP 404인지
확인합니다. 메모리 비율과 KST 시간대 등 기존 `JAVA_TOOL_OPTIONS`의 다른 값은 삭제하지 않습니다.

## 상태와 로그 확인

```bash
cd /opt/wevo
sudo docker compose --env-file .env.prod -f compose.prod.yml ps
sudo docker compose --env-file .env.prod -f compose.prod.yml logs --tail=500 app
sudo docker compose --env-file .env.prod -f compose.prod.yml logs --tail=100 redis
sudo journalctl -u nginx --since "30 minutes ago" --no-pager
```

로그를 공유할 때 토큰, `Authorization` 헤더, 이메일, DB 접속 정보가 없는지 확인합니다.

## 수동 롤백

새 이미지를 Pull하기 전에 현재 실행 중인 이미지 ID를 로컬 롤백 태그로 보존할 수 있습니다.

```bash
CURRENT_WEVO_IMAGE_ID="$(sudo docker inspect --format '{{.Image}}' wevo-backend-app-1)"
sudo docker image tag "$CURRENT_WEVO_IMAGE_ID" wevo-backend:rollback
```

새 버전에 문제가 생기면 보존한 이미지로 앱만 되돌립니다.

```bash
cd /opt/wevo
sudo env APP_IMAGE=wevo-backend:rollback docker compose \
  --env-file .env.prod -f compose.prod.yml up -d --force-recreate app
sudo docker compose --env-file .env.prod -f compose.prod.yml ps
sudo docker compose --env-file .env.prod -f compose.prod.yml logs --tail=100 app
sudo docker compose --env-file .env.prod -f compose.prod.yml exec -T app \
  wget -qO- http://127.0.0.1:8081/actuator/health
```

롤백 후에도 DB 마이그레이션 호환성을 확인해야 합니다. 이미 적용된 Flyway 마이그레이션은
되돌리거나 수정하지 않습니다.

## EC2 재부팅 복구 확인

앱과 Redis에는 `restart: unless-stopped`가 설정되어 있고 Docker 서비스가 활성화되어 있어야 합니다.
데모 전 계획된 점검 시간에만 재부팅합니다.

```bash
sudo systemctl is-enabled docker
sudo systemctl is-enabled nginx
sudo reboot
```

재접속 후 다음을 확인합니다.

```bash
cd /opt/wevo
sudo docker compose --env-file .env.prod -f compose.prod.yml ps
sudo systemctl is-active nginx
sudo docker compose --env-file .env.prod -f compose.prod.yml exec -T app \
  wget -qO- http://127.0.0.1:8081/actuator/health
```

## 제출 전 점검

- HTTPS 프론트엔드에서 Mixed Content와 CORS 오류가 없음
- Google 또는 Kakao 로그인과 JWT 발급 성공
- 토큰 재발급과 로그아웃 성공
- 프로젝트 생성·조회 성공
- Redis를 사용하는 편집 잠금 흐름 성공
- 초대 링크가 운영 프론트엔드 주소를 사용
- 평가 기간에는 Swagger UI와 API 문서가 HTTP 200, 평가 종료 후에는 두 경로가 다시 HTTP 404
- EC2 재부팅 또는 컨테이너 재생성 후 자동 복구 성공
- 제출 링크의 공개 권한과 주소를 최종 확인
