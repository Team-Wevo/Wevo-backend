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
import com.wevo.backend.section.dto.response.DraftLeaseAcquireResponse;
import com.wevo.backend.section.dto.response.DraftLeaseAcquireResponse.HolderResponse;
import com.wevo.backend.section.service.DraftLeaseService;
import java.time.LocalDateTime;
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
class DraftLeaseControllerWebMvcTest {

    private static final String URL = "/api/project-sections/10/draft-lease/acquire";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DraftLeaseService draftLeaseService;

    @Test
    @DisplayName("인증 없이 편집 잠금을 획득하면 A001을 반환한다")
    void acquire_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("편집 잠금 획득 성공 시 201과 lease 정보를 반환한다")
    void acquire_returnsCreatedLease() throws Exception {
        given(draftLeaseService.acquire(10L, 7L)).willReturn(new DraftLeaseAcquireResponse(
                91L,
                10L,
                new HolderResponse(7L, "김민준"),
                LocalDateTime.of(2026, 7, 19, 14, 1)
        ));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("LEASE_ACQUIRED"))
                .andExpect(jsonPath("$.message").value("편집권을 획득했습니다."))
                .andExpect(jsonPath("$.data.leaseId").value(91))
                .andExpect(jsonPath("$.data.projectSectionId").value(10))
                .andExpect(jsonPath("$.data.holder.id").value(7))
                .andExpect(jsonPath("$.data.holder.name").value("김민준"))
                .andExpect(jsonPath("$.data.leaseUntil").value("2026-07-19T14:01:00"));
    }

    @Test
    @DisplayName("다른 사용자가 편집 중이면 S005와 보유자 정보를 반환한다")
    void acquire_whenHeldByOther_returnsS005() throws Exception {
        given(draftLeaseService.acquire(10L, 7L)).willThrow(new BusinessException(
                ErrorCode.DRAFT_LEASE_HELD_BY_OTHER,
                List.of(new FieldError("holder", "이서연 (leaseUntil: 2026-07-19T14:01:00)"))
        ));

        mockMvc.perform(post(URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("S005"))
                .andExpect(jsonPath("$.errors[0].field").value("holder"));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
