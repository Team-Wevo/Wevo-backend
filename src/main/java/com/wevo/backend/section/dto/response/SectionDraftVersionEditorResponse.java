package com.wevo.backend.section.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 초안 버전을 저장한 사람.
 *
 * <p>이력 목록(§3.7.8)과 버전 본문 조회(§3.7.9)가 같은 형태로 내리므로 한 타입으로 둔다.
 *
 * <p><b>사용자 엔티티가 아니라 스칼라 값으로 만든다</b> — 표시에 필요한 것은 ID 와 이름뿐인데
 * {@code User} 를 받으면 section 의 응답 DTO 가 user 도메인 엔티티에 의존하게 된다.
 * (CLAUDE.md §6 — 도메인 간 접근) 값은 조회 모델
 * ({@link com.wevo.backend.section.service.SectionDraftVersionSummary})이 채워 넘긴다.
 *
 * <p>탈퇴한 사용자도 그대로 내린다 — 탈퇴 시 표시 이름이 대체 문구로 바뀌므로 개인정보가 남지
 * 않고, "누가 저장했는지"의 자리는 비지 않는다.
 *
 * @param userId 사용자 ID
 * @param name   표시 이름
 */
@Schema(requiredProperties = {"userId", "name"})
public record SectionDraftVersionEditorResponse(Long userId, String name) {

    /**
     * 편집자 정보를 만든다. 편집자가 기록되지 않은 버전이면 {@code null}.
     *
     * <p>편집자가 없는 행이 생기는 경로는 두 가지다 — 컬럼이 nullable 이던 시절의 레거시 데이터와,
     * 사람이 아닌 AI 가 만든 초안. 목록에서 그런 버전을 빼면 "이 시점에 본문이 이렇게 바뀌었다"는
     * 이력의 연속성이 끊기므로, 버전은 그대로 두고 이 필드만 생략한다.
     */
    public static SectionDraftVersionEditorResponse of(Long userId, String name) {
        if (userId == null) {
            return null;
        }
        return new SectionDraftVersionEditorResponse(userId, name);
    }
}
