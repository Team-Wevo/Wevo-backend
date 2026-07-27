package com.wevo.backend.issue.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 현재 정리 세트의 쟁점을 변경하기 위한 공통 잠금·인가 경계.
 *
 * <p>호출 서비스의 쓰기 트랜잭션 안에서 쟁점 행을 먼저 잠그고 섹션 행을 이어서 잠근다.
 * issue 기반 API의 비멤버 접근은 {@code I001}로 숨긴다.
 */
@Component
public class IssueCommandAccessGuard {

    private final IssueRepository issueRepository;
    private final CurrentSynthesisSetResolver currentSynthesisSetResolver;
    private final SectionAccessGuard sectionAccessGuard;

    public IssueCommandAccessGuard(
            IssueRepository issueRepository,
            CurrentSynthesisSetResolver currentSynthesisSetResolver,
            SectionAccessGuard sectionAccessGuard
    ) {
        this.issueRepository = issueRepository;
        this.currentSynthesisSetResolver = currentSynthesisSetResolver;
        this.sectionAccessGuard = sectionAccessGuard;
    }

    /**
     * OWNER가 변경할 수 있는 현재 세트의 쟁점을 잠근 상태로 반환한다.
     */
    public Issue requireCurrentIssueForOwner(Long issueId, Long actorUserId) {
        Issue issue = issueRepository.findByIdForUpdate(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        SynthesisSet issueSet = requireSynthesisSet(issue);

        requireOwner(issueSet.getProjectSectionId(), actorUserId);
        requireCurrentSet(issueSet);
        return issue;
    }

    /**
     * 프로젝트 참여자가 변경할 수 있는 현재 세트의 쟁점을 잠근 상태로 반환한다.
     */
    public Issue requireCurrentIssueForParticipant(Long issueId, Long actorUserId) {
        Issue issue = issueRepository.findByIdForUpdate(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        SynthesisSet issueSet = requireSynthesisSet(issue);

        requireParticipant(issueSet.getProjectSectionId(), actorUserId);
        requireCurrentSet(issueSet);
        return issue;
    }

    private SynthesisSet requireSynthesisSet(Issue issue) {
        if (issue.getSynthesisSet() == null
                || issue.getSynthesisSet().getId() == null
                || issue.getSynthesisSet().getProjectSectionId() == null) {
            throw new IllegalStateException("쟁점의 정리 세트 참조가 유효하지 않습니다.");
        }
        return issue.getSynthesisSet();
    }

    private void requireOwner(Long sectionId, Long actorUserId) {
        try {
            sectionAccessGuard.requireOwnedSectionForUpdate(sectionId, actorUserId);
        } catch (BusinessException exception) {
            hideSectionNotFound(exception);
        }
    }

    private void requireParticipant(Long sectionId, Long actorUserId) {
        try {
            sectionAccessGuard.requireParticipantSectionForUpdate(sectionId, actorUserId);
        } catch (BusinessException exception) {
            hideSectionNotFound(exception);
        }
    }

    private void hideSectionNotFound(BusinessException exception) {
        if (exception.getErrorCode() == ErrorCode.SECTION_NOT_FOUND) {
            throw new BusinessException(ErrorCode.ISSUE_NOT_FOUND);
        }
        throw exception;
    }

    private void requireCurrentSet(SynthesisSet issueSet) {
        CurrentSynthesisSetReference current = currentSynthesisSetResolver
                .findCurrent(issueSet.getProjectSectionId())
                .orElseThrow(() -> new IllegalStateException(
                        "쟁점이 속한 섹션의 현재 정리 세트가 없습니다."));
        if (!Objects.equals(current.synthesisSetId(), issueSet.getId())
                || !Objects.equals(current.requestId(), issueSet.getRequestId())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }
}
