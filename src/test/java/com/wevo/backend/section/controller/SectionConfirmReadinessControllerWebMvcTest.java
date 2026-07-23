package com.wevo.backend.section.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.domain.ConfirmReadinessCheck;
import com.wevo.backend.section.dto.response.SectionConfirmReadinessResponse;
import com.wevo.backend.section.dto.response.SectionConfirmReadinessResponse.CheckResponse;
import java.util.List;
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
class SectionConfirmReadinessControllerWebMvcTest {

    private static final String URL = "/api/project-sections/10/confirm-readiness";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.wevo.backend.section.service.SectionConfirmReadinessService sectionConfirmReadinessService;

    @Test
    @DisplayName("인증 없이 조회하면 A001을 반환한다")
    void getReadiness_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("충족 항목은 reason을 생략하고, 미충족 항목만 사유를 싣는다")
    void getReadiness_omitsReasonForSatisfiedChecks() throws Exception {
        SectionConfirmReadinessResponse response = SectionConfirmReadinessResponse.of(
                true,
                List.of(
                        CheckResponse.of(ConfirmReadinessCheck.SECTION_REVIEWING, true),
                        CheckResponse.of(ConfirmReadinessCheck.AI_CHECK_CURRENT, false)
                ));
        given(sectionConfirmReadinessService.getReadiness(10L, 7L)).willReturn(response);

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.canConfirm").value(false))
                .andExpect(jsonPath("$.data.checks[0].key").value("SECTION_REVIEWING"))
                .andExpect(jsonPath("$.data.checks[0].satisfied").value(true))
                .andExpect(jsonPath("$.data.checks[0].reason").doesNotExist())
                .andExpect(jsonPath("$.data.checks[1].key").value("AI_CHECK_CURRENT"))
                .andExpect(jsonPath("$.data.checks[1].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[1].reason")
                        .value("현재 초안에 대한 AI 사전 검토가 필요합니다."));
    }

    @Test
    @DisplayName("모든 조건 충족 + OWNER면 ready·canConfirm 모두 true")
    void getReadiness_allSatisfiedOwner_canConfirm() throws Exception {
        SectionConfirmReadinessResponse response = SectionConfirmReadinessResponse.of(
                true,
                List.of(
                        CheckResponse.of(ConfirmReadinessCheck.SECTION_REVIEWING, true),
                        CheckResponse.of(ConfirmReadinessCheck.AI_CHECK_CURRENT, true),
                        CheckResponse.of(ConfirmReadinessCheck.MEMBER_APPROVED, true),
                        CheckResponse.of(ConfirmReadinessCheck.NO_UNRESOLVED_REQUEST, true),
                        CheckResponse.of(ConfirmReadinessCheck.NO_ACTIVE_EDITOR, true)
                ));
        given(sectionConfirmReadinessService.getReadiness(10L, 7L)).willReturn(response);

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.canConfirm").value(true));
    }

    @Test
    @DisplayName("섹션이 없거나 비멤버면 404 S001 (존재 숨김)")
    void getReadiness_hiddenOrMissing_returnsS001() throws Exception {
        given(sectionConfirmReadinessService.getReadiness(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("S001"));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
