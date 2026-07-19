package com.wevo.backend.section.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.section.dto.response.DraftLeaseAcquireResponse;
import com.wevo.backend.section.dto.response.DraftLeaseRenewResponse;
import com.wevo.backend.section.dto.response.DraftLeaseStatusResponse;
import com.wevo.backend.section.dto.response.DraftLeaseStatusResponse.EditorResponse;
import com.wevo.backend.section.service.DraftLeaseService;
import java.time.LocalDateTime;
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

    private static final String LEASE_URL = "/api/project-sections/10/draft/lease";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DraftLeaseService draftLeaseService;

    @Test
    @DisplayName("인증 없이 편집 잠금을 획득하면 A001을 반환한다")
    void acquire_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(post(LEASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("편집 잠금 획득 성공 시 200과 만료 시각을 반환한다")
    void acquire_returnsLeaseExpiration() throws Exception {
        given(draftLeaseService.acquire(10L, 7L)).willReturn(new DraftLeaseAcquireResponse(
                LocalDateTime.of(2026, 7, 19, 14, 1)
        ));

        mockMvc.perform(post(LEASE_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("DRAFT_LEASE_ACQUIRED"))
                .andExpect(jsonPath("$.message").value("편집권을 획득했습니다."))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-07-19T14:01:00"))
                .andExpect(jsonPath("$.data.leaseId").doesNotExist())
                .andExpect(jsonPath("$.data.holder").doesNotExist());
    }

    @Test
    @DisplayName("다른 사용자가 편집 중이면 errors 없이 S004를 반환한다")
    void acquire_whenHeldByOther_returnsS004() throws Exception {
        given(draftLeaseService.acquire(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER));

        mockMvc.perform(post(LEASE_URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("S004"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("인증 없이 편집 잠금 상태를 조회하면 A001을 반환한다")
    void getStatus_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(get(LEASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("유효한 잠금이 있으면 locked=true와 편집자·만료 시각을 반환한다")
    void getStatus_whenLocked_returnsEditorAndExpiration() throws Exception {
        given(draftLeaseService.getStatus(10L, 7L)).willReturn(new DraftLeaseStatusResponse(
                true,
                new EditorResponse(9L, "이서연"),
                LocalDateTime.of(2026, 7, 19, 14, 1)
        ));

        mockMvc.perform(get(LEASE_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.editor.userId").value(9))
                .andExpect(jsonPath("$.data.editor.name").value("이서연"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-07-19T14:01:00"))
                .andExpect(jsonPath("$.data.lease").doesNotExist());
    }

    @Test
    @DisplayName("유효한 잠금이 없으면 locked=false만 반환한다")
    void getStatus_whenUnlocked_omitsLeaseDetails() throws Exception {
        given(draftLeaseService.getStatus(10L, 7L)).willReturn(DraftLeaseStatusResponse.unlocked());

        mockMvc.perform(get(LEASE_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.editor").doesNotExist())
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist());
    }

    @Test
    @DisplayName("인증 없이 편집 잠금을 갱신하면 A001을 반환한다")
    void renew_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(put(LEASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("보유자가 편집 잠금을 갱신하면 DRAFT_LEASE_RENEWED 응답을 반환한다")
    void renew_byHolder_returnsRenewedLease() throws Exception {
        given(draftLeaseService.renew(10L, 7L)).willReturn(new DraftLeaseRenewResponse(
                LocalDateTime.of(2026, 7, 19, 14, 1, 30)
        ));

        mockMvc.perform(put(LEASE_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("DRAFT_LEASE_RENEWED"))
                .andExpect(jsonPath("$.message").value("편집권이 연장되었습니다."))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-07-19T14:01:30"))
                .andExpect(jsonPath("$.data.leaseId").doesNotExist())
                .andExpect(jsonPath("$.data.leaseUntil").doesNotExist());
    }

    @Test
    @DisplayName("편집 잠금이 없으면 S005를 반환한다")
    void renew_whenMissing_returnsS005() throws Exception {
        given(draftLeaseService.renew(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.DRAFT_LEASE_NOT_HELD));

        mockMvc.perform(put(LEASE_URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("다른 사용자의 편집 잠금 갱신은 S004를 반환한다")
    void renew_byNonHolder_returnsS004() throws Exception {
        given(draftLeaseService.renew(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER));

        mockMvc.perform(put(LEASE_URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S004"));
    }

    @Test
    @DisplayName("만료된 편집 잠금 갱신은 S005를 반환한다")
    void renew_whenExpired_returnsS005() throws Exception {
        given(draftLeaseService.renew(10L, 7L))
                .willThrow(new BusinessException(ErrorCode.DRAFT_LEASE_NOT_HELD));

        mockMvc.perform(put(LEASE_URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
