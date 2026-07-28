package com.wevo.backend.ai.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.service.DraftGenerationRequestService;
import com.wevo.backend.global.config.CorsProperties;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.global.security.RestAccessDeniedHandler;
import com.wevo.backend.global.security.RestAuthenticationEntryPoint;
import com.wevo.backend.global.security.SecurityConfig;
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

@WebMvcTest(DraftGenerationController.class)
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
class DraftGenerationControllerWebMvcTest {

    private static final String URL = "/api/project-sections/10/draft/generate";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DraftGenerationRequestService requestService;
    @MockitoBean private JwtProvider jwtProvider;

    @Test
    @DisplayName("인증 없이 초안 생성을 요청하면 401 A001이다")
    void unauthenticated_returnsA001() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("OWNER 요청은 Provider를 기다리지 않고 202와 requestId를 반환한다")
    void owner_returnsAcceptedRequestId() throws Exception {
        UUID requestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        given(requestService.requestDraftGeneration(10L, 7L)).willReturn(requestId);

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("DRAFT_GENERATION_REQUESTED"))
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("멤버지만 OWNER가 아니면 403 A002이다")
    void member_returnsA002() throws Exception {
        given(requestService.requestDraftGeneration(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("없는 섹션과 비멤버는 404 S001로 존재를 숨긴다")
    void hiddenSection_returnsS001() throws Exception {
        given(requestService.requestDraftGeneration(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("잘못된 섹션 상태는 409 S002이다")
    void invalidSectionStatus_returnsS002() throws Exception {
        given(requestService.requestDraftGeneration(10L, 7L))
                .willThrow(new BusinessException(
                        ErrorCode.INVALID_SECTION_STATUS_TRANSITION));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S002"));
    }

    @Test
    @DisplayName("미결정 충돌 쟁점은 409 I002이다")
    void unresolvedConflict_returnsI002() throws Exception {
        given(requestService.requestDraftGeneration(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.ISSUE_CONFLICT_UNDECIDED));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("I002"));
    }

    @Test
    @DisplayName("stale synthesis는 409 C003이다")
    void staleSynthesis_returnsC003() throws Exception {
        given(requestService.requestDraftGeneration(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.CONFLICT));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
