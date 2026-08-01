package com.wevo.backend.ai.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.dto.response.AuthorIntentResponse;
import com.wevo.backend.ai.service.AuthorIntentConfirmationService;
import com.wevo.backend.ai.service.AuthorIntentQueryService;
import com.wevo.backend.ai.service.AuthorIntentRequestService;
import com.wevo.backend.global.config.CorsProperties;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.global.security.RestAccessDeniedHandler;
import com.wevo.backend.global.security.RestAuthenticationEntryPoint;
import com.wevo.backend.global.security.SecurityConfig;
import com.wevo.backend.section.domain.SectionAuthorIntentStatus;
import java.time.LocalDateTime;
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

@WebMvcTest(AuthorIntentController.class)
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
class AuthorIntentControllerWebMvcTest {

    private static final String BASE = "/api/project-sections/10/author-intent";
    private static final UUID REQUEST_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuthorIntentRequestService requestService;
    @MockitoBean private AuthorIntentQueryService queryService;
    @MockitoBean private AuthorIntentConfirmationService confirmationService;
    @MockitoBean private JwtProvider jwtProvider;

    @Test
    void extractionIsOwnerAuthenticatedAsyncRequest() throws Exception {
        given(requestService.requestExtraction(10L, 7L)).willReturn(REQUEST_ID);

        mockMvc.perform(post(BASE + "/extractions").with(user()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("AUTHOR_INTENT_EXTRACTION_REQUESTED"))
                .andExpect(jsonPath("$.data.requestId").value(REQUEST_ID.toString()));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void confirmationReturnsNormalizedHumanValue() throws Exception {
        AuthorIntentResponse response = new AuthorIntentResponse(
                3,
                SectionAuthorIntentStatus.CONFIRMED,
                "AI 제안",
                "사람이 수정해 확정한 의도다.",
                7L,
                LocalDateTime.of(2026, 7, 31, 20, 0),
                null);
        given(confirmationService.confirm(
                10L, 7L, 3, "사람이 수정해 확정한 의도다."))
                .willReturn(response);

        mockMvc.perform(put(BASE)
                        .with(user())
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentVersion": 3,
                                  "intent": "사람이 수정해 확정한 의도다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTHOR_INTENT_CONFIRMED"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedIntent")
                        .value("사람이 수정해 확정한 의도다."));
    }

    @Test
    void invalidConfirmationBodyReturnsC001() throws Exception {
        mockMvc.perform(put(BASE)
                        .with(user())
                        .contentType("application/json")
                        .content("{\"contentVersion\":0,\"intent\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    private RequestPostProcessor user() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
