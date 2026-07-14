package com.wevo.backend.opinion.controller;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.MyOpinionResponse;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.dto.response.OpinionSubmitResponse;
import com.wevo.backend.opinion.service.OpinionService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpinionControllerWebMvcTest {

    private static final String URL = "/api/project-sections/10/my-opinion/draft";
    private static final String GET_URL = "/api/project-sections/10/my-opinion";
    private static final String SUBMIT_URL = "/api/project-sections/10/my-opinion/submit";
    private static final String VALID_CONTENT = "타겟을 공모전 참가 대학생 팀으로 좁히는 게 좋겠습니다.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpinionService opinionService;

    @Test
    @DisplayName("인증 없이 호출하면 A001 공통 응답을 반환한다")
    void saveDraft_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(patch(URL)
                        .contentType("application/json")
                        .content("{\"content\": \"" + VALID_CONTENT + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("content 가 20자 미만이면 C001 과 필드 오류를 반환한다")
    void saveDraft_tooShortContent_returnsC001() throws Exception {
        mockMvc.perform(patch(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"content\": \"너무 짧은 의견\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("content"));
    }

    @Test
    @DisplayName("content 가 1,000자를 초과하면 C001 과 필드 오류를 반환한다")
    void saveDraft_tooLongContent_returnsC001() throws Exception {
        String tooLong = "가".repeat(1001);
        mockMvc.perform(patch(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"content\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("content"));
    }

    @Test
    @DisplayName("유효한 요청이면 OPINION_DRAFT_SAVED 성공 응답을 반환한다")
    void saveDraft_validRequest_returnsSuccess() throws Exception {
        given(opinionService.saveDraft(eq(10L), eq(7L), any(OpinionDraftRequest.class)))
                .willReturn(new OpinionDraftResponse(
                        501L, VALID_CONTENT, OpinionStatus.DRAFT,
                        LocalDateTime.of(2026, 7, 14, 12, 0)));

        mockMvc.perform(patch(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"content\": \"" + VALID_CONTENT + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OPINION_DRAFT_SAVED"))
                .andExpect(jsonPath("$.message").value("의견이 임시저장되었습니다."))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.content").value(VALID_CONTENT))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("의견 조회는 인증 없이 호출하면 A001 공통 응답을 반환한다")
    void getMyOpinion_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(get(GET_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("작성한 의견이 있으면 exists=true 와 의견 내용을 담은 OK 응답을 반환한다")
    void getMyOpinion_present_returnsOpinion() throws Exception {
        given(opinionService.getMyOpinion(10L, 7L))
                .willReturn(new MyOpinionResponse(
                        true, 501L, VALID_CONTENT, OpinionStatus.DRAFT,
                        LocalDateTime.of(2026, 7, 14, 12, 0)));

        mockMvc.perform(get(GET_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.content").value(VALID_CONTENT))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    @DisplayName("의견 미작성이면 404가 아니라 exists=false 인 OK 응답을 반환한다")
    void getMyOpinion_absent_returnsExistsFalse() throws Exception {
        given(opinionService.getMyOpinion(10L, 7L)).willReturn(MyOpinionResponse.empty());

        mockMvc.perform(get(GET_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.id").hasJsonPath())
                .andExpect(jsonPath("$.data.id").value(nullValue()))
                .andExpect(jsonPath("$.data.content").hasJsonPath())
                .andExpect(jsonPath("$.data.content").value(nullValue()))
                .andExpect(jsonPath("$.data.status").hasJsonPath())
                .andExpect(jsonPath("$.data.status").value(nullValue()))
                .andExpect(jsonPath("$.data.updatedAt").hasJsonPath())
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));
    }

    @Test
    @DisplayName("의견 제출은 인증 없이 호출하면 A001 공통 응답을 반환한다")
    void submitMyOpinion_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(post(SUBMIT_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("임시저장된 의견을 제출하면 OPINION_SUBMITTED 성공 응답을 반환한다")
    void submitMyOpinion_returnsSuccess() throws Exception {
        given(opinionService.submitMyOpinion(10L, 7L))
                .willReturn(new OpinionSubmitResponse(
                        501L,
                        OpinionStatus.SUBMITTED,
                        LocalDateTime.of(2026, 7, 14, 12, 5)));

        mockMvc.perform(post(SUBMIT_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OPINION_SUBMITTED"))
                .andExpect(jsonPath("$.message").value("의견이 제출되었습니다."))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedAt").value("2026-07-14T12:05:00"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("임시저장된 의견이 없으면 O001과 projectSectionId 필드 오류를 반환한다")
    void submitMyOpinion_opinionNotFound_returnsO001() throws Exception {
        given(opinionService.submitMyOpinion(10L, 7L))
                .willThrow(new BusinessException(
                        ErrorCode.OPINION_NOT_FOUND,
                        List.of(new FieldError(
                                "projectSectionId",
                                "no draft opinion to submit"
                        ))
                ));

        mockMvc.perform(post(SUBMIT_URL).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("O001"))
                .andExpect(jsonPath("$.message").value("의견을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.errors[0].field").value("projectSectionId"))
                .andExpect(jsonPath("$.errors[0].reason").value("no draft opinion to submit"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
