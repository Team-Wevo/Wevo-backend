package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.AuthorIntentContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.AuthorIntentExtractionOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.repository.SectionAuthorIntentRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorIntentResultWriter {

    private final AiJobRepository jobRepository;
    private final SectionAuthorIntentRepository intentRepository;
    private final AuthorIntentExtractionOutputValidator outputValidator;

    public AuthorIntentResultWriter(
            AiJobRepository jobRepository,
            SectionAuthorIntentRepository intentRepository,
            AuthorIntentExtractionOutputValidator outputValidator
    ) {
        this.jobRepository = jobRepository;
        this.intentRepository = intentRepository;
        this.outputValidator = outputValidator;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long persist(
            UUID requestId,
            AuthorIntentContext context,
            AuthorIntentExtractionOutput output
    ) {
        AiJob job = jobRepository.findByRequestId(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
        if (job.getFeature() != AiFeature.AUTHOR_INTENT_EXTRACTION
                || job.getProjectSection() == null
                || !job.getProjectSection().getId().equals(context.sectionId())) {
            throw new IllegalArgumentException("작성자 의도 저장 입력이 유효하지 않습니다.");
        }
        outputValidator.validate(output, StructuredOutputValidationContext.empty());

        SectionAuthorIntent intent = intentRepository
                .findByProjectSection_IdAndContentVersion(
                        context.sectionId(), context.contentVersion())
                .orElseGet(() -> SectionAuthorIntent.suggested(
                        job.getProjectSection(), context.contentVersion(), output.intent(), job));
        if (intent.getId() != null) {
            intent.updateSuggestion(output.intent(), job);
        }
        try {
            intentRepository.saveAndFlush(intent);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("작성자 의도 결과 무결성 저장에 실패했습니다.", exception);
        }
        return intent.getId();
    }
}
