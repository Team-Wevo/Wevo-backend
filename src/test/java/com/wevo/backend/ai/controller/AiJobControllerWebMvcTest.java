package com.wevo.backend.ai.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.domain.AiRequestFeature;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.dto.response.AiJobResponse;
import com.wevo.backend.ai.dto.response.AiJobResponse.FailureResponse;
import com.wevo.backend.ai.service.AiJobQueryService;
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

@WebMvcTest(AiJobController.class)
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
class AiJobControllerWebMvcTest {

    private static final UUID REQUEST_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String URL = "/api/ai-jobs/" + REQUEST_ID;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AiJobQueryService queryService;
    @MockitoBean private JwtProvider jwtProvider;

    @Test
    @DisplayName("인증 없이 job을 조회하면 401 A001이다")
    void unauthenticated_returnsA001() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("프로젝트 멤버는 결과 본문·내부 상태 없이 공통 job 상태를 조회한다")
    void member_returnsExternalStatusOnly() throws Exception {
        given(queryService.getJob(REQUEST_ID, 7L)).willReturn(new AiJobResponse(
                REQUEST_ID,
                AiRequestFeature.PRECHECK,
                AiRequestStatus.FAILED,
                new FailureResponse("AI018", "AI 응답이 출력 형식을 충족하지 않습니다.")
        ));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.data.feature").value("PRECHECK"))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failure.errorCode").value("AI018"))
                .andExpect(jsonPath("$.data.resultId").doesNotExist())
                .andExpect(jsonPath("$.data.inputSnapshotHash").doesNotExist());
    }

    @Test
    @DisplayName("없는 job과 비멤버는 모두 404 AI021이다")
    void hiddenJob_returnsAi021() throws Exception {
        given(queryService.getJob(REQUEST_ID, 7L))
                .willThrow(new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI021"));
    }

    @Test
    @DisplayName("잘못된 requestId 형식은 400 C001이다")
    void invalidRequestId_returnsC001() throws Exception {
        mockMvc.perform(get("/api/ai-jobs/not-a-uuid").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("requestId"));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
