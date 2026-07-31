package com.wevo.backend.review.service;

import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.domain.ReviewIntentAlignment;
import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.domain.ReviewIntentComparisonStatus;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.repository.ReviewIntentComparisonRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 비교 상태의 생성·AI job 연결·완료 전이를 review 도메인 안에서 소유한다. */
@Service
public class ReviewIntentComparisonStateService {

    private final ReviewIntentComparisonRepository repository;

    public ReviewIntentComparisonStateService(ReviewIntentComparisonRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareNotAvailable(ReviewSubmission submission) {
        if (repository.findByReviewSubmission_Id(submission.getId()).isEmpty()) {
            repository.save(ReviewIntentComparison.notAvailable(submission));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void preparePending(
            ReviewSubmission submission,
            String intentHash,
            String summaryHash,
            String promptVersion,
            String schemaVersion,
            String modelId
    ) {
        if (repository.findByReviewSubmission_Id(submission.getId()).isEmpty()) {
            repository.save(ReviewIntentComparison.pending(
                    submission,
                    intentHash,
                    summaryHash,
                    promptVersion,
                    schemaVersion,
                    modelId));
        }
    }

    @Transactional(readOnly = true)
    public ReviewIntentComparisonInput getInput(Long submissionId) {
        return toInput(findBySubmission(submissionId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReviewIntentComparisonInput lockInput(Long submissionId) {
        ReviewIntentComparison comparison = repository
                .findByReviewSubmissionIdForUpdate(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_INPUT_CHANGED));
        return toInput(comparison);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bindJob(Long submissionId, AiJob job) {
        ReviewIntentComparison comparison = repository
                .findByReviewSubmissionIdForUpdate(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_INPUT_CHANGED));
        if (comparison.getStatus() != ReviewIntentComparisonStatus.PENDING) {
            return;
        }
        if (comparison.getSourceAiJob() == null) {
            comparison.bindSourceJob(job);
        } else if (!Objects.equals(comparison.getSourceAiJob().getId(), job.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long succeed(
            Long submissionId,
            AiJob job,
            ReviewIntentAlignment alignment,
            String differenceSummary,
            String evidenceExcerpt
    ) {
        ReviewIntentComparison comparison = repository
                .findByReviewSubmissionIdForUpdate(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_INPUT_CHANGED));
        if (comparison.getSourceAiJob() == null) {
            comparison.bindSourceJob(job);
        }
        comparison.succeed(alignment, differenceSummary, evidenceExcerpt);
        return comparison.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long submissionId, String failureCode) {
        repository.findByReviewSubmissionIdForUpdate(submissionId)
                .ifPresent(comparison -> comparison.fail(failureCode));
    }

    private ReviewIntentComparison findBySubmission(Long submissionId) {
        return repository.findByReviewSubmission_Id(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_INPUT_CHANGED));
    }

    private ReviewIntentComparisonInput toInput(ReviewIntentComparison comparison) {
        if (comparison.getStatus() != ReviewIntentComparisonStatus.PENDING) {
            throw new BusinessException(ErrorCode.AI_JOB_INPUT_CHANGED);
        }
        ReviewSubmission submission = comparison.getReviewSubmission();
        if (submission.getReviewLink().getCreatedBy() == null
                || submission.getReviewLink().getAuthorIntentSnapshot() == null
                || submission.getSummary() == null
                || submission.getSummary().isBlank()) {
            throw new BusinessException(ErrorCode.AI_JOB_INPUT_CHANGED);
        }
        return new ReviewIntentComparisonInput(
                submission.getId(),
                submission.getReviewLink().getProjectSection().getProject().getId(),
                submission.getReviewLink().getProjectSection().getId(),
                submission.getReviewLink().getCreatedBy().getId(),
                submission.getReviewLink().getContentVersion(),
                submission.getReviewLink().getAuthorIntentSnapshot(),
                submission.getSummary());
    }
}
