package com.wevo.backend.issue.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.dto.request.IssueDecisionRequest;
import com.wevo.backend.issue.dto.response.IssueDecisionResponse;
import com.wevo.backend.issue.service.IssueDecisionService;
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
class IssueControllerWebMvcTest {

    private static final String URL = "/api/issues/10/decision";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueDecisionService issueDecisionService;

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

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
