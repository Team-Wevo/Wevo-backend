package com.wevo.backend.issue.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.config.CorsProperties;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.global.security.RestAccessDeniedHandler;
import com.wevo.backend.global.security.RestAuthenticationEntryPoint;
import com.wevo.backend.global.security.SecurityConfig;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.dto.request.EvidenceRequestCreateRequest;
import com.wevo.backend.issue.dto.request.IssueDecisionRequest;
import com.wevo.backend.issue.dto.response.EvidenceRequestResponse;
import com.wevo.backend.issue.dto.response.IssueDecisionResponse;
import com.wevo.backend.issue.service.EvidenceRequestService;
import com.wevo.backend.issue.service.IssueDecisionService;
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

@WebMvcTest(IssueController.class)
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
class IssueControllerWebMvcTest {

    private static final String URL = "/api/issues/10/decision";
    private static final String EVIDENCE_REQUEST_URL = "/api/issues/10/evidence-request";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueDecisionService issueDecisionService;

    @MockitoBean
    private EvidenceRequestService evidenceRequestService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("인증 없이 호출하면 401 A001을 반환한다")
    void withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType("application/json")
                        .content("{\"customInput\":\"직접 결정\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("선택지 결정은 200 ISSUE_DECIDED와 RESOLVED 상태를 반환한다")
    void selectedOption_returnsSuccess() throws Exception {
        given(issueDecisionService.decide(eq(10L), eq(7L), any(IssueDecisionRequest.class)))
                .willReturn(new IssueDecisionResponse(10L, IssueStatus.RESOLVED));

        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"selectedOption\":\"대학생 팀\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("ISSUE_DECIDED"))
                .andExpect(jsonPath("$.message").value("쟁점이 결정되었습니다."))
                .andExpect(jsonPath("$.data.issueId").value(10))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.timestamp").exists());

        then(issueDecisionService).should()
                .decide(eq(10L), eq(7L), any(IssueDecisionRequest.class));
    }

    @Test
    @DisplayName("직접 입력 결정도 200 ISSUE_DECIDED를 반환한다")
    void customInput_returnsSuccess() throws Exception {
        given(issueDecisionService.decide(eq(10L), eq(7L), any(IssueDecisionRequest.class)))
                .willReturn(IssueDecisionResponse.resolved(10L));

        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"customInput\":\"대학생 팀을 우선한다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ISSUE_DECIDED"));
    }

    @Test
    @DisplayName("선택지와 직접 입력을 모두 지정하면 400 C001이다")
    void bothChoices_returnsC001() throws Exception {
        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("""
                                {
                                  "selectedOption": "대학생 팀",
                                  "customInput": "직접 결정"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field")
                        .value("exactlyOneChoiceProvided"));
    }

    @Test
    @DisplayName("두 입력을 모두 생략하면 400 C001이다")
    void noChoice_returnsC001() throws Exception {
        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("공백 직접 입력은 선택하지 않은 것으로 보아 400 C001이다")
    void blankCustomInput_returnsC001() throws Exception {
        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"customInput\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("직접 입력이 200자를 초과하면 400 C001이다")
    void tooLongCustomInput_returnsC001() throws Exception {
        String tooLong = "가".repeat(201);

        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"customInput\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("customInput"));
    }

    @Test
    @DisplayName("선택지 문자열이 200자를 초과하면 400 C001이다")
    void tooLongSelectedOption_returnsC001() throws Exception {
        String tooLong = "가".repeat(201);

        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"selectedOption\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("selectedOption"));
    }

    @Test
    @DisplayName("멤버지만 OWNER가 아니면 403 A002이다")
    void member_returnsA002() throws Exception {
        given(issueDecisionService.decide(eq(10L), eq(7L), any(IssueDecisionRequest.class)))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"customInput\":\"직접 결정\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("쟁점 없음 또는 비멤버이면 404 I001이다")
    void hiddenIssue_returnsI001() throws Exception {
        given(issueDecisionService.decide(eq(10L), eq(7L), any(IssueDecisionRequest.class)))
                .willThrow(new BusinessException(ErrorCode.ISSUE_NOT_FOUND));

        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"customInput\":\"직접 결정\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("I001"));
    }

    @Test
    @DisplayName("GAP·이미 해소·이전 세트 충돌은 409 C003이다")
    void conflict_returnsC003() throws Exception {
        given(issueDecisionService.decide(eq(10L), eq(7L), any(IssueDecisionRequest.class)))
                .willThrow(new BusinessException(ErrorCode.CONFLICT));

        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"customInput\":\"직접 결정\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    @DisplayName("예상하지 못한 내부 오류는 500 C999로 반환한다")
    void unexpectedError_returnsC999() throws Exception {
        given(issueDecisionService.decide(eq(10L), eq(7L), any(IssueDecisionRequest.class)))
                .willThrow(new IllegalStateException("internal detail"));

        mockMvc.perform(post(URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"customInput\":\"직접 결정\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("C999"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    @Test
    @DisplayName("인증 없이 추가 근거를 요청하면 401 A001이다")
    void requestEvidence_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(post(EVIDENCE_REQUEST_URL)
                        .contentType("application/json")
                        .content("{\"targetUserId\":20}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("GAP 추가 근거 요청은 대상 사용자 스냅샷을 반환한다")
    void requestEvidence_validRequest_returnsSuccess() throws Exception {
        given(evidenceRequestService.request(
                eq(10L), eq(7L), any(EvidenceRequestCreateRequest.class)))
                .willReturn(EvidenceRequestResponse.requested(10L, 20L, "팀원"));

        mockMvc.perform(post(EVIDENCE_REQUEST_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"targetUserId\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("EVIDENCE_REQUESTED"))
                .andExpect(jsonPath("$.message").value("추가 근거를 요청했습니다."))
                .andExpect(jsonPath("$.data.issueId").value(10))
                .andExpect(jsonPath("$.data.requestedTo.userId").value(20))
                .andExpect(jsonPath("$.data.requestedTo.name").value("팀원"))
                .andExpect(jsonPath("$.timestamp").exists());

        then(evidenceRequestService).should().request(
                eq(10L), eq(7L), any(EvidenceRequestCreateRequest.class));
    }

    @Test
    @DisplayName("targetUserId가 없으면 400 C001이다")
    void requestEvidence_missingTarget_returnsC001() throws Exception {
        mockMvc.perform(post(EVIDENCE_REQUEST_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("targetUserId"));
    }

    @Test
    @DisplayName("추가 근거 요청 권한이 없으면 403 A002이다")
    void requestEvidence_member_returnsA002() throws Exception {
        given(evidenceRequestService.request(
                eq(10L), eq(7L), any(EvidenceRequestCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post(EVIDENCE_REQUEST_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"targetUserId\":20}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("쟁점 없음 또는 비멤버의 추가 근거 요청은 404 I001이다")
    void requestEvidence_hiddenIssue_returnsI001() throws Exception {
        given(evidenceRequestService.request(
                eq(10L), eq(7L), any(EvidenceRequestCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.ISSUE_NOT_FOUND));

        mockMvc.perform(post(EVIDENCE_REQUEST_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"targetUserId\":20}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("I001"));
    }

    @Test
    @DisplayName("관련 의견 작성자가 아닌 대상은 400 C001이다")
    void requestEvidence_unrelatedTarget_returnsC001() throws Exception {
        given(evidenceRequestService.request(
                eq(10L), eq(7L), any(EvidenceRequestCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(post(EVIDENCE_REQUEST_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"targetUserId\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("이미 추가 근거를 요청한 쟁점은 409 I003이다")
    void requestEvidence_alreadyRequested_returnsI003() throws Exception {
        given(evidenceRequestService.request(
                eq(10L), eq(7L), any(EvidenceRequestCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.EVIDENCE_REQUEST_ALREADY_SENT));

        mockMvc.perform(post(EVIDENCE_REQUEST_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"targetUserId\":20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("I003"));
    }

    @Test
    @DisplayName("CONFLICT 또는 이전 세트에 추가 근거를 요청하면 409 C003이다")
    void requestEvidence_conflict_returnsC003() throws Exception {
        given(evidenceRequestService.request(
                eq(10L), eq(7L), any(EvidenceRequestCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.CONFLICT));

        mockMvc.perform(post(EVIDENCE_REQUEST_URL)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"targetUserId\":20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
