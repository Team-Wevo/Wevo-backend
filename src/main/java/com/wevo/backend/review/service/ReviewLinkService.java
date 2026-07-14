package com.wevo.backend.review.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
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
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.repository.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 검토 링크(보조 검토) 관련 로직. (제품 정책서 §1.3, §6.2)
 *
 * <ul>
 *   <li>링크 발급은 <b>팀장(OWNER)만</b> 가능하다.</li>
 *   <li>외부 검토자는 로그인 없이 토큰으로 <b>발급 시점 스냅샷</b>을 읽고 이해도를 제출한다.</li>
 *   <li>외부 검토 결과는 <b>섹션 확정 조건에 포함되지 않는다.</b> (참고용)</li>
 * </ul>
 *
 * <h2>남용 방지 (토큰만 알면 무제한 제출되는 문제 차단)</h2>
 * <ul>
 *   <li><b>브라우저당 1회</b> — 익명 검토자 키 + 링크 유니크 제약으로 중복 제출을 막는다.</li>
 *   <li><b>링크당 20개 상한</b> — 제출 시 링크 행을 락으로 잡고 count 를 검사해 동시 초과를 막는다.</li>
 *   <li><b>버전 만료·비활성화</b> — OUTDATED/CLOSED 링크는 제출을 거부한다.</li>
 *   <li><b>토큰 해시 저장</b> — 원문 대신 해시만 저장하고 요청 토큰을 해시해 비교한다.</li>
 * </ul>
 *
 * <p>팀장(OWNER) 권한 검증은 {@link SectionAccessGuard} 에 위임한다.
 */
@Service
@Transactional(readOnly = true)
public class ReviewLinkService {

    private final SectionAccessGuard sectionAccessGuard;
    private final SectionDraftRepository sectionDraftRepository;
    private final ReviewLinkRepository reviewLinkRepository;
    private final ReviewSubmissionRepository reviewSubmissionRepository;
    private final UserRepository userRepository;
    private final ReviewTokenHasher tokenHasher;

