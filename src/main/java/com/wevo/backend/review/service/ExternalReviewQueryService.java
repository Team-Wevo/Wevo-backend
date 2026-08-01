package com.wevo.backend.review.service;

import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.dto.response.ExternalReviewResultResponse;
import com.wevo.backend.review.dto.response.ReviewLinkCurrentResponse;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import com.wevo.backend.review.repository.ReviewIntentComparisonRepository;
import com.wevo.backend.review.domain.ReviewSubmission;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 검토 조회 로직. (팀장 전용 — 제품 정책서 §6.2)
 *
 * <p>팀장이 섹션의 외부 검토 이해도 분포와 개별 코멘트를 참고용으로 확인하고, 현재 살아있는
 * 검토 링크의 상태를 복구한다. 외부 검토 결과는 <b>섹션 확정 조건에 포함되지 않는다.</b>
 *
 * <p>링크 발급·만료 등 상태를 바꾸는 로직은 {@link ReviewLinkService} 가 담당하고,
 * 이 서비스는 조회만 담당한다.
 *
 * <p>팀장(OWNER) 권한 검증은 {@link SectionAccessGuard} 에 위임한다.
 */
@Service
@Transactional(readOnly = true)
public class ExternalReviewQueryService {

    private final SectionAccessGuard sectionAccessGuard;
    private final ReviewLinkRepository reviewLinkRepository;
    private final ReviewSubmissionRepository reviewSubmissionRepository;
    private final ReviewIntentComparisonRepository comparisonRepository;

    public ExternalReviewQueryService(SectionAccessGuard sectionAccessGuard,
                                      ReviewLinkRepository reviewLinkRepository,
                                      ReviewSubmissionRepository reviewSubmissionRepository,
                                      ReviewIntentComparisonRepository comparisonRepository) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.reviewLinkRepository = reviewLinkRepository;
        this.reviewSubmissionRepository = reviewSubmissionRepository;
        this.comparisonRepository = comparisonRepository;
    }

    /**
     * 섹션의 외부 검토 결과(이해도 집계 + 개별 목록)를 조회한다. (팀장 전용)
     */
    public ExternalReviewResultResponse getResults(Long sectionId, Long userId) {
        sectionAccessGuard.requireOwnedSection(sectionId, userId);

        List<ReviewSubmission> submissions =
                reviewSubmissionRepository.findAllWithLinkByProjectSectionId(sectionId);
        return ExternalReviewResultResponse.from(
                submissions,
                submissions.isEmpty()
                        ? List.of()
                        : comparisonRepository.findAllWithSubmissionBySubmissionIdIn(
                                submissions.stream().map(ReviewSubmission::getId).toList()));
    }

    /**
     * 섹션의 현재 활성({@code ACTIVE}) 외부 검토 링크를 상태 복구용으로 조회한다. (팀장 전용)
     *
     * <p>팀장이 링크 발급 후 새로고침하면 활성 링크 존재 여부를 알 수 없어 무심코 재발급 →
     * 이미 공유한 링크가 죽는 사고가 난다. 이 조회로 현재 링크의 존재·메타데이터를 복구한다.
     * 섹션당 {@code ACTIVE} 링크는 부분 유니크 인덱스로 최대 1개가 보장되므로 결과는 단건이다.
     *
     * <p>원문 토큰은 재노출하지 않으며(해시만 저장), 활성 링크가 없으면 {@link Optional#empty()}
     * 를 반환한다(컨트롤러가 {@code data} 없는 {@code 200} 으로 변환).
     * 권한 검증은 발급과 동일하게 OWNER 로 제한한다.
     */
    public Optional<ReviewLinkCurrentResponse> getCurrentActiveLink(Long sectionId, Long userId) {
        sectionAccessGuard.requireOwnedSection(sectionId, userId);

        return reviewLinkRepository
                .findByProjectSection_IdAndStatus(sectionId, ReviewLinkStatus.ACTIVE)
                .stream()
                .findFirst()
                .map(link -> ReviewLinkCurrentResponse.of(
                        link, reviewSubmissionRepository.countByReviewLink_Id(link.getId())));
    }
}
