package com.wevo.backend.review.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.dto.request.ExternalReviewSubmitRequest;
import com.wevo.backend.review.dto.request.ReviewLinkIssueRequest;
import com.wevo.backend.review.dto.response.ExternalReviewViewResponse;
import com.wevo.backend.review.dto.response.ReviewLinkResponse;
import com.wevo.backend.review.dto.response.ReviewSubmissionResponse;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.section.service.SectionAuthorIntentQueryService;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li><b>버전 만료·비활성화</b> — OUTDATED/CLOSED 링크는 열람·제출을 거부한다.</li>
 *   <li><b>유효 기간</b> — 발급 시 지정한 마지막 날(KST)이 지나면 EXPIRED 로 열람·제출을 거부한다.</li>
 *   <li><b>토큰 해시 저장</b> — 원문 대신 해시만 저장하고 요청 토큰을 해시해 비교한다.</li>
 * </ul>
 *
 * <h2>유효 기간 판정 (배치 없음)</h2>
 * <p>기간 만료 판정 기준은 "지금 날짜 vs {@code expiresOn}"({@link ReviewLink#isPastDue})이고, 저장된 {@code status} 는 링크를
 * 만지는 시점(재발급·본문 수정·수동 종료)에 {@link ReviewLink#expireIfPastDue} 로 따라온다.
 * 정리가 늦어도 열람·제출·조회는 날짜로 판정하므로, 기간이 지난 링크가 열리거나 제출을 받는
 * 구간은 없다. 열람과 제출은 {@code requireUsable} 이라는 <b>같은 검사</b>를 지나므로
 * 같은 링크·같은 사유에는 항상 같은 실패 코드가 나간다.</p>
 *
 * <p>팀장(OWNER) 권한 검증은 SectionAccessGuard 에 위임한다. 단, 링크 ID 를 진입점으로 받는
 * 경로({@link #updateStatus})는 섹션이 아니라 <b>링크</b>의 존재를 숨겨야 하므로
 * {@link ProjectAccessGuard#hidingNonMember} 로 숨김 코드를 직접 지정한다.
 */
@Service
@Transactional(readOnly = true)
public class ReviewLinkService {

    private static final Logger log = LoggerFactory.getLogger(ReviewLinkService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final SectionDraftRepository sectionDraftRepository;
    private final ReviewLinkRepository reviewLinkRepository;
    private final ReviewSubmissionRepository reviewSubmissionRepository;
    private final UserRepository userRepository;
    private final ReviewTokenHasher tokenHasher;
    private final SectionAuthorIntentQueryService authorIntentQueryService;
    private final ReviewIntentComparisonCoordinator comparisonCoordinator;

    public ReviewLinkService(SectionAccessGuard sectionAccessGuard,
                             ProjectAccessGuard projectAccessGuard,
                             SectionDraftRepository sectionDraftRepository,
                             ReviewLinkRepository reviewLinkRepository,
                             ReviewSubmissionRepository reviewSubmissionRepository,
                             UserRepository userRepository,
                             ReviewTokenHasher tokenHasher,
                             SectionAuthorIntentQueryService authorIntentQueryService,
                             ReviewIntentComparisonCoordinator comparisonCoordinator) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.sectionDraftRepository = sectionDraftRepository;
        this.reviewLinkRepository = reviewLinkRepository;
        this.reviewSubmissionRepository = reviewSubmissionRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
        this.authorIntentQueryService = authorIntentQueryService;
        this.comparisonCoordinator = comparisonCoordinator;
    }

    /**
     * 외부 검토 링크를 발급한다. (팀장 전용 — <b>대체 발급</b>)
     *
     * <p>링크는 발급 시점의 <b>최신 초안 버전에 고정</b>된다 — 제목·본문·버전 스냅샷은 발급 이후
     * 불변이며, 이후 본문이 수정돼도 이 링크는 스냅샷을 그대로 보여준다. 새 본문에 대한 외부 검토는
     * 재발급으로만 가능하다.
     * 원문 토큰은 저장하지 않고(해시만 저장) 응답으로만 한 번 반환한다.
     *
     * <p>섹션당 {@code ACTIVE} 링크는 <b>최대 1개</b> — 재발급하면 같은 트랜잭션에서 기존
     * {@code ACTIVE} 링크를 {@code CLOSED}로 닫고 새 토큰을 발급한다. 토큰이 해시로만 저장되어
     * 원문 재반환이 불가능하므로 멱등 재사용이 아니라 대체 발급이다. 기존 링크로 들어온 제출
     * 결과는 보존된다.
     *
     * <p><b>유효 기간</b>({@code expiresOn}, 선택) — 링크가 살아 있는 마지막 날(KST)이다. 지정하면
     * 그 날 끝까지만 제출을 받고, 이후 제출은 {@code R013} 으로 거부한다.
     * <b>발급일보다 최소 1일 뒤</b>여야 하며, 당일·과거
     * 날짜는 저장 상태와 무관한 요청 내용의 오류이므로 422 {@code C002} 로 거부한다
     * (CLAUDE.md §5.6). 생략하면 기간 제한 없이 발급된다. 발급 후 기간을 바꾸려면 재발급한다.
     */
    @Transactional
    public ReviewLinkResponse issueExternalLink(Long sectionId, Long userId,
                                                ReviewLinkIssueRequest request) {

        ProjectSection section = sectionAccessGuard.requireOwnedSectionForUpdate(sectionId, userId);
        User createdBy = userRepository.getReferenceById(userId);
        LocalDate today = LocalDate.now(KST);
        LocalDate expiresOn = request == null ? null : request.expiresOn();
        requireValidExpiry(expiresOn, today);

        // 초안 없으면 발급 거부
        SectionDraft latestDraft = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(section.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_LINK_DRAFT_REQUIRED));
        SectionAuthorIntent authorIntent = authorIntentQueryService
                .findConfirmed(section.getId(), latestDraft.getVersion())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REVIEW_LINK_AUTHOR_INTENT_REQUIRED));

        // 대체 발급 — 기존 ACTIVE 링크를 닫는다 (섹션 행 잠금을 잡고 있어 발급·만료와 직렬화됨)
        // 유효 기간이 이미 지난 링크는 CLOSED 가 아니라 EXPIRED 로 정리해 종결 사유를 보존한다.
        List<ReviewLink> previousActive = reviewLinkRepository
                .findByProjectSection_IdAndStatus(sectionId, ReviewLinkStatus.ACTIVE);
        previousActive.forEach(previous -> {
            if (!previous.expireIfPastDue(today)) {
                previous.close(today);
            }
        });
        // 기존 ACTIVE→CLOSED UPDATE 를 새 ACTIVE INSERT 전에 DB 에 반영한다.
        // IDENTITY 전략은 save() 시점에 즉시 INSERT 하므로, 이 flush 가 없으면 새 행이 먼저 들어가
        // 부분 유니크 인덱스(uk_review_links_active_per_section)를 위반한다.
        if (!previousActive.isEmpty()) {
            reviewLinkRepository.flush();
        }

        String rawToken = tokenHasher.generateRawToken();
        ReviewLink link = ReviewLink.builder()
                .projectSection(section)
                .createdBy(createdBy)
                .tokenHash(tokenHasher.hash(rawToken))
                .sectionTitleSnapshot(section.getTitle())
                .contentSnapshot(latestDraft.getContent())
                .contentVersion(latestDraft.getVersion())
                .authorIntentSnapshot(authorIntent.getConfirmedIntent())
                .authorIntent(authorIntent)
                .status(ReviewLinkStatus.ACTIVE)
                .expiresOn(expiresOn)
                .build();
        try {
            reviewLinkRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException e) {
            // 부분 유니크 인덱스(uk_review_links_active_per_section) 위반 — 섹션 행 잠금으로 발급을
            // 직렬화하므로 정상 경로에서는 발생하지 않지만, 무결성 보장을 사전 검사에만 맡기지 않는다.
            // (CLAUDE.md §5.8) 재시도하면 성공하는 경합이라 원인 분기가 필요 없는 일반 충돌로 변환한다.
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        return ReviewLinkResponse.of(link, rawToken);
    }

    /**
     * 토큰으로 섹션 초안 스냅샷을 읽기 전용으로 조회한다.
     *
     * <p><b>살아 있는 링크만 열람할 수 있다</b> — 만료·종료된 링크는 제출과 <b>동일한 검사</b>
     * ({@link #requireUsable})로 거절한다. 본문 스냅샷은 팀 내부 문서이므로, 제출을 받지 않는
     * 링크로는 내용도 보여주지 않는다. 검토자는 열람 단계에서 사유({@code R004}/{@code R013}/
     * {@code R005})를 받아 바로 안내 화면을 볼 수 있다.
     *
     * <p>이미 제출한 브라우저면 {@code alreadySubmitted=true} 로 내려, 프론트가
     * "이미 검토를 제출했어요." 화면을 보여줄 수 있게 한다.
     */
    public ExternalReviewViewResponse getExternalView(String token, String anonymousReviewerId) {
        ReviewLink link = findLink(token);
        // 조회는 쓰기가 없는 경로라 상태를 정리하지 않고, 날짜 기준으로만 판정한다.
        LocalDate today = LocalDate.now(KST);
        requireUsable(link, today);

        boolean alreadySubmitted = anonymousReviewerId != null
                && reviewSubmissionRepository
                .existsByReviewLink_IdAndAnonymousReviewerId(link.getId(), anonymousReviewerId);
        return ExternalReviewViewResponse.of(link, alreadySubmitted, today);
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
        requireUsable(link, LocalDate.now(KST));

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
                .reviewerComment(request.reviewerComment())
                .build();
        try {
            reviewSubmissionRepository.saveAndFlush(submission);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_SUBMITTED);
        }

        try {
            comparisonCoordinator.prepare(submission);
        } catch (RuntimeException exception) {
            // 비교 준비 실패가 정상 저장된 공개 제출을 롤백하지 않도록 마지막 방어선으로 격리한다.
            // 원문·summary가 예외 메시지에 섞일 수 있으므로 타입만 기록한다.
            log.warn("검토 의도 비교 준비 실패 submissionId={}, exceptionType={}",
                    submission.getId(), exception.getClass().getSimpleName());
        }

        return ReviewSubmissionResponse.from(submission);
    }

    /**
     * 외부 검토 링크를 팀장이 수동으로 종료({@code CLOSED})한다.
     *
     * <p>검토 링크 종료의 <b>기본 경로는 서버 자동 처리</b>다 — 본문 수정 시
     * {@link #markSectionLinksOutdated}로 {@code OUTDATED} 되고, 재발급 시 기존 {@code ACTIVE}
     * 링크가 {@link #issueExternalLink}에서 {@code CLOSED} 된다. 이 경로는 자동 처리로 덮이지 않는
     * 상황(링크 오발송·유출 등 지금 당장 수집을 끊어야 하는 경우)을 위한 보조 수단이며,
     * {@code CLOSED} 로만 전이할 수 있고 OWNER 권한을 검증한다.
     *
     * <p><b>ACTIVE 링크만 종료된다</b> — 이미 끝난 링크는 상태를 그대로 둔 채 사유를 구분해 거절한다
     * ({@link ReviewLink#close()}): 이미 종료된 링크는 409 {@code R011} "이미 종료된 링크입니다.",
     * 본문 수정으로 만료된 링크는 409 {@code R012} "이미 만료된 링크입니다.". 조용히 성공시키면
     * 팀장이 실제 종료 사유를 오인하므로, 두 사유를 각각 다른 코드로 알려 화면에서 분기하게 한다.
     * 외부 검토자의 제출 거절 문구(R004/R005)는 안내 대상이 달라 그대로 둔다.
     *
     * <p><b>잠금</b> — 링크 행을 배타 잠금으로 읽어 같은 행을 잠그는 제출
     * ({@link #submitExternalReview})과 직렬화한다. 종료 커밋 전에 잠금을 잡은 제출은 그대로
     * 성공하고, 그 뒤의 제출은 {@code CLOSED} 를 보고 거부되므로 "닫는 도중에 한 건 더 들어오는"
     * 경합이 없다. 잠금을 먼저 잡고 권한을 검사하는 순서인데, 권한 검사는 잠금을 잡지 않는
     * 조회라 발급 경로(섹션 행 잠금 → 링크 행 갱신)와 교착 사이클을 만들지 않는다.
     * 권한 없는 호출자가 잠금을 잡는 구간은 인가 조회 한 번 길이이고, 실패 시 롤백으로 즉시 풀린다.
     *
     * <p><b>존재 숨김</b> — 이 API 는 링크 ID 가 진입점이므로 비멤버에게는 <b>링크</b>의 존재를
     * 숨긴다({@code R001}). 섹션 기준 숨김 코드({@code S001})를 쓰면 "없는 링크 → R001 /
     * 남의 링크 → S001" 로 갈려 ID 를 훑는 것만으로 링크 실재 여부가 드러나기 때문이다.
     * 멤버지만 OWNER 가 아닌 경우는 숨길 이유가 없어 {@code 403}({@code A002}) 그대로다.
     * (CLAUDE.md §5.6)
     */
    @Transactional
    public void updateStatus(Long reviewLinkId, Long userId, ReviewLinkStatus targetStatus) {
        if (targetStatus != ReviewLinkStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    List.of(new FieldError("status", "CLOSED 로만 변경할 수 있습니다.")));
        }
        ReviewLink link = reviewLinkRepository.findByIdForUpdate(reviewLinkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_LINK_NOT_FOUND));
        // 섹션 존재는 FK 로 보장되므로 SectionAccessGuard 의 섹션 조회를 거치지 않고
        // 링크가 속한 프로젝트의 OWNER 검사만 수행하며, 비멤버는 R001 로 숨긴다.
        ProjectAccessGuard.hidingNonMember(ErrorCode.REVIEW_LINK_NOT_FOUND,
                () -> projectAccessGuard.requireOwner(
                        link.getProjectSection().getProject().getId(), userId));
        // 유효 기간이 지난 링크는 이미 끝난 링크다 — close() 가 날짜로 판정해 R014 로 거절한다.
        link.close(LocalDate.now(KST));
    }

    /**
     * 섹션 본문이 수정될 때, 해당 섹션의 <b>활성화된 링크를 모두 만료</b> 시킨다.
     *
     * <p>본문 저장 플로우가 <b>첫 실제 저장 시점</b>에 호출해야 한다.
     * (편집창 열기만 한 경우는 호출하지 않는다.)
     *
     * <p><b>호출 계약</b> — 본문 저장 트랜잭션 안에서, 섹션 행 배타 잠금
     * ({@code ProjectSectionRepository#findByIdForUpdate})을 잡은 상태로 호출해야 한다.
     * 링크 발급({@link #issueExternalLink})이 같은 잠금을 잡으므로, 이 규약을 지키면
     * 발급 시점 버전 고정 계약(ACTIVE 링크의 스냅샷 = 최신 본문 버전)이 경합 없이 유지된다.
     *
     * <p><b>잠금</b> — 만료 대상을 <b>링크 행 배타 잠금</b>으로 읽는다. 섹션 잠금만으로는
     * 수동 종료({@link #updateStatus})와 직렬화되지 않기 때문이다 — 수동 종료는 링크 ID 로 진입해
     * 링크 행만 잠그므로 섹션 잠금을 거치지 않는다. 잠금 없이 읽으면 아직 커밋되지 않은 종료를
     * 못 보고 {@code ACTIVE} 로 읽어, 방금 커밋된 {@code CLOSED} 를 {@code OUTDATED} 로 덮어쓴다
     * (변경 손실). 잠금 순서는 저장·발급 모두 {@code 섹션 → 링크} 이고 수동 종료는 링크만 잡으므로
     * 교착 사이클이 없다.
     */
    @Transactional
    public void markSectionLinksOutdated(Long sectionId) {
        List<ReviewLink> activeLinks = reviewLinkRepository
                .findByProjectSection_IdAndStatusForUpdate(sectionId, ReviewLinkStatus.ACTIVE);
        LocalDate today = LocalDate.now(KST);
        // 유효 기간이 먼저 지난 링크는 본문 수정과 무관하게 이미 끝난 링크다 — 종결 사유를
        // OUTDATED 로 덮어쓰지 않고 EXPIRED 로 정리한다.
        activeLinks.forEach(link -> {
            if (!link.expireIfPastDue(today)) {
                link.markOutdated();
            }
        });
    }

    /**
     * <b>외부 검토자가 이 링크를 쓸 수 있는지</b> 검사한다. 열람({@link #getExternalView})과
     * 제출({@link #submitExternalReview})이 공유하는 단일 관문이다.
     *
     * <p>두 경로가 같은 검사를 쓰는 이유는 <b>같은 링크에 같은 사유로 접근했을 때 항상 같은 코드가
     * 나가야</b> 하기 때문이다. 검사가 갈리면 "열람은 되는데 제출만 막히는" 상태가 생겨,
     * 검토자가 본문을 다 읽고 나서야 거절당한다.
     *
     * <p>거절 사유(본문 수정 만료·유효 기간 만료·종료)는 검토자에게 보여줄 안내 문구가 각각 다르므로
     * 코드를 나눠 던진다. 유효 기간은 저장된 상태가 아직 {@code ACTIVE} 여도 날짜로 판정한다
     * ({@link ReviewLink#statusAsOf}) — 상태 정리가 늦어도 기간이 지난 링크는 열람·제출을 받지 않는다.
     *
     * @throws BusinessException 본문 수정 만료면 {@link ErrorCode#REVIEW_LINK_OUTDATED}({@code R004}),
     *                           유효 기간 만료면 {@link ErrorCode#REVIEW_LINK_EXPIRED}({@code R013}),
     *                           종료된 링크면 {@link ErrorCode#REVIEW_LINK_ALREADY_CLOSED}({@code R005})
     */
    private void requireUsable(ReviewLink link, LocalDate today) {
        switch (link.statusAsOf(today)) {
            case OUTDATED -> throw new BusinessException(ErrorCode.REVIEW_LINK_OUTDATED);
            case EXPIRED -> throw new BusinessException(ErrorCode.REVIEW_LINK_EXPIRED);
            case CLOSED -> throw new BusinessException(ErrorCode.REVIEW_LINK_ALREADY_CLOSED);
            default -> { } //ACTIVE -> 열람·제출 가능
        }
    }

    /**
     * 발급 요청의 유효 기간을 검사한다. (선택 입력이라 미지정은 그대로 통과 — 기간 제한 없음)
     *
     * <p>발급 당일·과거 날짜는 하루도 열려 있지 않은 링크라 요청 자체가 성립하지 않는다.
     * 저장된 리소스 상태와 무관한 요청 내용의 의미적 오류이므로 422({@code C002})로 거부하고,
     * 화면에서 입력 필드를 짚을 수 있게 필드 사유를 함께 담는다. (CLAUDE.md §5.6, §5.9)
     */
    private void requireValidExpiry(LocalDate expiresOn, LocalDate today) {
        if (expiresOn != null && !expiresOn.isAfter(today)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    List.of(new FieldError("expiresOn", "유효 기간은 발급일보다 최소 1일 뒤여야 합니다.")));
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
