package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.service.ReviewLinkService;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.domain.SectionDraftSource;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 검증된 rewrite append와 review 무효화·lease 해제·CURRENT 유지를 원자 반영한다. */
@Service
public class AiRewriteDraftWriter {

    private final ProjectSectionRepository projectSectionRepository;
    private final SectionDraftRepository sectionDraftRepository;
    private final DraftLeaseService draftLeaseService;
    private final ReviewLinkService reviewLinkService;
    private final TeamReviewService teamReviewService;
    private final UserService userService;
    private final SectionDriftService sectionDriftService;

    public AiRewriteDraftWriter(
            ProjectSectionRepository projectSectionRepository,
            SectionDraftRepository sectionDraftRepository,
            DraftLeaseService draftLeaseService,
            ReviewLinkService reviewLinkService,
            TeamReviewService teamReviewService,
            UserService userService,
            SectionDriftService sectionDriftService
    ) {
        this.projectSectionRepository = projectSectionRepository;
        this.sectionDraftRepository = sectionDraftRepository;
        this.draftLeaseService = draftLeaseService;
        this.reviewLinkService = reviewLinkService;
        this.teamReviewService = teamReviewService;
        this.userService = userService;
        this.sectionDriftService = sectionDriftService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AiRewriteDraftCreateResult append(AiRewriteDraftCreateCommand command) {
        validate(command);
        ProjectSection section = projectSectionRepository.findByIdForUpdate(command.sectionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
        if (!section.getStatus().allowsDraftEditing()) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }

        SectionDraft latest = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(command.sectionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND));
        if (latest.getVersion() != command.expectedBaseVersion()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        draftLeaseService.requireActiveHolder(command.sectionId(), command.actorUserId());

        SectionDraft draft = SectionDraft.builder()
                .projectSection(section)
                .content(command.content())
                .version(latest.getVersion() + 1)
                .lastEditor(userService.getUserReference(command.actorUserId()))
                .source(SectionDraftSource.AI_REWRITE_APPLIED)
                .build();
        try {
            sectionDraftRepository.saveAndFlush(draft);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        // rewrite 자체가 새 본문에 대한 검토 결과이므로 AI overlay만 CURRENT로 재바인딩한다.
        section.bindCurrentAiCheck();
        reviewLinkService.markSectionLinksOutdated(command.sectionId());
        teamReviewService.markSectionTeamReviewsOutdated(command.sectionId());
        var driftedSections = sectionDriftService.propagateConfirmedContentChange(
                section, command.actorUserId(), draft.getVersion());
        draftLeaseService.releaseHeldBy(command.sectionId(), command.actorUserId());
        return new AiRewriteDraftCreateResult(
                draft.getId(), draft.getVersion(), section.getStatus(), driftedSections);
    }

    private void validate(AiRewriteDraftCreateCommand command) {
        if (command == null
                || command.sectionId() == null
                || command.actorUserId() == null
                || command.expectedBaseVersion() <= 0
                || command.content() == null
                || command.content().isBlank()
                || command.content().length() > SectionDraftSaveRequest.MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("AI rewrite draft 명령이 유효하지 않습니다.");
        }
    }
}
