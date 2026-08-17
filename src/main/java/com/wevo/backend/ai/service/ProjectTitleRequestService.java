package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.ProjectTitleContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 제목 자동 생성 job 을 요청한다. 사용자용 API 없이 프로젝트 생성 이벤트로만 트리거된다.
 * 같은 입력의 진행 중·성공 job 이 있으면 재사용해 중복 호출을 막는다.
 */
@Service
public class ProjectTitleRequestService {

    private static final Set<AiJobStatus> REUSABLE_STATUSES =
            Set.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.SUCCEEDED);

    private final ProjectAccessGuard projectAccessGuard;
    private final AiContextAssembler contextAssembler;
    private final AiJobService aiJobService;
    private final AiProperties aiProperties;
    private final UserService userService;

    public ProjectTitleRequestService(
            ProjectAccessGuard projectAccessGuard,
            AiContextAssembler contextAssembler,
            AiJobService aiJobService,
            AiProperties aiProperties,
            UserService userService
    ) {
        this.projectAccessGuard = projectAccessGuard;
        this.contextAssembler = contextAssembler;
        this.aiJobService = aiJobService;
        this.aiProperties = aiProperties;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public void requestForProject(Long projectId, Long ownerId) {
        VerifiedProjectAccess access = projectAccessGuard.requireParticipantAccess(projectId, ownerId);
        AssembledAiContext<ProjectTitleContext> assembled =
                contextAssembler.assembleProjectTitle(access);
        AiProperties.ModelOptions options =
                aiProperties.optionsFor(AiFeature.PROJECT_TITLE_SUGGESTION);
        AiJobIdempotencyInput identity = identity(projectId, assembled, options);
        Optional<AiJobCreateResult> existing = aiJobService.findLatest(identity);
        if (existing.isPresent() && REUSABLE_STATUSES.contains(existing.get().status())) {
            return;
        }
        User requestedBy = userService.getUserReference(ownerId);
        if (existing.isPresent()) {
            aiJobService.retry(existing.get().requestId(), requestedBy);
            return;
        }
        aiJobService.createOrGet(new AiJobCreateCommand(
                access.project(),
                null,
                requestedBy,
                AiFeature.PROJECT_TITLE_SUGGESTION,
                assembled.snapshot().inputSnapshotHash(),
                assembled.context().sourceVersion(),
                ProjectTitleContract.PROMPT_VERSION,
                ProjectTitleContract.SCHEMA_VERSION,
                options.model(),
                options.maxOutputTokens()));
    }

    private AiJobIdempotencyInput identity(
            Long projectId,
            AssembledAiContext<ProjectTitleContext> assembled,
            AiProperties.ModelOptions options
    ) {
        return new AiJobIdempotencyInput(
                AiFeature.PROJECT_TITLE_SUGGESTION,
                projectId,
                null,
                assembled.snapshot().inputSnapshotHash(),
                assembled.context().sourceVersion(),
                ProjectTitleContract.PROMPT_VERSION,
                ProjectTitleContract.SCHEMA_VERSION,
                options.model(),
                options.maxOutputTokens());
    }
}
