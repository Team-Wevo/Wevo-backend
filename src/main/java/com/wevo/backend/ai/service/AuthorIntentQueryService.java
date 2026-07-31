package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.response.AuthorIntentResponse;
import com.wevo.backend.ai.dto.response.AuthorIntentResponse.FailureResponse;
import com.wevo.backend.ai.dto.response.AuthorIntentResponse.LatestJobResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.SectionAuthorIntentRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthorIntentQueryService {

    private final SectionAccessGuard sectionAccessGuard;
    private final SectionDraftRepository draftRepository;
    private final SectionAuthorIntentRepository intentRepository;
    private final AiJobRepository jobRepository;
    private final AiJobStatusMapper statusMapper;

    public AuthorIntentQueryService(
            SectionAccessGuard sectionAccessGuard,
            SectionDraftRepository draftRepository,
            SectionAuthorIntentRepository intentRepository,
            AiJobRepository jobRepository,
            AiJobStatusMapper statusMapper
    ) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.draftRepository = draftRepository;
        this.intentRepository = intentRepository;
        this.jobRepository = jobRepository;
        this.statusMapper = statusMapper;
    }

    public AuthorIntentResponse getCurrent(Long sectionId, Long userId) {
        sectionAccessGuard.requireOwnedSection(sectionId, userId);
        SectionDraft draft = draftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .orElseThrow(() -> new com.wevo.backend.global.exception.BusinessException(
                        com.wevo.backend.global.exception.ErrorCode.SECTION_DRAFT_NOT_FOUND));
        SectionAuthorIntent intent = intentRepository
                .findByProjectSection_IdAndContentVersion(sectionId, draft.getVersion())
                .orElse(null);
        return responseWithLatest(sectionId, draft.getVersion(), intent);
    }

    AuthorIntentResponse responseWithLatest(
            Long sectionId,
            int contentVersion,
            SectionAuthorIntent intent
    ) {
        AiJob latestJob = jobRepository
                .findTopByProjectSection_IdAndFeatureAndSourceVersionOrderByQueuedAtDescIdDesc(
                        sectionId,
                        AiFeature.AUTHOR_INTENT_EXTRACTION,
                        "draft-v" + contentVersion)
                .orElse(null);
        return response(contentVersion, intent, latestJob);
    }

    AuthorIntentResponse response(int contentVersion, SectionAuthorIntent intent, AiJob latestJob) {
        LatestJobResponse jobResponse = null;
        if (latestJob != null) {
            AiJobStatusMapper.MappedStatus mapped = statusMapper.map(latestJob);
            jobResponse = new LatestJobResponse(
                    latestJob.getRequestId(),
                    mapped.status(),
                    mapped.failed()
                            ? new FailureResponse(mapped.failureCode(), mapped.failureMessage())
                            : null);
        }
        return new AuthorIntentResponse(
                contentVersion,
                intent == null ? null : intent.getStatus(),
                intent == null ? null : intent.getAiSuggestedIntent(),
                intent == null ? null : intent.getConfirmedIntent(),
                intent == null || intent.getConfirmedBy() == null
                        ? null : intent.getConfirmedBy().getId(),
                intent == null ? null : intent.getConfirmedAt(),
                jobResponse);
    }
}
