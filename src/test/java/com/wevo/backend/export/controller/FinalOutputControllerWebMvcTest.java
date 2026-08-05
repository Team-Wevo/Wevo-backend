package com.wevo.backend.export.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.export.service.FinalOutputFile;
import com.wevo.backend.export.service.FinalOutputFormat;
import com.wevo.backend.export.service.FinalOutputService;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 완성본 다운로드의 HTTP 계약을 검증한다. (API_SPEC §3.6.4·§3.6.5)
 *
 * <p>본문 조립 규칙은 {@code FinalOutputFormatterTest}, 파일명 정제는 {@code FinalOutputFileNamerTest}
 * 가 이미 검증하므로 여기서는 <b>전송 계층만</b> 본다 — Content-Type, Content-Disposition, 그리고
 * 성공은 파일·실패는 JSON 인 혼합 계약이 실제로 성립하는지.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FinalOutputControllerWebMvcTest {

    private static final String TXT_URL = "/api/projects/1/final-output/download/plain-text";
    private static final String MD_URL = "/api/projects/1/final-output/download/markdown";

    private static final String BODY = """
            위보 기획

            01. 문제 정의

            문제 정의 확정본""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinalOutputService finalOutputService;

    @Test
    @DisplayName("인증 없이 다운로드하면 401 A001을 JSON으로 반환한다")
    void download_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(get(TXT_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("txt 다운로드는 text/plain 파일과 첨부 헤더를 반환한다")
    void downloadPlainText_returnsTextFile() throws Exception {
        given(finalOutputService.getDownloadFile(1L, 7L, FinalOutputFormat.PLAIN_TEXT, null))
                .willReturn(new FinalOutputFile("위보 기획.txt", "final-output.txt", BODY));

        mockMvc.perform(get(TXT_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string(BODY))
                // ApiResponse 래퍼를 적용하지 않는다 (CLAUDE.md §5.3)
                .andExpect(content().string(not(containsString("\"success\""))))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"final-output.txt\"; "
                                + "filename*=UTF-8''%EC%9C%84%EB%B3%B4%20%EA%B8%B0%ED%9A%8D.txt"));
    }

    @Test
    @DisplayName("마크다운 다운로드는 text/markdown 파일을 반환한다")
    void downloadMarkdown_returnsMarkdownFile() throws Exception {
        given(finalOutputService.getDownloadFile(1L, 7L, FinalOutputFormat.MARKDOWN, null))
                .willReturn(new FinalOutputFile("위보 기획.md", "final-output.md", "# 위보 기획"));

        mockMvc.perform(get(MD_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(content().string("# 위보 기획"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"final-output.md\"; "
                                + "filename*=UTF-8''%EC%9C%84%EB%B3%B4%20%EA%B8%B0%ED%9A%8D.md"));
    }

    @Test
    @DisplayName("ASCII 파일명만 있는 프로젝트도 두 형태를 모두 싣는다")
    void downloadPlainText_asciiTitle_stillSendsBothFilenameForms() throws Exception {
        given(finalOutputService.getDownloadFile(1L, 7L, FinalOutputFormat.PLAIN_TEXT, null))
                .willReturn(new FinalOutputFile("Wevo Plan.txt", "Wevo Plan.txt", BODY));

        mockMvc.perform(get(TXT_URL).with(authenticatedUser()))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Wevo Plan.txt\"; "
                                + "filename*=UTF-8''Wevo%20Plan.txt"));
    }

    @Test
    @DisplayName("fileName 을 지정하면 그대로 서비스에 전달한다")
    void download_withRequestedFileName_passesItThrough() throws Exception {
        given(finalOutputService.getDownloadFile(1L, 7L, FinalOutputFormat.PLAIN_TEXT, "최종 제안서"))
                .willReturn(new FinalOutputFile("최종 제안서.txt", "final-output.txt", BODY));

        mockMvc.perform(get(TXT_URL).param("fileName", "최종 제안서").with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"final-output.txt\"; "
                                + "filename*=UTF-8''%EC%B5%9C%EC%A2%85%20%EC%A0%9C%EC%95%88%EC%84%9C.txt"));
    }

    @Test
    @DisplayName("fileName 을 생략하면 null 로 전달해 프로젝트 제목을 쓰게 한다")
    void download_withoutRequestedFileName_passesNull() throws Exception {
        given(finalOutputService.getDownloadFile(1L, 7L, FinalOutputFormat.MARKDOWN, null))
                .willReturn(new FinalOutputFile("위보 기획.md", "final-output.md", "# 위보 기획"));

        mockMvc.perform(get(MD_URL).with(authenticatedUser()))
                .andExpect(status().isOk());

        then(finalOutputService).should()
                .getDownloadFile(1L, 7L, FinalOutputFormat.MARKDOWN, null);
    }

    @Test
    @DisplayName("미확정 섹션이 있으면 파일이 아니라 409 C003 JSON을 반환한다")
    void download_notReady_returnsC003AsJson() throws Exception {
        given(finalOutputService.getDownloadFile(anyLong(), anyLong(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.CONFLICT));

        // 매핑에 produces 를 걸지 않았기 때문에 실패 응답이 JSON 으로 협상된다 (406 이 아니다).
        mockMvc.perform(get(TXT_URL).with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    @DisplayName("프로젝트 멤버가 아니면 404 P001 JSON을 반환한다 — 존재를 숨긴다")
    void download_nonMember_returnsP001AsJson() throws Exception {
        given(finalOutputService.getDownloadFile(anyLong(), anyLong(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(get(MD_URL).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("P001"));
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES));
    }
}
