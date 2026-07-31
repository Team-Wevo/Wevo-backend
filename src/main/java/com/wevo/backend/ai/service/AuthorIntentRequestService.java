package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.AuthorIntentContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorIntentRequestService {

    private static final Set<AiJobStatus> REUSABLE_STATUSES =
            Set.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.SUCCEEDED);

    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final AiContextAssembler contextAssembler;
    private final AiJobService aiJobService;
    private final AiProperties aiProperties;
    private final UserService userService;

    public AuthorIntentRequestService(
            SectionAccessGuard sectionAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            AiContextAssembler contextAssembler,
            AiJobService aiJobService,
            AiProperties aiProperties,
            UserService userService
    ) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.contextAssembler = contextAssembler;
        this.aiJobService = aiJobService;
        this.aiProperties = aiProperties;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public UUID requestExtraction(Long sectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireOwnedSection(sectionId, userId);
        VerifiedProjectAccess access = projectAccessGuard.requireParticipantAccess(
                section.getProject().getId(), userId);
        AssembledAiContext<AuthorIntentContext> assembled =
                contextAssembler.assembleAuthorIntent(access, sectionId);
        AiProperties.ModelOptions options =
                aiProperties.optionsFor(AiFeature.AUTHOR_INTENT_EXTRACTION);
        AiJobIdempotencyInput identity = identity(section, assembled, options);
        Optional<AiJobCreateResult> existing = aiJobService.findLatest(identity);
        if (existing.isPresent() && REUSABLE_STATUSES.contains(existing.get().status())) {
            return existing.get().requestId();
        }
        User requestedBy = userService.getUserReference(userId);
        AiJobCreateResult result = existing
                .map(previous -> aiJobService.retry(previous.requestId(), requestedBy))
                .orElseGet(() -> aiJobService.createOrGet(new AiJobCreateCommand(
                        section.getProject(),
                        section,
                        requestedBy,
                        AiFeature.AUTHOR_INTENT_EXTRACTION,
                        assembled.snapshot().inputSnapshotHash(),
                        assembled.context().sourceVersion(),
                        AuthorIntentContract.PROMPT_VERSION,
                        AuthorIntentContract.SCHEMA_VERSION,
                        options.model(),
                        options.maxOutputTokens())));
        return result.requestId();
    }

    private AiJobIdempotencyInput identity(
            ProjectSection section,
            AssembledAiContext<AuthorIntentContext> assembled,
            AiProperties.ModelOptions options
    ) {
        return new AiJobIdempotencyInput(
                AiFeature.AUTHOR_INTENT_EXTRACTION,
                section.getProject().getId(),
                section.getId(),
                assembled.snapshot().inputSnapshotHash(),
                assembled.context().sourceVersion(),
                AuthorIntentContract.PROMPT_VERSION,
                AuthorIntentContract.SCHEMA_VERSION,
                options.model(),
                options.maxOutputTokens());
    }
}
