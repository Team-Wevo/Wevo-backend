package com.wevo.backend.section.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.section.service.SectionDraftVersionContent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 지난 초안 한 버전의 본문. (API_SPEC §3.7.9)
 *
 * <p>이력 목록(§3.7.8)에서 고른 버전을 열어볼 때 쓴다. 사용자가 필요한 대목을 <b>직접 복사</b>해
 * 현재 본문에 붙이는 것이 복구 경로이며, 이 응답이 본문을 되돌리지는 않는다.
 *
 * <p>최신 버전을 요청해도 그대로 반환한다 — 편집 화면의 진입점인 최신 초안 조회(§3.7.1)와 달리
 * 여기서는 편집권(`activeEditor`)·저장 기준 버전을 함께 내리지 않는다. 이 응답은 <b>읽기 전용
 * 이력</b>이라 편집을 시작하는 데 쓰이지 않기 때문이다.
 *
 * @param version 조회한 본문 버전
 * @param content 해당 버전의 본문 — 저장된 값이 없으면 빈 문자열
 * @param editor  저장한 사람 — 기록이 없으면 생략
 * @param savedAt 저장 시각 (KST)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"version", "content", "savedAt"})
public record SectionDraftVersionDetailResponse(
        Integer version,
        String content,
        SectionDraftVersionEditorResponse editor,
        LocalDateTime savedAt
) {

    public static SectionDraftVersionDetailResponse from(SectionDraftVersionContent version) {
        String content = version.content();
        return new SectionDraftVersionDetailResponse(
                version.version(),
                // 본문 컬럼이 nullable 이라 빈 문자열로 정규화한다 — 화면이 null 분기를 두지 않게.
                content == null ? "" : content,
                SectionDraftVersionEditorResponse.of(version.editorUserId(), version.editorName()),
                version.savedAt());
    }
}
