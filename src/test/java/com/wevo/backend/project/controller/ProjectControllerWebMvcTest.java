package com.wevo.backend.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.dto.request.ProjectUpdateRequest;
import com.wevo.backend.project.dto.response.ProjectDetailResponse;
import com.wevo.backend.project.dto.response.ProjectDetailResponse.SectionProgress;
import com.wevo.backend.project.service.ProjectService;
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

/**
 * 프로젝트 기본 정보 수정의 HTTP 계약 검증. (API_SPEC §3.2.10)
 *
 * <p>서비스 단위 테스트가 못 보는 지점만 다룬다 — 요청 본문 검증({@code C001})과
 * 도메인 예외의 HTTP 상태 매핑이다.
 */
@WebMvcTest(ProjectController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
class ProjectControllerWebMvcTest {

    private static final String URL = "/api/projects/1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("OWNER 가 수정하면 200 PROJECT_UPDATED 와 갱신된 상세를 반환한다")
    void update_returns200WithUpdatedDetail() throws Exception {
        given(projectService.updateProject(7L, 1L, new ProjectUpdateRequest("위보 중간발표", "설명")))
                .willReturn(detail("위보 중간발표", "설명"));

        mockMvc.perform(patch(URL).with(authenticatedUser())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "위보 중간발표",
                                  "description": "설명"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT_UPDATED"))
                .andExpect(jsonPath("$.message").value("프로젝트가 수정되었습니다."))
                .andExpect(jsonPath("$.data.title").value("위보 중간발표"))
                .andExpect(jsonPath("$.data.description").value("설명"));
    }

    @Test
    @DisplayName("설명이 없으면 description 필드 자체가 응답에서 생략된다")
    void update_omitsNullDescription() throws Exception {
        given(projectService.updateProject(7L, 1L, new ProjectUpdateRequest("이름만", null)))
                .willReturn(detail("이름만", null));

        mockMvc.perform(patch(URL).with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"title\": \"이름만\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").doesNotExist());
    }

    @Test
    @DisplayName("title 이 공백뿐이면 400 C001 을 반환하고 서비스를 호출하지 않는다")
    void update_withBlankTitle_returns400C001() throws Exception {
        mockMvc.perform(patch(URL).with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"title\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        verify(projectService, never()).updateProject(any(), any(), any());
    }

    @Test
    @DisplayName("title 이 200자를 넘으면 400 C001 을 반환한다")
    void update_withTooLongTitle_returns400C001() throws Exception {
        mockMvc.perform(patch(URL).with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"title\": \"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    @DisplayName("MEMBER 가 수정을 시도하면 403 A002 를 반환한다")
    void update_asMember_returns403A002() throws Exception {
        given(projectService.updateProject(any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(patch(URL).with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"title\": \"바꾼 이름\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("비멤버가 수정을 시도하면 404 P001 로 존재를 숨긴다")
    void update_asNonMember_returns404P001() throws Exception {
        given(projectService.updateProject(any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(patch(URL).with(authenticatedUser())
                        .contentType("application/json")
                        .content("{\"title\": \"바꾼 이름\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("인증 없이 수정하면 401 A001 을 반환한다")
    void update_withoutAuthentication_returns401A001() throws Exception {
        mockMvc.perform(patch(URL)
                        .contentType("application/json")
                        .content("{\"title\": \"바꾼 이름\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    private ProjectDetailResponse detail(String title, String description) {
        return new ProjectDetailResponse(1L, title, description, "아이디어",
                OutputType.PRESENTATION, "심사위원", ProjectStatus.ACTIVE,
                ProjectMemberRole.OWNER, 1L, new SectionProgress(6, 0));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
