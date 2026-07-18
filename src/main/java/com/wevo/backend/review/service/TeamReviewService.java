package com.wevo.backend.review.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.domain.TeamReview;
import com.wevo.backend.review.domain.TeamReviewStatus;
import com.wevo.backend.review.dto.request.TeamReviewSubmitRequest;
import com.wevo.backend.review.dto.response.TeamReviewItemResponse;
import com.wevo.backend.review.dto.response.TeamReviewStatusResponse;
import com.wevo.backend.review.repository.TeamReviewRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀(내부) 검토 로직. (제품 정책서 §6.1)
 *
 * <ul>
 *   <li><b>현황 조회</b> — 프로젝트 멤버가 동의율 과 팀원별 상태를 본다.</li>
 *   <li><b>내 검토 제출</b> — 팀원(MEMBER)만 가능. 팀장(OWNER)은 검토자가 아니다(403).</li>
 *   <li>미제출 팀원은 행을 만들지 않고 {@code PENDING} 으로 파생 노출한다.</li>
 *   <li>동의율 분모 M = 팀원(MEMBER) 수. 팀장은 집계에서 제외한다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TeamReviewService {

    private final SectionAccessGuard sectionAccessGuard;
    private final TeamReviewRepository teamReviewRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final SectionDraftRepository sectionDraftRepository;
    private final UserRepository userRepository;

    public TeamReviewService(SectionAccessGuard sectionAccessGuard,
                             TeamReviewRepository teamReviewRepository,
                             ProjectMemberRepository projectMemberRepository,
                             SectionDraftRepository sectionDraftRepository,
                             UserRepository userRepository) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.teamReviewRepository = teamReviewRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.sectionDraftRepository = sectionDraftRepository;
        this.userRepository = userRepository;
    }

    /**
     * 섹션의 팀 검토 현황을 조회한다. (역할 무관)
     *
     * <p>목록은 <b>조회 시점의 프로젝트 멤버 기준</b>으로 구성한다 — 검토 시작 후 합류한 팀원도
     * {@code PENDING}으로 파생 포함된다. {@code currentContentVersion}은 검토 제출(§3.5.7)의
     * {@code contentVersion} 값으로 그대로 사용된다.
     */
    public TeamReviewStatusResponse getStatus(Long sectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireParticipantSection(sectionId, userId);
        Long projectId = section.getProject().getId();

        Map<Long, TeamReview> reviewByReviewer = teamReviewRepository.findByProjectSection_Id(sectionId).stream()
                .collect(Collectors.toMap(review -> review.getReviewer().getId(), Function.identity()));

        List<TeamReviewItemResponse> items = projectMemberRepository
                .findAllWithUserByProjectIdAndRole(projectId, ProjectMemberRole.MEMBER).stream()
                .map(ProjectMember::getUser)
                .map(member -> toItem(member, reviewByReviewer.get(member.getId())))
                .toList();

        Integer currentContentVersion = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .map(SectionDraft::getVersion)
                .orElse(null);

        return TeamReviewStatusResponse.from(items, currentContentVersion);
    }

    /**
     * 내 팀 검토를 제출한다. (팀원 전용)
     */
    @Transactional
    public TeamReviewItemResponse submitMyReview(Long sectionId, Long userId, TeamReviewSubmitRequest request) {
        // 제출 가능한 상태는 APPROVED/CHANGES_REQUESTED
        // (PENDING 은 "미제출"의 파생 상태라 저장되면 응답 계약과 집계 의미가 깨진다.)
        if (request.status() != TeamReviewStatus.APPROVED
                && request.status() != TeamReviewStatus.CHANGES_REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    List.of(new FieldError("status", "APPROVED 또는 CHANGES_REQUESTED 만 제출할 수 있습니다.")));
        }

        // 팀원(MEMBER)만 제출 가능 — 팀장(OWNER)은 FORBIDDEN
        // 섹션 행을 배타 잠금으로 조회해 같은 (섹션, 팀원)의 동시 최초 제출을 직렬화한다.
        // (둘 다 review == null 을 보고 insert 를 시도하면 한쪽이 유니크 제약 위반으로 실패해
        //  "재제출 시 기존 검토 갱신" 업서트 계약이 깨지는 경합을 막는다.)
        ProjectSection section = sectionAccessGuard.requireMemberSectionForUpdate(sectionId, userId);

        //REVIEWING 상태가 아니면 팀 검토 진행 불가능
        if (section.getStatus() != ProjectSectionStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.TEAM_REVIEW_SECTION_NOT_REVIEWING);
        }

        // REVIEWING 섹션은 초안 확정을 거쳐 도달하므로 초안이 항상 존재해야 한다.
        // 없다면 비정상 상태 — 어느 버전을 검토했는지(reviewedContentVersion) 알 수 없는
        // 검토가 저장되면 만료(outdated)·재검토 판단 근거가 깨지므로 제출을 거부한다.
        Integer reviewedVersion = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .map(SectionDraft::getVersion)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT));

        // contentVersion 바인딩 (§6.1 "검토 대상은 최신 본문") — 검토 화면이 읽은 버전과 서버의
        // 현재 버전이 다르면, 읽는 사이 바뀐 본문에 대한 검토가 새 버전에 기록되는 것을 막는다.
        if (!reviewedVersion.equals(request.contentVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    List.of(new FieldError("contentVersion",
                            "본문이 수정되었습니다. 다시 읽고 검토해주세요.")));
        }

        // 수정 요청이면 사유 필수
        String reason = request.status() == TeamReviewStatus.CHANGES_REQUESTED
                ? request.changeRequestReason()
                : null;

        TeamReview review = teamReviewRepository
                .findByProjectSection_IdAndReviewer_Id(sectionId, userId)
                .orElse(null);
        if (review == null) {
            review = TeamReview.builder()
                    .projectSection(section)
                    .reviewer(userRepository.getReferenceById(userId))
                    .status(request.status())
                    .changeRequestReason(reason)
                    .reviewedContentVersion(reviewedVersion)
                    .build();
            teamReviewRepository.save(review);
        } else {
            review.apply(request.status(), reason, reviewedVersion);
        }

        return TeamReviewItemResponse.of(review, review.getReviewer());
    }

    /**
     * 팀장이 수정 요청을 해소(resolved) 처리한다. (§6.1.1 — 수정 안 하고 합의된 경우)
     *
     * <p>OWNER 전용이며, {@code CHANGES_REQUESTED} 상태의 검토에만 적용된다.
     */
    @Transactional
    public TeamReviewItemResponse resolveChangeRequest(Long sectionId, Long reviewId, Long userId, boolean resolved) {
        sectionAccessGuard.requireOwnedSection(sectionId, userId); // 팀장(OWNER)만

        TeamReview review = teamReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_REVIEW_NOT_FOUND));
        if (!review.getProjectSection().getId().equals(sectionId)) {
            throw new BusinessException(ErrorCode.TEAM_REVIEW_NOT_FOUND); // 해당 섹션의 검토가 아님
        }
        if (review.getStatus() != TeamReviewStatus.CHANGES_REQUESTED) {
            throw new BusinessException(ErrorCode.TEAM_REVIEW_NOT_CHANGES_REQUESTED);
        }

        review.updateResolved(resolved);
        return TeamReviewItemResponse.of(review, review.getReviewer());
    }

    /**
     * 섹션 본문이 수정될 때, 해당 섹션의 팀 검토를 모두 <b>만료(OUTDATED)</b> 시킨다. (§6.1)
     *
     * <p>본문 저장(초안 수정) 플로우가 <b>첫 실제 저장 시점</b>에 호출해야 한다.
     * 외부 검토의 {@code ReviewLinkService#markSectionLinksOutdated} 와 대칭이며, 클라이언트가 직접 호출하는
     * 엔드포인트가 아니라 본문 수정의 부수효과다.
     *
     * <p><b>호출 계약</b> — 본문 저장 트랜잭션 안에서, 섹션 행 배타 잠금
     * ({@code ProjectSectionRepository#findByIdForUpdate})을 잡은 상태로 호출해야 한다.
     * 검토 제출({@link #submitMyReview})이 같은 잠금을 잡으므로, 이 규약을 지키면
     * "새 버전 저장 직전에 커밋된 검토가 만료 처리에서 빠지는" 경합이 생기지 않는다.
     */
    @Transactional
    public void markSectionTeamReviewsOutdated(Long sectionId) {
        teamReviewRepository.findByProjectSection_Id(sectionId).forEach(TeamReview::markOutdated);
    }

    private TeamReviewItemResponse toItem(User member, TeamReview review) {
        return review == null
                ? TeamReviewItemResponse.pending(member)
                : TeamReviewItemResponse.of(review, member);
    }
}
