package com.wevo.backend.section.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.section.domain.SectionDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 섹션 초안의 버전 이력 목록. (본문 미포함 — API_SPEC §3.7.8)
 *
 * <p>초안 저장은 본문 전체를 덮어쓰므로, 실수로 지운 내용은 최신 본문만 보는 화면에서 되찾을 수
 * 없다. 이 응답은 <b>어느 버전을 열어볼지 고르게 하는 것</b>이 목적이라 본문을 싣지 않는다 —
 * 본문은 버전 본문 조회(§3.7.9)로 한 건씩 받는다.
 *
 * <p><b>되돌리기(롤백)는 제공하지 않는다.</b> 사용자가 지난 버전을 열어 필요한 부분을 직접
 * 복사해 현재 본문에 붙이는 흐름이며, 서버가 과거 본문으로 새 버전을 만드는 경로는 없다.
 *
 * <p><b>배열이 아니라 객체로 감싼다</b> — 이력이 길어져 페이지네이션(§1.5)을 붙일 때
 * 최상위 형태를 바꾸지 않고 필드만 더할 수 있게 하기 위함이다.
 *
 * @param totalCount 이 섹션에 쌓인 버전 수
 * @param versions   버전 목록 — <b>최신 버전이 먼저</b> 온다 (직전 상태를 가장 자주 찾기 때문)
 */
@Schema(requiredProperties = {"totalCount", "versions"})
public record SectionDraftVersionListResponse(
        long totalCount,
        List<VersionSummary> versions
) {

    /**
     * 최신 버전부터 정렬된 초안 목록으로 응답을 만든다. (정렬은 조회 쿼리가 보장)
     */
    public static SectionDraftVersionListResponse from(List<SectionDraft> drafts) {
        List<VersionSummary> versions = drafts.stream()
                .map(VersionSummary::from)
                .toList();
        return new SectionDraftVersionListResponse(versions.size(), versions);
    }

    /**
     * 버전 한 건의 메타데이터.
     *
     * @param version       본문 버전 (1부터, 저장할 때마다 +1)
     * @param contentLength 본문 글자 수 — 본문 없이도 <b>내용이 크게 줄어든 지점</b>을 짚을 수 있게
     *                      함께 내린다. 실수로 지운 버전을 찾는 것이 이 기능의 목적이라, 길이가
     *                      급감한 버전의 <b>직전</b>이 대개 되찾을 본문이다
     * @param editor        저장한 사람 — 기록이 없으면 생략 ({@link SectionDraftVersionEditorResponse})
     * @param savedAt       저장 시각 (KST). 초안 행은 저장 후 수정되지 않으므로 생성 시각과 같다
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"version", "contentLength", "savedAt"})
    public record VersionSummary(
            Integer version,
            int contentLength,
            SectionDraftVersionEditorResponse editor,
            LocalDateTime savedAt
    ) {

        private static VersionSummary from(SectionDraft draft) {
            String content = draft.getContent();
            return new VersionSummary(
                    draft.getVersion(),
                    content == null ? 0 : content.length(),
                    SectionDraftVersionEditorResponse.from(draft.getLastEditor()),
                    draft.getCreatedAt());
        }
    }
}
