package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContextAssembler;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 모든 기본 섹션이 확정된 프로젝트의 OWNER 명시 실행을 큐잉한다. */
@Service
public class ProjectFlowReviewRequestService {
    private static final Set<AiJobStatus> REUSABLE =
            Set.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.SUCCEEDED);
    private final ProjectAccessGuard accessGuard;
    private final SectionConfirmationQueryService confirmationQueryService;
    private final ProjectFlowReviewContextAssembler assembler;
    private final AiJobService jobService;
    private final AiProperties properties;
    private final UserService userService;

    public ProjectFlowReviewRequestService(ProjectAccessGuard accessGuard,
                                           SectionConfirmationQueryService confirmationQueryService,
                                           ProjectFlowReviewContextAssembler assembler,
                                           AiJobService jobService, AiProperties properties,
                                           UserService userService) {
        this.accessGuard = accessGuard; this.confirmationQueryService = confirmationQueryService;
        this.assembler = assembler; this.jobService = jobService;
        this.properties = properties; this.userService = userService;
    }

    @Transactional(readOnly = true)
    public UUID requestReview(Long projectId, Long userId) {
        ProjectAccessGuard.hidingNonMember(ErrorCode.PROJECT_NOT_FOUND,
                () -> accessGuard.requireOwner(projectId, userId));
        VerifiedProjectAccess access = ProjectAccessGuard.hidingNonMember(ErrorCode.PROJECT_NOT_FOUND,
                () -> accessGuard.requireParticipantAccess(projectId, userId));
        if (!confirmationQueryService.getConfirmationSummary(access).allConfirmed()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        var assembled = assembler.assemble(access);
        validateComplete(assembled.context());
        var options = properties.optionsFor(AiFeature.PROJECT_FLOW_REVIEW);
        AiJobIdempotencyInput identity = new AiJobIdempotencyInput(
                AiFeature.PROJECT_FLOW_REVIEW, projectId, null,
                assembled.snapshot().inputSnapshotHash(), ProjectFlowReviewContract.SOURCE_VERSION,
                ProjectFlowReviewContract.PROMPT_VERSION, ProjectFlowReviewContract.SCHEMA_VERSION,
                options.model(), options.maxOutputTokens());
        Optional<AiJobCreateResult> existing = jobService.findLatest(identity);
        if (existing.isPresent() && REUSABLE.contains(existing.get().status())) {
            return existing.get().requestId();
        }
        User user = userService.getUserReference(userId);
        return existing.map(previous -> jobService.retry(previous.requestId(), user))
                .orElseGet(() -> jobService.createOrGet(new AiJobCreateCommand(
                        access.project(), null, user, AiFeature.PROJECT_FLOW_REVIEW,
                        assembled.snapshot().inputSnapshotHash(), ProjectFlowReviewContract.SOURCE_VERSION,
                        ProjectFlowReviewContract.PROMPT_VERSION, ProjectFlowReviewContract.SCHEMA_VERSION,
                        options.model(), options.maxOutputTokens()))).requestId();
    }

    private void validateComplete(ProjectFlowReviewContext context) {
        if (context.totalSectionCount() != context.confirmedSectionCount()
                || context.sections().size() != context.totalSectionCount()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }
}
