package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.ReviewIntentComparisonContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.service.ReviewIntentComparisonInput;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReviewIntentComparisonRequestService {

    private static final Set<AiJobStatus> REUSABLE_STATUSES =
            Set.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.SUCCEEDED);

    private final ReviewIntentComparisonStateService stateService;
    private final SectionAccessGuard sectionAccessGuard;
    private final UserService userService;
    private final AiInputSnapshotHasher snapshotHasher;
    private final AiProperties properties;
    private final AiJobService aiJobService;
    private final AiJobRepository jobRepository;

    public ReviewIntentComparisonRequestService(
            ReviewIntentComparisonStateService stateService,
            SectionAccessGuard sectionAccessGuard,
            UserService userService,
            AiInputSnapshotHasher snapshotHasher,
            AiProperties properties,
            AiJobService aiJobService,
            AiJobRepository jobRepository
    ) {
        this.stateService = stateService;
        this.sectionAccessGuard = sectionAccessGuard;
        this.userService = userService;
        this.snapshotHasher = snapshotHasher;
        this.properties = properties;
        this.aiJobService = aiJobService;
        this.jobRepository = jobRepository;
    }

    public void request(Long submissionId) {
        ReviewIntentComparisonInput input = stateService.getInput(submissionId);
        ProjectSection section = sectionAccessGuard.requireOwnedSection(
                input.sectionId(), input.requestedByUserId());
        ReviewIntentComparisonContext context = context(input);
        AiProperties.ModelOptions options =
                properties.optionsFor(AiFeature.REVIEW_INTENT_COMPARISON);
        AiInputSnapshot snapshot = snapshotHasher.snapshot(context, options);
        AiJobIdempotencyInput identity = identity(input, context, snapshot, options);
        Optional<AiJobCreateResult> existing = aiJobService.findLatest(identity);
        User requestedBy = userService.getUserReference(input.requestedByUserId());
        AiJobCreateResult result;
        if (existing.isPresent() && REUSABLE_STATUSES.contains(existing.get().status())) {
            result = existing.get();
        } else if (existing.isPresent()) {
            result = aiJobService.retry(existing.get().requestId(), requestedBy);
        } else {
            result = aiJobService.createOrGet(new AiJobCreateCommand(
                    section.getProject(),
                    section,
                    requestedBy,
                    AiFeature.REVIEW_INTENT_COMPARISON,
                    snapshot.inputSnapshotHash(),
                    context.sourceVersion(),
                    ReviewIntentComparisonContract.PROMPT_VERSION,
                    ReviewIntentComparisonContract.SCHEMA_VERSION,
                    options.model(),
                    options.maxOutputTokens()));
        }
        AiJob job = jobRepository.findByRequestId(result.requestId())
                .orElseThrow(() -> new IllegalStateException("생성된 비교 AI 작업을 찾을 수 없습니다."));
        stateService.bindJob(submissionId, job);
    }

    private ReviewIntentComparisonContext context(ReviewIntentComparisonInput input) {
        return new ReviewIntentComparisonContext(
                input.submissionId(),
                input.sectionId(),
                input.contentVersion(),
                input.authorIntent(),
                input.reviewerSummary());
    }

    private AiJobIdempotencyInput identity(
            ReviewIntentComparisonInput input,
            ReviewIntentComparisonContext context,
            AiInputSnapshot snapshot,
            AiProperties.ModelOptions options
    ) {
        return new AiJobIdempotencyInput(
                AiFeature.REVIEW_INTENT_COMPARISON,
                input.projectId(),
                input.sectionId(),
                snapshot.inputSnapshotHash(),
                context.sourceVersion(),
                ReviewIntentComparisonContract.PROMPT_VERSION,
                ReviewIntentComparisonContract.SCHEMA_VERSION,
                options.model(),
                options.maxOutputTokens());
    }
}
