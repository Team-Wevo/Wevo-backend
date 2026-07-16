package com.wevo.backend.review.service;

import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.dto.response.ExternalReviewResultResponse;
import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 검토 결과 조회 로직. (팀장 전용 — 제품 정책서 §6.2)
 *
 * <p>팀장이 섹션의 외부 검토 이해도 분포와 개별 코멘트를 참고용으로 확인한다.
 * 이 결과는 <b>섹션 확정 조건에 포함되지 않는다.</b>
 *
 * <p>팀장(OWNER) 권한 검증은 {@link SectionAccessGuard} 에 위임한다.
 */
@Service
@Transactional(readOnly = true)
public class ExternalReviewQueryService {

    private final SectionAccessGuard sectionAccessGuard;
    private final ReviewSubmissionRepository reviewSubmissionRepository;

    public ExternalReviewQueryService(SectionAccessGuard sectionAccessGuard,
                                      ReviewSubmissionRepository reviewSubmissionRepository) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.reviewSubmissionRepository = reviewSubmissionRepository;
    }

    /**
     * 섹션의 외부 검토 결과(이해도 집계 + 개별 목록)를 조회한다. (팀장 전용)
     */
    public ExternalReviewResultResponse getResults(Long sectionId, Long userId) {
        sectionAccessGuard.requireOwnedSection(sectionId, userId);

        return ExternalReviewResultResponse.from(
                reviewSubmissionRepository.findByReviewLink_ProjectSection_IdOrderByCreatedAtDesc(sectionId));
    }
}
