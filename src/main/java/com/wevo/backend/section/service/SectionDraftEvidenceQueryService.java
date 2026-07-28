package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.SectionDraftEvidence;
import com.wevo.backend.section.repository.SectionDraftEvidenceDecisionRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceGapAnswerRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceOpinionRepository;
import com.wevo.backend.section.repository.SectionDraftEvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최신 AI 생성 version의 aggregate evidence를 공개하는 section read boundary. */
@Service
@Transactional(readOnly = true)
public class SectionDraftEvidenceQueryService {

    private final SectionDraftEvidenceRepository evidenceRepository;
    private final SectionDraftEvidenceOpinionRepository opinionRepository;
    private final SectionDraftEvidenceDecisionRepository decisionRepository;
    private final SectionDraftEvidenceGapAnswerRepository gapAnswerRepository;

    public SectionDraftEvidenceQueryService(
            SectionDraftEvidenceRepository evidenceRepository,
            SectionDraftEvidenceOpinionRepository opinionRepository,
            SectionDraftEvidenceDecisionRepository decisionRepository,
            SectionDraftEvidenceGapAnswerRepository gapAnswerRepository
    ) {
        this.evidenceRepository = evidenceRepository;
        this.opinionRepository = opinionRepository;
        this.decisionRepository = decisionRepository;
        this.gapAnswerRepository = gapAnswerRepository;
    }

    public SectionDraftEvidenceView getLatest(
            VerifiedProjectAccess access,
            Long sectionId
    ) {
        if (access == null || sectionId == null) {
            throw new IllegalArgumentException("검증된 project 접근과 sectionId는 필수입니다.");
        }
        SectionDraftEvidence evidence = evidenceRepository
                .findTopBySectionDraft_ProjectSection_IdOrderBySectionDraft_VersionDesc(sectionId)
                .filter(item -> access.projectId().equals(
                        item.getSectionDraft().getProjectSection().getProject().getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND));
        Long evidenceId = evidence.getId();
        return new SectionDraftEvidenceView(
                evidence.getSectionDraft().getId(),
                evidence.getSectionDraft().getVersion(),
                evidence.getSynthesisSetId(),
                evidence.getOpinionGateGeneration(),
                evidence.getGenerationRequestId(),
                evidence.getInputSnapshotHash(),
                evidence.getConsensusSummary(),
                opinionRepository.findAllByDraftEvidence_IdOrderBySortOrderAsc(evidenceId).stream()
                        .map(item -> new SectionDraftEvidenceView.OpinionEvidence(
                                item.getOpinionId(),
                                item.getAuthorNameSnapshot(),
                                item.getContent()))
                        .toList(),
                decisionRepository.findAllByDraftEvidence_IdOrderBySortOrderAsc(evidenceId).stream()
                        .map(item -> new SectionDraftEvidenceView.DecisionEvidence(
                                item.getIssueId(),
                                item.getQuestion(),
                                item.getDecision()))
                        .toList(),
                gapAnswerRepository.findAllByDraftEvidence_IdOrderBySortOrderAsc(evidenceId).stream()
                        .map(item -> new SectionDraftEvidenceView.GapAnswerEvidence(
                                item.getSourceIssueId(),
                                item.getAnswerId(),
                                item.getAuthorNameSnapshot(),
                                item.getContent(),
                                item.getAnsweredAt(),
                                item.isInherited()))
                        .toList()
        );
    }
}
