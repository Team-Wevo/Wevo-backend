package com.wevo.backend.export.controller;

import com.wevo.backend.export.dto.response.FinalOutputContentResponse;
import com.wevo.backend.export.dto.response.FinalOutputResponse;
import com.wevo.backend.export.service.FinalOutputFile;
import com.wevo.backend.export.service.FinalOutputFormat;
import com.wevo.backend.export.service.FinalOutputService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Export", description = "최종 결과물 조회 · 다운로드 API")
public class FinalOutputController {

    private static final MediaType TEXT_PLAIN_UTF8 =
            new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    /** {@code MediaType} 에 상수가 없는 타입이라 직접 만든다. */
    private static final MediaType TEXT_MARKDOWN_UTF8 =
            new MediaType("text", "markdown", StandardCharsets.UTF_8);

    /** RFC 5987 {@code attr-char} 중 영숫자를 제외한 문자. */
    private static final String ATTRIBUTE_CHAR_SYMBOLS = "!#$&+-.^_`|~";

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private final FinalOutputService finalOutputService;

    public FinalOutputController(FinalOutputService finalOutputService) {
        this.finalOutputService = finalOutputService;
    }

    /**
     * 최종 결과물 조회 — 전 섹션 확정 시에만 본문을 담고, 그 전에는 진행도만 반환한다.
     * 멤버가 아니면 404(PROJECT_NOT_FOUND/존재 숨김).
     */
    @Operation(summary = "최종 결과물 조회 — 전 섹션 확정 시에만 본문 조립 (미확정이면 진행도만)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK — ready=false 면 sections 생략"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P001 — 프로젝트 없음 또는 비멤버 (존재 숨김)")
    })
    @GetMapping("/{projectId}/final-output")
    public ResponseEntity<ApiResponse<FinalOutputResponse>> getFinalOutput(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        FinalOutputResponse response = finalOutputService.getFinalOutput(projectId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 완성본을 클립보드 복사용 <b>일반 텍스트</b>로 조회한다. (EXP-02)
     *
     * <p>마크다운 기호 없이 줄바꿈과 여백으로만 구조를 표현해, 서식을 해석하지 않는 곳
     * (메모장·메신저·메일 본문)에 붙여 넣어도 읽히게 한다.
     *
     * <p>전 섹션 확정 시에만 제공한다 — 미확정이면 409. 멤버가 아니면 404(존재 숨김).
     */
    @GetMapping("/{projectId}/final-output/plain-text")
    public ResponseEntity<ApiResponse<FinalOutputContentResponse>> getPlainText(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.",
                finalOutputService.getFormattedOutput(
                        projectId, principal.userId(), FinalOutputFormat.PLAIN_TEXT)));
    }

    /**
     * 완성본을 클립보드 복사용 <b>마크다운</b>으로 조회한다. (EXP-03)
     *
     * <p>붙여 넣는 곳이 마크다운을 해석한다고 보고 제목 구조를 {@code #}/{@code ##} 로 표현한다.
     *
     * <p>파일 다운로드가 아니라 복사용 조회이므로 응답은 JSON({@code ApiResponse} 래퍼)이다.
     * 파일 응답은 별도 계약으로 아래 다운로드 API 가 담당한다. (CLAUDE.md §5.3)
     */
    @GetMapping("/{projectId}/final-output/markdown")
    public ResponseEntity<ApiResponse<FinalOutputContentResponse>> getMarkdown(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.",
                finalOutputService.getFormattedOutput(
                        projectId, principal.userId(), FinalOutputFormat.MARKDOWN)));
    }

    /**
     * 완성본을 <b>일반 텍스트 파일(.txt)</b> 로 다운로드한다. (EXP-04)
     *
     * <p>본문은 복사 API와 완전히 같다 — 차이는 응답을 JSON 으로 감싸지 않고 파일로
     * 내려보낸다는 것뿐이다. 전 섹션 확정 시에만 제공하며, 미확정이면 409. 비멤버는 404(존재 숨김).
     *
     * <p>{@code fileName} 으로 파일명을 지정할 수 있고, 생략하면 프로젝트 제목을 쓴다. (EXP-06)
     */
    @Operation(summary = "완성본 txt 파일 다운로드 — 성공은 파일, 실패는 JSON")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "text/plain 파일 (ApiResponse 래퍼 미적용)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P001 — 프로젝트 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "C003 — 미확정 섹션이 있음")
    })
    @GetMapping("/{projectId}/final-output/download/plain-text")
    public ResponseEntity<String> downloadPlainText(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @Parameter(description = "파일명 (확장자 제외) — 생략 시 프로젝트 제목")
            @RequestParam(required = false) String fileName
    ) {
        return fileResponse(
                finalOutputService.getDownloadFile(
                        projectId, principal.userId(), FinalOutputFormat.PLAIN_TEXT, fileName),
                TEXT_PLAIN_UTF8);
    }

    /**
     * 완성본을 <b>마크다운 파일(.md)</b> 로 다운로드한다. (EXP-05)
     *
     * <p>파일명 규칙은 txt 다운로드와 같다 — {@code fileName} 지정, 생략 시 프로젝트 제목.
     */
    @Operation(summary = "완성본 마크다운 파일 다운로드 — 성공은 파일, 실패는 JSON")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "text/markdown 파일 (ApiResponse 래퍼 미적용)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P001 — 프로젝트 없음 또는 비멤버 (존재 숨김)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "C003 — 미확정 섹션이 있음")
    })
    @GetMapping("/{projectId}/final-output/download/markdown")
    public ResponseEntity<String> downloadMarkdown(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @Parameter(description = "파일명 (확장자 제외) — 생략 시 프로젝트 제목")
            @RequestParam(required = false) String fileName
    ) {
        return fileResponse(
                finalOutputService.getDownloadFile(
                        projectId, principal.userId(), FinalOutputFormat.MARKDOWN, fileName),
                TEXT_MARKDOWN_UTF8);
    }

    /**
     * 완성본 파일을 첨부(attachment) 응답으로 만든다.
     *
     * <p><b>매핑에 {@code produces} 를 걸지 않는다.</b> 걸어두면 그 media type 이 요청 속성으로 남아,
     * 예외가 났을 때 {@code GlobalExceptionHandler} 가 만든 <b>JSON 실패 응답</b>까지 같은 타입으로
     * 써야 해서 406 이 된다. 성공은 파일·실패는 JSON 인 혼합 계약(CLAUDE.md §5.3)을 지키려면
     * Content-Type 을 응답 헤더로 직접 지정해야 한다.
     */
    private ResponseEntity<String> fileResponse(FinalOutputFile file, MediaType contentType) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition(file.fileName(), file.asciiFileName()))
                .body(file.content());
    }

    /**
     * {@code Content-Disposition: attachment} 헤더를 만든다. (RFC 6266)
     *
     * <p>파일명을 <b>두 형태로 함께</b> 싣는다 — {@code filename*}(RFC 5987, UTF-8 퍼센트 인코딩)은
     * 한글 제목을 그대로 전달하고, {@code filename}(ASCII)은 {@code filename*} 을 모르는 옛
     * 클라이언트를 위한 대체값이다. 둘 다 이해하는 클라이언트는 {@code filename*} 을 우선한다.
     *
     * <p>Spring 의 {@code ContentDisposition} 은 둘 중 하나만 내보내므로 직접 조립한다.
     */
    private String contentDisposition(String fileName, String asciiFileName) {
        return "attachment; filename=\"" + asciiFileName + "\"; "
                + "filename*=UTF-8''" + encodeRfc5987(fileName);
    }

    /**
     * RFC 5987 {@code ext-value} 로 퍼센트 인코딩한다.
     *
     * <p>{@code URLEncoder} 를 쓰지 않는 이유: 공백을 {@code +} 로 바꿔 파일명이 깨진다.
     * 여기서는 {@code attr-char} 만 그대로 두고 나머지 바이트를 모두 {@code %XX} 로 옮긴다.
     */
    private String encodeRfc5987(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            if (isAttributeChar(b)) {
                encoded.append((char) b);
            } else {
                encoded.append('%').append(HEX_DIGITS[(b >> 4) & 0xF]).append(HEX_DIGITS[b & 0xF]);
            }
        }
        return encoded.toString();
    }

    /** RFC 5987 {@code attr-char} — 인코딩 없이 그대로 쓸 수 있는 문자. */
    private boolean isAttributeChar(byte b) {
        return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9')
                || ATTRIBUTE_CHAR_SYMBOLS.indexOf(b) >= 0;
    }
}
