package com.wevo.backend.export.controller;

import com.wevo.backend.export.dto.response.FinalOutputContentResponse;
import com.wevo.backend.export.dto.response.FinalOutputResponse;
import com.wevo.backend.export.service.FinalOutputFormat;
import com.wevo.backend.export.service.FinalOutputService;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class FinalOutputController {

    private final FinalOutputService finalOutputService;

    public FinalOutputController(FinalOutputService finalOutputService) {
        this.finalOutputService = finalOutputService;
    }

    /**
     * 최종 결과물 조회 — 전 섹션 확정 시에만 본문을 담고, 그 전에는 진행도만 반환한다.
     * 멤버가 아니면 404(PROJECT_NOT_FOUND/존재 숨김).
     */
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
     * 파일 응답은 별도 계약으로 추후 추가한다. (CLAUDE.md §5.3)
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
}
