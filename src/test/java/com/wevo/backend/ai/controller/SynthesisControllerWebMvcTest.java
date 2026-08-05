package com.wevo.backend.ai.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.dto.response.SynthesisResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.CurrentSetResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.DecisionResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.IssueResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.LatestJobResponse;
import com.wevo.backend.ai.dto.response.SynthesisResponse.RelatedOpinionResponse;
import com.wevo.backend.ai.service.SynthesisQueryService;
import com.wevo.backend.ai.service.SynthesisRequestService;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import java.time.LocalDateTime;
import java.util.List;
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

    @MockitoBean
    private SynthesisQueryService synthesisQueryService;

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

    @Test
    @DisplayName("AI Provider가 비활성 상태면 503 AI008을 반환한다")
    void providerUnavailable_returns503() throws Exception {
        given(synthesisRequestService.requestSynthesis(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI008"));
    }

    @Test
    @DisplayName("인증 없이 조회하면 A001 공통 응답을 반환한다")
    void getWithoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("조회는 200 OK로 최신 실행과 현재 세트를 분리해 반환한다")
    void get_returns200WithLatestJobAndCurrentSet() throws Exception {
        UUID requestId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        given(synthesisQueryService.getSynthesis(10L, 7L)).willReturn(SynthesisResponse.of(
                false,
                new LatestJobResponse(requestId, AiRequestStatus.SUCCEEDED, null),
                new CurrentSetResponse(
                        requestId,
                        "협업 도구 부족이 공통 문제다.",
                        List.of(new IssueResponse(
                                101L,
                                IssueType.CONFLICT,
                                IssueStatus.RESOLVED,
                                "우선 사용자층이 갈립니다.",
                                List.of(new RelatedOpinionResponse(
                                        11L, 21L, "팀원A", "대학생이 주 사용자입니다.")),
                                "어느 사용자층을 우선할까요?",
                                List.of("대학생", "직장인"),
                                new DecisionResponse("대학생", null,
                                        LocalDateTime.of(2026, 7, 25, 15, 0)),
                                null,
                                null)),
                        List.of())));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.synthesisStale").value(false))
                .andExpect(jsonPath("$.data.latestJob.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.data.latestJob.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.latestJob.failure").doesNotExist())
                .andExpect(jsonPath("$.data.currentSet.setId").value(requestId.toString()))
                .andExpect(jsonPath("$.data.currentSet.issues[0].type").value("CONFLICT"))
                .andExpect(jsonPath(
                        "$.data.currentSet.issues[0].relatedOpinions[0].authorUserId")
                        .value(21))
                .andExpect(jsonPath("$.data.currentSet.issues[0].options[0]").value("대학생"))
                .andExpect(jsonPath("$.data.currentSet.issues[0].decision.selectedOption").value("대학생"))
                // 유형에 해당하지 않는 필드와 null은 직렬화에서 생략된다 (§1.4)
                .andExpect(jsonPath("$.data.currentSet.issues[0].decision.customInput").doesNotExist())
                .andExpect(jsonPath("$.data.currentSet.issues[0].evidenceRequested").doesNotExist())
                .andExpect(jsonPath("$.data.currentSet.issues[0].answer").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("정리 실행 전이면 200 OK + exists=false만 반환한다 (오류 아님)")
    void get_notExecuted_returns200WithExistsFalse() throws Exception {
        given(synthesisQueryService.getSynthesis(10L, 7L)).willReturn(SynthesisResponse.notExecuted());

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.synthesisStale").doesNotExist())
                .andExpect(jsonPath("$.data.latestJob").doesNotExist())
                .andExpect(jsonPath("$.data.currentSet").doesNotExist());
    }

    @Test
    @DisplayName("조회에서 비멤버·없는 섹션이면 404 S001을 반환한다 (존재 숨김)")
    void get_nonMember_returns404() throws Exception {
        given(synthesisQueryService.getSynthesis(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
