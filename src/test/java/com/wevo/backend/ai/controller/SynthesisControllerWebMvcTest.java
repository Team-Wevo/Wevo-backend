package com.wevo.backend.ai.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.service.SynthesisRequestService;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class SynthesisControllerWebMvcTest {

    private static final String URL = "/api/project-sections/10/synthesis";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SynthesisRequestService synthesisRequestService;

    @Test
    @DisplayName("인증 없이 호출하면 A001 공통 응답을 반환한다")
    void withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("OWNER의 정리 실행은 202와 SYNTHESIS_REQUESTED, requestId를 반환한다")
    void owner_returns202WithRequestId() throws Exception {
        UUID requestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        given(synthesisRequestService.requestSynthesis(10L, 7L)).willReturn(requestId);

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SYNTHESIS_REQUESTED"))
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("멤버지만 OWNER가 아니면 403 A002를 반환한다")
    void member_returns403() throws Exception {
        given(synthesisRequestService.requestSynthesis(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("비멤버·없는 섹션이면 404 S001을 반환한다 (존재 숨김)")
    void nonMember_returns404() throws Exception {
        given(synthesisRequestService.requestSynthesis(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("SYNTHESIZING이 아니면 409 S002를 반환한다")
    void invalidStatus_returns409() throws Exception {
        given(synthesisRequestService.requestSynthesis(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S002"));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
