package com.wevo.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 문서 설정.
 *
 * <p><b>Bearer 인증 스키마를 정의한다.</b> 스키마가 없으면 Swagger UI 에 {@code Authorize} 버튼이
 * 생기지 않아, 인증이 필요한 API 를 브라우저에서 시험할 수 없다. (curl 로만 확인 가능)
 * 스키마를 <b>문서 전역 요구사항</b>으로 걸어, 별도 표기가 없는 모든 API 는 로그인 사용자만
 * 호출할 수 있다는 규칙(CLAUDE.md §5.2)이 문서에도 그대로 나타나게 한다.
 *
 * <p>인증 없이 접근하는 경로는 각 메서드에 {@code @SecurityRequirements}(빈 값)를 붙여 전역
 * 요구사항을 해제한다 — 목록은 {@code SecurityConfig.PUBLIC_ENDPOINTS} 가 정본이며 둘을 항상
 * 일치시킨다.
 *
 * <p><b>운영에서는 이 문서 자체가 노출되지 않는다.</b> {@code application-prod.yml} 이
 * {@code springdoc.api-docs.enabled} 와 {@code swagger-ui.enabled} 를 모두 {@code false} 로 두므로,
 * 이 설정은 로컬·개발 환경의 확인용이다.
 */
@Configuration
public class OpenApiConfig {

    /**
     * 보안 스키마 이름. Swagger UI 의 {@code Authorize} 창 제목으로 노출되고,
     * 각 API 의 자물쇠 표시가 이 이름을 참조한다.
     */
    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    /**
     * 공통 실패 응답 예제. 키가 곧 참조 이름이다
     * ({@code @ExampleObject(ref = "#/components/examples/invalidInput")}).
     *
     * <p>예제를 실패 응답에 우선 붙이는 이유 — 성공 응답은 한 번 호출해보면 모양을 알 수 있지만,
     * 실패는 조건을 만들어야 재현되므로 FE 가 확인하기 어렵다.
     *
     * <p>시각은 <b>오프셋 없는 KST</b> 다({@code CLAUDE.md §5.4}). {@code Z}·{@code +09:00} 이
     * 붙지 않는다는 점이 타입만으로는 드러나지 않아 예제로 보여준다.
     */
    private static final Map<String, Example> COMMON_FAILURE_EXAMPLES = Map.of(
            "invalidInput", failureExample(
                    "요청 검증 실패 (C001) — 필드 단위 사유가 errors 에 담긴다",
                    """
                            {
                              "success": false,
                              "code": "C001",
                              "message": "잘못된 요청입니다.",
                              "errors": [
                                { "field": "title", "reason": "공백일 수 없습니다" }
                              ],
                              "timestamp": "2026-08-06T18:10:00"
                            }"""),
            "unauthorized", failureExample(
                    "인증 실패 (A001) — 토큰 없음·만료",
                    """
                            {
                              "success": false,
                              "code": "A001",
                              "message": "인증이 필요합니다.",
                              "timestamp": "2026-08-06T18:10:00"
                            }"""),
            "forbidden", failureExample(
                    "권한 부족 (A002) — 멤버지만 OWNER 가 아님",
                    """
                            {
                              "success": false,
                              "code": "A002",
                              "message": "접근 권한이 없습니다.",
                              "timestamp": "2026-08-06T18:10:00"
                            }"""),
            "projectNotFound", failureExample(
                    "프로젝트 없음 또는 비멤버 (P001) — 존재 숨김이라 둘을 같은 응답으로 돌려준다",
                    """
                            {
                              "success": false,
                              "code": "P001",
                              "message": "프로젝트를 찾을 수 없습니다.",
                              "timestamp": "2026-08-06T18:10:00"
                            }"""),
            "sectionNotFound", failureExample(
                    "섹션 없음 또는 비멤버 (S001) — 존재 숨김",
                    """
                            {
                              "success": false,
                              "code": "S001",
                              "message": "섹션을 찾을 수 없습니다.",
                              "timestamp": "2026-08-06T18:10:00"
                            }"""),
            "conflict", failureExample(
                    "상태·버전 충돌 (C003)",
                    """
                            {
                              "success": false,
                              "code": "C003",
                              "message": "요청이 현재 상태와 충돌합니다.",
                              "timestamp": "2026-08-06T18:10:00"
                            }"""));

    private static Example failureExample(String summary, String body) {
        return new Example().summary(summary).value(body);
    }

    @Bean
    public OpenAPI wevoOpenApi() {
        return new OpenAPI()
                .info(info())
                .components(commonComponents())
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }

    /**
     * 여러 API 가 공유하는 실패 응답 예제를 한 곳에 등록한다.
     *
     * <p>컨트롤러에서는 {@code @ExampleObject(ref = "#/components/examples/이름")} 으로 참조한다 —
     * 같은 본문을 각 컨트롤러에 복사하면 응답 계약이 바뀔 때 일부만 고쳐져 문서가 갈린다.
     *
     * <p><b>성공 응답 예제는 여기 두지 않는다.</b> {@code code}·{@code message}·{@code data} 가
     * API 마다 달라 공유할 것이 없다. 각 컨트롤러에서 개별로 작성한다.
     */
    private Components commonComponents() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme());
        COMMON_FAILURE_EXAMPLES.forEach(components::addExamples);
        return components;
    }

    /**
     * 문서 제목·설명. 설정하지 않으면 Springdoc 기본값인 {@code OpenAPI definition} 으로 표시된다.
     *
     * <p>{@code version} 은 <b>문서의 판</b>이지 URL 경로 버전이 아니다 — MVP 에서는
     * {@code /api/v1} 같은 경로 버전을 쓰지 않는다. (CLAUDE.md §5.1)
     */
    private Info info() {
        return new Info()
                .title("Wevo API")
                .version("MVP")
                .description("""
                        팀원들의 흩어진 의견을 AI가 정리·조율해 하나의 결과물로 완성하는 협업 워크스페이스.

                        **인증** — 우측 상단 `Authorize` 에 Access Token 을 넣으면 이후 요청에
                        `Authorization: Bearer {token}` 이 자동으로 붙는다. `Bearer ` 접두어는 제외하고
                        토큰 값만 입력한다.

                        토큰은 `POST /api/auth/login` 응답의 `data.accessToken` 이며, 로컬에서는
                        `POST /api/auth/dev-login` 으로 소셜 인증 없이 발급받을 수 있다.

                        자물쇠가 없는 API(로그인·토큰 재발급·공개 검토)는 인증 없이 호출한다.

                        엔드포인트별 계약의 정본은 `docs/API_SPEC.md` 이며, 이 문서는 구현 확인용
                        보조 문서다.""");
    }

    /**
     * {@code Authorization: Bearer {token}} 헤더를 쓰는 JWT 스키마.
     *
     * <p>{@code bearerFormat} 은 문서 표기용 힌트일 뿐 검증에 쓰이지 않는다. 실제 토큰 검증은
     * {@code JwtAuthenticationFilter} 가 한다.
     */
    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Access Token 값만 입력한다. (`Bearer ` 접두어 제외)");
    }
}
