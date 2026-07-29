package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.service.ReviewLinkService;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.domain.SectionDraftEvidence;
import com.wevo.backend.section.domain.SectionDraftEvidenceDecision;
import com.wevo.backend.section.domain.SectionDraftEvidenceGapAnswer;
import com.wevo.backend.section.domain.SectionDraftEvidenceOpinion;
import com.wevo.backend.section.domain.SectionDraftEvidencePrerequisite;
import com.wevo.backend.section.domain.SectionDraftSource;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceDecisionRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceGapAnswerRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceOpinionRepository;
import com.wevo.backend.section.repository.SectionDraftEvidencePrerequisiteRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.service.UserService;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 초안 append, 생성 근거, 검토 무효화와 상태 전이를 하나의 짧은 트랜잭션에 반영한다.
 */
@Service
public class AiSectionDraftWriter {

    private final ProjectSectionRepository projectSectionRepository;
    private final SectionDraftRepository sectionDraftRepository;
    private final SectionDraftEvidenceRepository evidenceRepository;
    private final SectionDraftEvidenceOpinionRepository opinionRepository;
    private final SectionDraftEvidenceDecisionRepository decisionRepository;
    private final SectionDraftEvidenceGapAnswerRepository gapAnswerRepository;
    private final SectionDraftEvidencePrerequisiteRepository prerequisiteRepository;
    private final ReviewLinkService reviewLinkService;
    private final TeamReviewService teamReviewService;
    private final SectionStatusService sectionStatusService;
    private final UserService userService;

    public AiSectionDraftWriter(
            ProjectSectionRepository projectSectionRepository,
            SectionDraftRepository sectionDraftRepository,
            SectionDraftEvidenceRepository evidenceRepository,
            SectionDraftEvidenceOpinionRepository opinionRepository,
            SectionDraftEvidenceDecisionRepository decisionRepository,
            SectionDraftEvidenceGapAnswerRepository gapAnswerRepository,
            SectionDraftEvidencePrerequisiteRepository prerequisiteRepository,
            ReviewLinkService reviewLinkService,
            TeamReviewService teamReviewService,
            SectionStatusService sectionStatusService,
            UserService userService
    ) {
        this.projectSectionRepository = projectSectionRepository;
        this.sectionDraftRepository = sectionDraftRepository;
        this.evidenceRepository = evidenceRepository;
        this.opinionRepository = opinionRepository;
        this.decisionRepository = decisionRepository;
        this.gapAnswerRepository = gapAnswerRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.reviewLinkService = reviewLinkService;
        this.teamReviewService = teamReviewService;
        this.sectionStatusService = sectionStatusService;
        this.userService = userService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createAiDraft(AiSectionDraftCreateCommand command) {
        validate(command);
        ProjectSection section = projectSectionRepository.findByIdForUpdate(command.sectionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
        if (section.getStatus() != ProjectSectionStatus.SYNTHESIZING) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
        if (section.isSynthesisStale()
                || section.getOpinionGateGeneration() != command.opinionGateGeneration()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        int latestVersion = sectionDraftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(command.sectionId())
                .map(SectionDraft::getVersion)
                .orElse(0);
        if (latestVersion != command.baseVersion()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        SectionDraft draft = SectionDraft.builder()
                .projectSection(section)
                .content(command.content())
                .version(latestVersion + 1)
                .lastEditor(userService.getUserReference(command.actorUserId()))
                .source(SectionDraftSource.AI_GENERATED)
                .build();
        try {
            sectionDraftRepository.saveAndFlush(draft);
            SectionDraftEvidence evidence = evidenceRepository.saveAndFlush(
                    SectionDraftEvidence.builder()
                            .sectionDraft(draft)
                            .synthesisSetId(command.synthesisSetId())
                            .opinionGateGeneration(command.opinionGateGeneration())
                            .generationRequestId(command.generationRequestId())
                            .inputSnapshotHash(command.inputSnapshotHash())
                            .sourceVersion(command.sourceVersion())
                            .consensusSummary(command.consensusSummary())
                            .build()
            );
            persistEvidence(evidence, command);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        if (latestVersion > 0) {
            section.markAiCheckOutdated();
            reviewLinkService.markSectionLinksOutdated(command.sectionId());
            teamReviewService.markSectionTeamReviewsOutdated(command.sectionId());
        }
        sectionStatusService.markDraftingAfterAi(
                command.sectionId(),
                command.actorUserId(),
                draft.getVersion()
        );
        return draft.getId();
    }

    private void persistEvidence(
            SectionDraftEvidence evidence,
            AiSectionDraftCreateCommand command
    ) {
        AtomicInteger order = new AtomicInteger();
        opinionRepository.saveAll(command.opinions().stream()
                .map(item -> SectionDraftEvidenceOpinion.builder()
                        .draftEvidence(evidence)
                        .opinionId(item.opinionId())
                        .authorNameSnapshot(item.authorName())
                        .content(item.content())
                        .sortOrder(order.incrementAndGet())
                        .build())
                .toList());
        order.set(0);
        decisionRepository.saveAll(command.decisions().stream()
                .map(item -> SectionDraftEvidenceDecision.builder()
                        .draftEvidence(evidence)
                        .issueId(item.issueId())
                        .decisionId(item.decisionId())
                        .question(item.question())
                        .decision(item.decision())
                        .sortOrder(order.incrementAndGet())
                        .build())
                .toList());
        order.set(0);
        gapAnswerRepository.saveAll(command.gapAnswers().stream()
                .map(item -> SectionDraftEvidenceGapAnswer.builder()
                        .draftEvidence(evidence)
                        .sourceIssueId(item.sourceIssueId())
                        .answerId(item.answerId())
                        .authorNameSnapshot(item.authorName())
                        .content(item.content())
                        .answeredAt(item.answeredAt())
                        .inherited(item.inherited())
                        .sortOrder(order.incrementAndGet())
                        .build())
                .toList());
        order.set(0);
        prerequisiteRepository.saveAll(command.prerequisites().stream()
                .map(item -> SectionDraftEvidencePrerequisite.builder()
                        .draftEvidence(evidence)
                        .sourceSectionId(item.sectionId())
                        .contentVersion(item.contentVersion())
                        .sortOrder(order.incrementAndGet())
                        .build())
                .toList());
    }

    private void validate(AiSectionDraftCreateCommand command) {
        if (command == null || command.sectionId() == null || command.actorUserId() == null
                || command.synthesisSetId() == null || command.generationRequestId() == null
                || command.baseVersion() < 0 || command.opinionGateGeneration() < 0
                || command.content() == null || command.content().isBlank()
                || command.content().length() > SectionDraftSaveRequest.MAX_CONTENT_LENGTH
                || command.inputSnapshotHash() == null
                || !command.inputSnapshotHash().matches("[0-9a-f]{64}")
                || command.sourceVersion() == null || command.sourceVersion().isBlank()
                || command.consensusSummary() == null || command.consensusSummary().isBlank()) {
            throw new IllegalArgumentException("AI 초안 생성 명령이 유효하지 않습니다.");
        }
    }
}
