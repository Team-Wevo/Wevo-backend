package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiSectionCheck;
import com.wevo.backend.ai.domain.AiSectionCheckFinding;
import com.wevo.backend.ai.domain.AiSectionCheckPrerequisite;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.repository.AiSectionCheckFindingRepository;
import com.wevo.backend.ai.repository.AiSectionCheckPrerequisiteRepository;
import com.wevo.backend.ai.repository.AiSectionCheckRepository;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 성공 결과 저장과 section CURRENT overlay 바인딩을 원자적으로 반영한다. */
@Service
public class PrecheckResultWriter {

    private final AiSectionCheckRepository checkRepository;
    private final AiSectionCheckFindingRepository findingRepository;
    private final AiSectionCheckPrerequisiteRepository prerequisiteRepository;
    private final AiJobRepository jobRepository;
    private final DraftReviewOutputValidator outputValidator;

    public PrecheckResultWriter(
            AiSectionCheckRepository checkRepository,
            AiSectionCheckFindingRepository findingRepository,
            AiSectionCheckPrerequisiteRepository prerequisiteRepository,
            AiJobRepository jobRepository,
            DraftReviewOutputValidator outputValidator
    ) {
        this.checkRepository = checkRepository;
        this.findingRepository = findingRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.jobRepository = jobRepository;
        this.outputValidator = outputValidator;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long persist(
            UUID requestId,
            DraftReviewContext context,
            DraftReviewOutput output
    ) {
        AiJob job = jobRepository.findByRequestId(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
        validate(job, context, output);
        LinkedHashSet<String> sourceContents = new LinkedHashSet<>();
        sourceContents.add(context.content());
        context.prerequisites().forEach(item -> sourceContents.add(item.content()));
        outputValidator.validate(
                output,
                StructuredOutputValidationContext.forSourceContents(
                        context.content(), sourceContents)
        );
        AiSectionCheck check = AiSectionCheck.builder()
                .projectSection(job.getProjectSection())
                .sourceJob(job)
                .checkedDraftId(context.draftId())
                .checkedContentVersion(context.contentVersion())
                .inputSnapshotHash(job.getInputSnapshotHash())
                .sourceVersion(job.getSourceVersion())
                .dependencyVersionHash(context.dependencyVersionHash())
                .rewriteContent(output.rewrite().content())
                .changedCount(output.rewrite().changedCount())
                .build();
        try {
            checkRepository.saveAndFlush(check);
            AtomicInteger order = new AtomicInteger();
            findingRepository.saveAll(output.findings().stream()
                    .map(item -> AiSectionCheckFinding.builder()
                            .sectionCheck(check)
                            .type(item.type())
                            .targetExcerpt(item.targetExcerpt())
                            .comment(item.comment())
                            .suggestion(item.suggestion())
                            .sortOrder(order.incrementAndGet())
                            .build())
                    .toList());
            order.set(0);
            prerequisiteRepository.saveAll(context.prerequisites().stream()
                    .map(item -> AiSectionCheckPrerequisite.builder()
                            .sectionCheck(check)
                            .sourceSectionId(item.sectionId())
                            .contentVersion(item.contentVersion())
                            .sortOrder(order.incrementAndGet())
                            .build())
                    .toList());
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("AI 사전 검토 결과 무결성 저장에 실패했습니다.", exception);
        }
        job.getProjectSection().bindCurrentAiCheck();
        return check.getId();
    }

    private void validate(
            AiJob job,
            DraftReviewContext context,
            DraftReviewOutput output
    ) {
        if (job == null
                || job.getFeature() != AiFeature.DRAFT_REVIEW
                || job.getProjectSection() == null
                || context == null
                || !job.getProjectSection().getId().equals(context.section().sectionId())
                || !job.getInputSnapshotHash().matches("[0-9a-f]{64}")
                || context.draftId() == null
                || context.draftId() <= 0
                || context.contentVersion() <= 0
                || output == null
                || output.findings() == null
                || output.rewrite() == null) {
            throw new IllegalArgumentException("AI 사전 검토 저장 입력이 유효하지 않습니다.");
        }
    }
}
