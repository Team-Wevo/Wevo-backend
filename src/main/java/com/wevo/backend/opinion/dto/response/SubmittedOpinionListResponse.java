package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 섹션에 제출된 팀원 의견 목록 응답. (정책서 §5 공개 게이트)
 *
 * <p>요청자가 아직 제출하지 않았으면({@code everSubmitted=false}) 목록은 비우고
 * 제출 건수만 공개한다 — 베끼기 방지.
 */
public record SubmittedOpinionListResponse(
        boolean everSubmitted,
        int totalSubmittedCount,
        List<SubmittedOpinionResponse> opinions
) {

    /** 요청자가 제출을 마친 경우 — 전체 목록을 공개한다. */
    public static SubmittedOpinionListResponse visible(List<Opinion> opinions) {
        return new SubmittedOpinionListResponse(
                true,
                opinions.size(),
                opinions.stream().map(SubmittedOpinionResponse::from).toList()
        );
    }

    /** 요청자가 아직 제출하지 않은 경우 — 건수만 공개한다. */
    public static SubmittedOpinionListResponse hidden(int totalSubmittedCount) {
        return new SubmittedOpinionListResponse(false, totalSubmittedCount, List.of());
    }

    public record SubmittedOpinionResponse(
            Long id,
            AuthorResponse author,
            String content,
            LocalDateTime submittedAt
    ) {

        public static SubmittedOpinionResponse from(Opinion opinion) {
            return new SubmittedOpinionResponse(
                    opinion.getId(),
                    AuthorResponse.from(opinion.getAuthor()),
                    // 팀에 공개되는 본문은 작업본이 아니라 제출본이다 (재제출 모델 §4.1)
                    opinion.getSubmittedContentOrLegacy(),
                    opinion.getSubmittedAt()
            );
        }
    }

    public record AuthorResponse(
            Long id,
            String name,
            String profileImageUrl
    ) {

        public static AuthorResponse from(User author) {
            return new AuthorResponse(author.getId(), author.getName(), author.getProfileImageUrl());
        }
    }
}
