package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import java.time.LocalDateTime;

/**
 * 내 의견 상태 조회 응답.
 *
 * <p>의견 미작성은 에러가 아니라 {@code exists=false} 로 표현한다 —
 * 화면 초기 진입 시 "작성 전" 상태를 그려야 하기 때문. (API_SPEC_NOTION.md §5.1)
 */
public record MyOpinionResponse(
        boolean exists,
        Long id,
        String content,
        OpinionStatus status,
        LocalDateTime updatedAt
) {

    public static MyOpinionResponse from(Opinion opinion) {
        return new MyOpinionResponse(
                true,
                opinion.getId(),
                opinion.getContent(),
                opinion.getStatus(),
                opinion.getUpdatedAt()
        );
    }

    public static MyOpinionResponse empty() {
        return new MyOpinionResponse(false, null, null, null, null);
    }
}
