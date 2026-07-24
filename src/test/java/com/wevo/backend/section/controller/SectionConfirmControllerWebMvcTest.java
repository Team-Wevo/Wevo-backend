package com.wevo.backend.section.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.domain.ConfirmReadinessCheck;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.dto.response.SectionConfirmResponse;
import com.wevo.backend.section.service.SectionConfirmService;
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
class SectionConfirmControllerWebMvcTest {

    private static final String URL = "/api/project-sections/10/confirm";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SectionConfirmService sectionConfirmService;

    @Test
    @DisplayName("인증 없이 확정하면 A001을 반환한다")
    void confirm_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("확정 성공 시 200과 확정 상태·버전을 반환한다")
    void confirm_success_returnsConfirmed() throws Exception {
        given(sectionConfirmService.confirm(10L, 7L)).willReturn(
                new SectionConfirmResponse(10L, ProjectSectionStatus.CONFIRMED, 3));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SECTION_CONFIRMED"))
                .andExpect(jsonPath("$.message").value("섹션이 확정되었습니다."))
                .andExpect(jsonPath("$.data.sectionId").value(10))
                .andExpect(jsonPath("$.data.sectionStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedVersion").value(3));
    }

    @Test
    @DisplayName("OWNER가 아니면 403 A002를 반환한다")
    void confirm_notOwner_returnsA002() throws Exception {
        given(sectionConfirmService.confirm(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("섹션이 REVIEWING이 아니면 409 S002를 반환한다")
    void confirm_notReviewing_returnsS002() throws Exception {
        given(sectionConfirmService.confirm(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S002"));
    }

    @Test
    @DisplayName("§6.3 조건 미충족이면 409 C003과 조건별 사유를 errors에 담는다")
    void confirm_conditionsUnmet_returnsC003WithErrors() throws Exception {
        given(sectionConfirmService.confirm(10L, 7L)).willThrow(new BusinessException(
                ErrorCode.CONFLICT,
                List.of(new FieldError(
                        ConfirmReadinessCheck.MEMBER_APPROVED.name(),
                        ConfirmReadinessCheck.MEMBER_APPROVED.unsatisfiedReason()))));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.errors[0].field").value("MEMBER_APPROVED"))
                .andExpect(jsonPath("$.errors[0].reason").value("팀원 1명 이상의 동의가 필요합니다."));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
