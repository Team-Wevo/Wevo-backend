package com.wevo.backend.review.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.review.domain.TeamReview;
import com.wevo.backend.review.domain.TeamReviewStatus;
import com.wevo.backend.review.dto.request.TeamReviewSubmitRequest;
import com.wevo.backend.review.dto.response.TeamReviewItemResponse;
import com.wevo.backend.review.dto.response.TeamReviewStatusResponse;
import com.wevo.backend.review.repository.TeamReviewRepository;
import com.wevo.backend.review.service.SectionAccessGuard.SectionMembership;
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
     */
    public TeamReviewStatusResponse getStatus(Long sectionId, Long userId) {
        SectionMembership membership = sectionAccessGuard.requireSectionMembership(sectionId, userId);
        Long projectId = membership.section().getProject().getId();

        Map<Long, TeamReview> reviewByReviewer = teamReviewRepository.findByProjectSection_Id(sectionId).stream()
                .collect(Collectors.toMap(review -> review.getReviewer().getId(), Function.identity()));

        List<TeamReviewItemResponse> items = projectMemberRepository
                .findAllWithUserByProjectIdAndRole(projectId, ProjectMemberRole.MEMBER).stream()
                .map(ProjectMember::getUser)
                .map(member -> toItem(member, reviewByReviewer.get(member.getId())))
                .toList();

        return TeamReviewStatusResponse.from(items);
    }

    /**
     * 내 팀 검토를 제출한다. (팀원 전용·1인 1검토)
     */
    @Transactional
    public TeamReviewItemResponse submitMyReview(Long sectionId, Long userId, TeamReviewSubmitRequest request) {
        SectionMembership membership = sectionAccessGuard.requireSectionMembership(sectionId, userId);
        if (membership.member().getRole() != ProjectMemberRole.MEMBER) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // 팀장(OWNER)은 검토 제출 불가
        }

        //REVIEWING 상태가 아니면 팀 검토 진행 불가능
        ProjectSection section = membership.section();
        if (section.getStatus() != ProjectSectionStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.TEAM_REVIEW_SECTION_NOT_REVIEWING);
        }

        Integer reviewedVersion = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .map(SectionDraft::getVersion)
                .orElse(null);

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

        return TeamReviewItemResponse.of(review, membership.member().getUser());
    }

    private TeamReviewItemResponse toItem(User member, TeamReview review) {
        return review == null
                ? TeamReviewItemResponse.pending(member)
                : TeamReviewItemResponse.of(review, member);
    }
}
