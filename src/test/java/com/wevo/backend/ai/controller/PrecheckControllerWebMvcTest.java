package com.wevo.backend.ai.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.dto.response.PrecheckResponse;
import com.wevo.backend.ai.dto.response.PrecheckResponse.CurrentResultResponse;
import com.wevo.backend.ai.dto.response.PrecheckResponse.LatestJobResponse;
import com.wevo.backend.ai.dto.response.PrecheckResponse.RewriteResponse;
import com.wevo.backend.ai.service.PrecheckQueryService;
import com.wevo.backend.ai.service.PrecheckRequestService;
import com.wevo.backend.ai.service.PrecheckRewriteApplyResult;
import com.wevo.backend.ai.service.PrecheckRewriteService;
import com.wevo.backend.global.config.CorsProperties;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.global.security.RestAccessDeniedHandler;
import com.wevo.backend.global.security.RestAuthenticationEntryPoint;
import com.wevo.backend.global.security.SecurityConfig;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PrecheckController.class)
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@EnableConfigurationProperties(CorsProperties.class)
class PrecheckControllerWebMvcTest {

    private static final String URL = "/api/project-sections/10/precheck";
    private static final String APPLY_URL = URL + "/apply";
    private static final UUID REQUEST_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PrecheckRequestService requestService;
    @MockitoBean private PrecheckQueryService queryService;
    @MockitoBean private PrecheckRewriteService rewriteService;
    @MockitoBean private JwtProvider jwtProvider;

    @Test
    @DisplayName("인증 없이 사전 검토를 요청하면 401 A001이다")
    void unauthenticated_returnsA001() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("프로젝트 멤버의 사전 검토 요청은 202와 requestId를 반환한다")
    void request_returnsAcceptedRequestId() throws Exception {
        given(requestService.requestPrecheck(10L, 7L)).willReturn(REQUEST_ID);

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("PRECHECK_REQUESTED"))
                .andExpect(jsonPath("$.data.requestId").value(REQUEST_ID.toString()));
    }

    @Test
    @DisplayName("초안이 없으면 404 S003이다")
    void requestWithoutDraft_returnsS003() throws Exception {
        given(requestService.requestPrecheck(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S003"));
    }

    @Test
    @DisplayName("없는 섹션과 비멤버는 404 S001로 존재를 숨긴다")
    void hiddenSection_returnsS001() throws Exception {
        given(requestService.requestPrecheck(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("조회는 최신 실행과 마지막 성공 결과를 분리해 반환한다")
    void get_returnsLatestJobAndCurrentResult() throws Exception {
        given(queryService.getPrecheck(10L, 7L)).willReturn(PrecheckResponse.of(
                AiCheckStatus.CURRENT,
                new LatestJobResponse(REQUEST_ID, AiRequestStatus.SUCCEEDED, null),
                new CurrentResultResponse(
                        REQUEST_ID,
                        3,
                        List.of(),
                        new RewriteResponse("개선 본문", 1),
                        false,
                        null
                )
        ));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.aiCheckStatus").value("CURRENT"))
                .andExpect(jsonPath("$.data.latestJob.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.currentResult.resultId")
                        .value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.data.currentResult.rewrite.content")
                        .value("개선 본문"))
                .andExpect(jsonPath("$.data.currentResult.appliedContentVersion")
                        .doesNotExist());
    }

    @Test
    @DisplayName("실행 전 조회는 200과 exists=false만 반환한다")
    void getBeforeExecution_returnsExistsFalse() throws Exception {
        given(queryService.getPrecheck(10L, 7L))
                .willReturn(PrecheckResponse.notExecuted());

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.aiCheckStatus").doesNotExist())
                .andExpect(jsonPath("$.data.latestJob").doesNotExist())
                .andExpect(jsonPath("$.data.currentResult").doesNotExist());
    }

    @Test
    @DisplayName("유효한 수정안 적용은 200과 새 본문 버전을 반환한다")
    void apply_returnsNewContentVersion() throws Exception {
        given(rewriteService.apply(10L, 7L, REQUEST_ID, 3))
                .willReturn(new PrecheckRewriteApplyResult(
                        4, ProjectSectionStatus.DRAFTING, List.of()));

        mockMvc.perform(post(APPLY_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "%s",
                                  "checkedContentVersion": 3
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRECHECK_REWRITE_APPLIED"))
                .andExpect(jsonPath("$.data.contentVersion").value(4))
                .andExpect(jsonPath("$.data.sectionStatus").value("DRAFTING"))
                .andExpect(jsonPath("$.data.driftedSections").isEmpty());
    }

    @Test
    @DisplayName("적용 요청 값 누락과 잘못된 형식은 400 C001이다")
    void applyInvalidBody_returnsC001() throws Exception {
        mockMvc.perform(post(APPLY_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "not-a-uuid",
                                  "checkedContentVersion": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        mockMvc.perform(post(APPLY_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("lease·version·snapshot 충돌은 합의된 409 code로 반환한다")
    void applyConflicts_returnMappedCodes() throws Exception {
        given(rewriteService.apply(eq(10L), eq(7L), eq(REQUEST_ID), eq(3)))
                .willThrow(new BusinessException(ErrorCode.DRAFT_LEASE_NOT_HELD));

        mockMvc.perform(post(APPLY_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "%s",
                                  "checkedContentVersion": 3
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