    public ReviewLinkService(SectionAccessGuard sectionAccessGuard,
                             SectionDraftRepository sectionDraftRepository,
                             ReviewLinkRepository reviewLinkRepository,
                             ReviewSubmissionRepository reviewSubmissionRepository,
                             UserRepository userRepository,
                             ReviewTokenHasher tokenHasher) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.sectionDraftRepository = sectionDraftRepository;
        this.reviewLinkRepository = reviewLinkRepository;
        this.reviewSubmissionRepository = reviewSubmissionRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
    }

    /**
     * 외부 검토 링크를 발급한다. (팀장 전용)
     *
     * <p>발급 시점의 제목·본문·버전을 스냅샷으로 저장한다. 이후 본문이 수정돼도 이 링크는
     * 스냅샷을 그대로 보여준다. 원문 토큰은 저장하지 않고(해시만 저장) 응답으로만 한 번 반환한다.
     */
    @Transactional
    public ReviewLinkResponse issueExternalLink(Long sectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireOwnedSection(sectionId, userId);
        User createdBy = userRepository.getReferenceById(userId);

        SectionDraft latestDraft = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(section.getId())
                .orElse(null);

        String rawToken = tokenHasher.generateRawToken();
        ReviewLink link = ReviewLink.builder()
                .projectSection(section)
                .createdBy(createdBy)
                .tokenHash(tokenHasher.hash(rawToken))
                .sectionTitleSnapshot(section.getTitle())
                .contentSnapshot(latestDraft != null ? latestDraft.getContent() : null)
                .contentVersion(latestDraft != null ? latestDraft.getVersion() : null)
                .status(ReviewLinkStatus.ACTIVE)
                .build();
        reviewLinkRepository.save(link);

        return ReviewLinkResponse.of(link, rawToken);
    }

    /**
     * 토큰으로 섹션 초안 스냅샷을 읽기 전용으로 조회한다.
     *
     * <p>이미 제출한 브라우저면 {@code alreadySubmitted=true} 로 내려, 프론트가
     * "이미 검토를 제출했어요." 화면을 보여줄 수 있게 한다.
     */
    public ExternalReviewViewResponse getExternalView(String token, String anonymousReviewerId) {
        ReviewLink link = findLink(token);
        boolean alreadySubmitted = anonymousReviewerId != null
                && reviewSubmissionRepository
                .existsByReviewLink_IdAndAnonymousReviewerId(link.getId(), anonymousReviewerId);
        return ExternalReviewViewResponse.of(link, alreadySubmitted);
    }

    /**
     * 외부 검토자의 이해도 제출을 저장한다.
     *
     * <p>링크 행에 쓰기 락을 걸어 같은 링크의 동시 제출을 직렬화한 뒤,
     * (1) 링크 상태 → (2) 브라우저당 1회 → (3) 20개 상한 순으로 검사한다.
     */
    @Transactional
    public ReviewSubmissionResponse submitExternalReview(String token, String anonymousReviewerId,
                                                         ExternalReviewSubmitRequest request) {
        ReviewLink link = findLinkForUpdate(token);
        requireSubmittable(link);

        if (reviewSubmissionRepository
                .existsByReviewLink_IdAndAnonymousReviewerId(link.getId(), anonymousReviewerId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_SUBMITTED);
        }
        if (reviewSubmissionRepository.countByReviewLink_Id(link.getId()) >= ReviewLink.MAX_SUBMISSIONS) {
            throw new BusinessException(ErrorCode.REVIEW_SUBMISSION_LIMIT_EXCEEDED);
        }

        ReviewSubmission submission = ReviewSubmission.builder()
                .reviewLink(link)
                .anonymousReviewerId(anonymousReviewerId)
                .understandingSignal(request.understandingSignal())
                .reviewerName(request.reviewerName())
                .summary(request.summary())
                .build();
        try {
            reviewSubmissionRepository.saveAndFlush(submission);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_SUBMITTED);
        }

        return ReviewSubmissionResponse.from(submission);
    }

    /**
     * 외부 검토 링크 상태를 변경한다. (팀장 전용)
     *
     * <p>현재 팀장이 지정할 수 있는 전이는 {@code CLOSED}(직접 비활성화) 뿐이다.
     * 그 외 값은 잘못된 요청으로 처리한다.
     */
    @Transactional
    public void updateStatus(Long reviewLinkId, Long userId, ReviewLinkStatus targetStatus) {
        if (targetStatus != ReviewLinkStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    List.of(new FieldError("status", "CLOSED 로만 변경할 수 있습니다.")));
        }
        ReviewLink link = reviewLinkRepository.findById(reviewLinkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_LINK_NOT_FOUND));
        sectionAccessGuard.requireOwnedSection(link.getProjectSection().getId(), userId);
        link.close();
    }

    /**
     * 섹션 본문이 수정될 때, 해당 섹션의 <b>활성화된 링크를 모두 만료</b> 시킨다.
     *
     * <p>본문 저장 플로우(초안 저장 API)가 <b>첫 실제 저장 시점</b>에 호출해야 한다.
     * (편집창 열기만 한 경우는 호출하지 않는다.)
     */
    @Transactional
    public void markSectionLinksOutdated(Long sectionId) {
        List<ReviewLink> activeLinks = reviewLinkRepository
                .findByProjectSection_IdAndStatus(sectionId, ReviewLinkStatus.ACTIVE);
        activeLinks.forEach(ReviewLink::markOutdated);
    }

    private void requireSubmittable(ReviewLink link) {
        switch (link.getStatus()) {
            case OUTDATED -> throw new BusinessException(ErrorCode.REVIEW_LINK_OUTDATED);
            case CLOSED -> throw new BusinessException(ErrorCode.REVIEW_LINK_CLOSED);
            default -> { } //ACTIVE -> 제출 가능
        }
    }

    private ReviewLink findLink(String token) {
        return reviewLinkRepository.findByTokenHash(tokenHasher.hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_LINK_NOT_FOUND));
    }

    private ReviewLink findLinkForUpdate(String token) {
        return reviewLinkRepository.findByTokenHashForUpdate(tokenHasher.hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_LINK_NOT_FOUND));
    }
}
