package com.wevo.backend.ai.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.ProjectFlowFindingType;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.CheckedSectionResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.FindingResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.LatestJobResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.ResultResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.SectionExcerptResponse;
import com.wevo.backend.ai.service.ProjectFlowReviewQueryService;
import com.wevo.backend.ai.service.ProjectFlowReviewRequestService;
import com.wevo.backend.global.config.CorsProperties;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.global.security.RestAccessDeniedHandler;
import com.wevo.backend.global.security.RestAuthenticationEntryPoint;
import com.wevo.backend.global.security.SecurityConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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

@WebMvcTest(ProjectFlowReviewController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
class ProjectFlowReviewControllerWebMvcTest {
    private static final String URL = "/api/projects/1/flow-check";
    @Autowired MockMvc mockMvc;
    @MockitoBean ProjectFlowReviewRequestService requestService;
    @MockitoBean ProjectFlowReviewQueryService queryService;
    @MockitoBean JwtProvider jwtProvider;

    @Test
    void ownerRequestReturnsAcceptedAndMemberCanReadStoredVersions() throws Exception {
        UUID id = UUID.randomUUID();
        given(requestService.requestReview(1L, 7L)).willReturn(id);
        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("PROJECT_FLOW_REVIEW_REQUESTED"))
                .andExpect(jsonPath("$.data.requestId").value(id.toString()));

        given(queryService.getReview(1L, 7L)).willReturn(new ProjectFlowReviewResponse(
                true, true, new LatestJobResponse(id, AiRequestStatus.SUCCEEDED, null),
                new ResultResponse(id, false, LocalDateTime.of(2026, 8, 1, 12, 0), 2, 1,
                        List.of(new CheckedSectionResponse(10L, "problem", "문제", 3)),
                        List.of(new FindingResponse(1, ProjectFlowFindingType.CLAIM_OR_NUMBER_CONTRADICTION,
                                List.of(new SectionExcerptResponse(10L, 3, "100만원"),
                                        new SectionExcerptResponse(20L, 2, "200만원")),
                                "예산이 다릅니다.", "하나로 맞추세요.")))));
        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outdated").value(true))
                .andExpect(jsonPath("$.data.lastSuccessfulResult.current").value(false))
                .andExpect(jsonPath("$.data.lastSuccessfulResult.checkedSections[0].confirmedVersion").value(3))
                .andExpect(jsonPath("$.data.lastSuccessfulResult.findings[0].sections[1].targetExcerpt").value("200만원"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
