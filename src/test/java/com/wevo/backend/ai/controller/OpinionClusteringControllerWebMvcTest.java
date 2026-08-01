package com.wevo.backend.ai.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse.ClusterResponse;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse.CurrentSetResponse;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse.LatestJobResponse;
import com.wevo.backend.ai.service.OpinionClusteringQueryService;
import com.wevo.backend.ai.service.OpinionClusteringRequestService;
import com.wevo.backend.global.config.CorsProperties;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(OpinionClusteringController.class)
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
class OpinionClusteringControllerWebMvcTest {

    private static final String URL = "/api/project-sections/10/opinion-clusters";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OpinionClusteringRequestService requestService;
    @MockitoBean private OpinionClusteringQueryService queryService;
    @MockitoBean private JwtProvider jwtProvider;

    @Test
    void requestReturnsAcceptedJobId() throws Exception {
        UUID requestId = UUID.randomUUID();
        given(requestService.requestClustering(10L, 7L)).willReturn(requestId);

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("OPINION_CLUSTERING_REQUESTED"))
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()));
    }

    @Test
    void queryReturnsPartitionMetadataAndIds() throws Exception {
        UUID requestId = UUID.randomUUID();
        given(queryService.getClustering(10L, 7L)).willReturn(OpinionClusteringResponse.of(
                true,
                false,
                new LatestJobResponse(requestId, AiRequestStatus.SUCCEEDED, null),
                new CurrentSetResponse(
                        requestId, 2, 3, 3,
                        LocalDateTime.of(2026, 8, 1, 10, 0),
                        List.of(new ClusterResponse(
                                1, "공통 문제", "두 의견이 같은 문제를 설명합니다.",
                                List.of(11L, 12L)),
                                new ClusterResponse(
                                        2, "소수 의견", "독립적인 관점입니다.", List.of(13L)))
                )));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.canViewResult").value(true))
                .andExpect(jsonPath("$.data.stale").value(false))
                .andExpect(jsonPath("$.data.currentSet.totalOpinionCount").value(3))
                .andExpect(jsonPath("$.data.currentSet.coveredCount").value(3))
                .andExpect(jsonPath("$.data.currentSet.clusters[1].opinionIds[0]").value(13));
    }

    @Test
    void queryHidesClusterResultBeforeOwnSubmission() throws Exception {
        UUID requestId = UUID.randomUUID();
        given(queryService.getClustering(10L, 7L)).willReturn(OpinionClusteringResponse.of(
                false,
                false,
                new LatestJobResponse(requestId, AiRequestStatus.SUCCEEDED, null),
                null));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canViewResult").value(false))
                .andExpect(jsonPath("$.data.currentSet").doesNotExist());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void memberAndNonMemberErrorsFollowRoleAndExistenceHidingRules() throws Exception {
        given(requestService.requestClustering(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));
        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));

        given(queryService.getClustering(10L, 7L))
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
