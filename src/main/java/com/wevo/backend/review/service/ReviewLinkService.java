package com.wevo.backend.review.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.dto.request.ExternalReviewSubmitRequest;
import com.wevo.backend.review.dto.response.ExternalReviewViewResponse;
import com.wevo.backend.review.dto.response.ReviewLinkResponse;
import com.wevo.backend.review.dto.response.ReviewSubmissionResponse;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.SectionDraftRepository;
import java.util.UUID;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 검토 링크(보조 검토) 관련 로직. (제품 정책서 §1.3, §6.2)
 *
 * <ul>
 *   <li>링크 발급은 <b>팀장(OWNER)만</b> 가능하다.</li>
 *   <li>외부 검토자는 로그인 없이 토큰으로 초안을 <b>읽고</b> 이해도를 제출한다.</li>
 *   <li>외부 검토 결과는 <b>섹션 확정 조건에 포함되지 않는다.</b> (참고용)</li>
 *   <li>MVP 에서는 링크 만료·비활성화를 구현하지 않는다. (§2.1) isActive 는 컬럼 기본값(true)으로만 세팅한다.</li>
 * </ul>
 *
 * <p>검토 링크는 <b>외부 검토 전용</b>이다. 내부(팀) 검토(§6.1)는 링크가 아니라 멤버십 기반이다.
 *
 * <p>팀장(OWNER) 권한 검증은 SectionAccessGuard 에 위임한다.
 */
@Service
@Transactional(readOnly = true)
public class ReviewLinkService {

    private final SectionAccessGuard sectionAccessGuard;
    private final SectionDraftRepository sectionDraftRepository;
    private final ReviewLinkRepository reviewLinkRepository;
    private final ReviewSubmissionRepository reviewSubmissionRepository;
    private final UserRepository userRepository;

    public ReviewLinkService(SectionAccessGuard sectionAccessGuard,
                             SectionDraftRepository sectionDraftRepository,
                             ReviewLinkRepository reviewLinkRepository,
                             ReviewSubmissionRepository reviewSubmissionRepository, UserRepository userRepository) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.sectionDraftRepository = sectionDraftRepository;
        this.reviewLinkRepository = reviewLinkRepository;
        this.reviewSubmissionRepository = reviewSubmissionRepository;
        this.userRepository = userRepository;
    }

    /**
     * 외부 검토 링크를 발급한다. (팀장 전용)
     */
    @Transactional
    public ReviewLinkResponse issueExternalLink(Long sectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireOwnedSection(sectionId, userId);
        User createdBy = userRepository.getReferenceById(userId);
        ReviewLink link = ReviewLink.builder()
                .projectSection(section)
                .createdBy(createdBy)
                .token(generateToken())
                .isActive(true)
                .build();
        reviewLinkRepository.save(link);

        return ReviewLinkResponse.from(link);
    }

    /**
     * 토큰으로 섹션 초안을 읽기 전용으로 조회한다.
     */
    public ExternalReviewViewResponse getExternalView(String token) {
        ReviewLink link = findLink(token);
        ProjectSection section = link.getProjectSection();

        String content = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(section.getId())
                .map(SectionDraft::getContent)
                .orElse(null);

        return new ExternalReviewViewResponse(section.getId(), section.getTitle(), content);
    }

    /**
     * 외부 검토자의 이해도 제출을 저장한다.
     */
    @Transactional
    public ReviewSubmissionResponse submitExternalReview(String token, ExternalReviewSubmitRequest request) {
        ReviewLink link = findLink(token);

        ReviewSubmission submission = ReviewSubmission.builder()
                .reviewLink(link)
                .understandingSignal(request.understandingSignal())
                .reviewerName(request.reviewerName())
                .summary(request.summary())
                .build();
        reviewSubmissionRepository.save(submission);

        return ReviewSubmissionResponse.from(submission);
    }

    //검토 링크 조회
    private ReviewLink findLink(String token) {
        return reviewLinkRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_LINK_NOT_FOUND));
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
