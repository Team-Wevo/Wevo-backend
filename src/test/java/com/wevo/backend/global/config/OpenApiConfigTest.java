package com.wevo.backend.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 생성된 OpenAPI 문서가 Bearer 인증 스키마를 담고 있는지 확인한다.
 *
 * <p>스키마가 빠지면 Swagger UI 에 {@code Authorize} 버튼이 생기지 않아 보호 API 를 브라우저에서
 * 시험할 수 없다. 설정 클래스가 조용히 지워지거나 빈 등록이 깨져도 컴파일·기존 테스트는 통과하므로,
 * 문서 산출물 자체를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest {

    private static final String API_DOCS = "/v3/api-docs";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("문서 제목이 Springdoc 기본값이 아니라 Wevo API 다")
    void documentHasWevoTitle() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Wevo API"));
    }

    @Test
    @DisplayName("Bearer JWT 보안 스키마가 정의되고 문서 전역 요구사항으로 걸린다")
    void documentDefinesBearerSchemeGlobally() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                // 별도 표기가 없는 모든 API 는 로그인 사용자만 호출 가능하다. (CLAUDE.md §5.2)
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }

    @Test
    @DisplayName("인증이 필요한 API 는 전역 요구사항을 그대로 상속한다")
    void protectedOperationsInheritGlobalRequirement() throws Exception {
        // 자체 security 를 선언하지 않으면 문서 전역 요구사항이 적용된다.
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users/me'].get.security").doesNotExist());
    }

    @Test
    @DisplayName("비인증 경로는 빈 security 로 전역 요구사항을 해제한다")
    void publicOperationsClearGlobalRequirement() throws Exception {
        // SecurityConfig.PUBLIC_ENDPOINTS 와 짝이 맞아야 한다 — 문서만 자물쇠가 붙으면
        // 프론트가 토큰 없이 호출해도 되는 API 를 인증 필요로 오해한다.
        // (dev-login 은 local 프로파일 전용이라 이 문서에 나타나지 않는다)
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/reissue'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/public/review-links/{token}'].get.security").isEmpty())
                .andExpect(jsonPath(
                        "$.paths['/public/review-links/{token}/submissions'].post.security").isEmpty());
    }

    @Test
    @DisplayName("로그아웃은 비인증 경로가 아니므로 자물쇠가 유지된다")
    void logoutStaysProtected() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security").doesNotExist());
    }

    @Test
    @DisplayName("JSON 응답 Content-Type 이 application/json 으로 문서화된다")
    void jsonResponsesDeclareApplicationJson() throws Exception {
        // 지정하지 않으면 모든 응답이 `*/*` 로 나가 문서만 봐서는 JSON 인지 알 수 없다.
        // 설정이 src/test/resources/application.yml 에도 있어야 테스트가 운영과 같은 문서를 본다.
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/projects'].get.responses['200'].content"
                                + "['application/json']").exists());
    }

    @Test
    @DisplayName("파일 응답은 실제 타입으로 문서화된다 — JSON 기본값에 묻히지 않는다")
    void fileResponsesDeclareTheirOwnMediaType() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/projects/{projectId}/final-output/download/plain-text']"
                                + ".get.responses['200'].content['text/plain']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/projects/{projectId}/final-output/download/markdown']"
                                + ".get.responses['200'].content['text/markdown']").exists());
    }

    @Test
    @DisplayName("공통 실패 응답 예제가 components.examples 에 등록된다")
    void commonFailureExamplesAreRegistered() throws Exception {
        // 이름은 ApiExampleRefs 의 상수와 짝이 맞아야 한다 — 어긋나면 참조가 끊기는데,
        // 애노테이션 값이라 컴파일은 통과하고 Swagger 화면에서 예제만 조용히 비어 보인다.
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.examples.invalidInput.value").exists())
                .andExpect(jsonPath("$.components.examples.unauthorized.value").exists())
                .andExpect(jsonPath("$.components.examples.forbidden.value").exists())
                .andExpect(jsonPath("$.components.examples.projectNotFound.value").exists())
                .andExpect(jsonPath("$.components.examples.sectionNotFound.value").exists())
                .andExpect(jsonPath("$.components.examples.conflict.value").exists());
    }

    @Test
    @DisplayName("실패 응답이 공통 예제를 참조한다 — ref 만 주고 name 을 빠뜨리면 실리지 않는다")
    void failureResponsesReferenceCommonExamples() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/projects'].post.responses['400'].content"
                                + "['application/json'].examples.C001").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/projects'].post.responses['401'].content"
                                + "['application/json'].examples.A001").exists());
    }

    @Test
    @DisplayName("성공 응답 예제를 붙여도 스키마 참조가 사라지지 않는다")
    void successExampleKeepsSchemaReference() throws Exception {
        // 응답에 @Content(examples=...) 를 직접 붙이면 자동 생성된 schema.$ref 가 통째로 덮인다.
        // 그래서 성공 예제는 응답 DTO 타입의 @Schema(example=...) 로 준다 — 스키마와 required 가
        // 그대로 남고 예제만 얹힌다.
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/projects'].post.responses['201'].content"
                                + "['application/json'].schema").exists())
                .andExpect(jsonPath("$.components.schemas.ProjectCreateResponse.example").exists())
                .andExpect(jsonPath("$.components.schemas.ProjectCreateResponse.required").exists());
    }
}
