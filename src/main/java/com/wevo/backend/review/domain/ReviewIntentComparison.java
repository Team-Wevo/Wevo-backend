package com.wevo.backend.review.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 외부 검토 제출 한 건과 링크에 고정된 작성자 의도의 AI 비교 결과. */
@Entity
@Table(name = "review_intent_comparisons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewIntentComparison extends BaseTimeEntity {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_submission_id", nullable = false, unique = true)
    private ReviewSubmission reviewSubmission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewIntentComparisonStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReviewIntentAlignment alignment;

    @Column(name = "difference_summary", length = 1000)
    private String differenceSummary;

    @Column(name = "evidence_excerpt", length = 300)
    private String evidenceExcerpt;

    @Column(name = "source_ai_job_id", unique = true)
    private Long sourceAiJobId;

    @Column(name = "intent_snapshot_hash", length = 64)
    private String intentSnapshotHash;

    @Column(name = "reviewer_summary_hash", length = 64)
    private String reviewerSummaryHash;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Column(name = "schema_version", length = 100)
    private String schemaVersion;

    @Column(name = "model_id", length = 100)
    private String modelId;

    @Column(name = "failure_code", length = 10)
    private String failureCode;

    private ReviewIntentComparison(ReviewSubmission submission) {
        this.reviewSubmission = Objects.requireNonNull(submission, "submission은 필수입니다.");
    }

    public static ReviewIntentComparison notAvailable(ReviewSubmission submission) {
        ReviewIntentComparison comparison = new ReviewIntentComparison(submission);
        comparison.status = ReviewIntentComparisonStatus.NOT_AVAILABLE;
        return comparison;
    }

    public static ReviewIntentComparison pending(
            ReviewSubmission submission,
            String intentHash,
            String summaryHash,
            String promptVersion,
            String schemaVersion,
            String modelId
    ) {
        ReviewIntentComparison comparison = new ReviewIntentComparison(submission);
        comparison.status = ReviewIntentComparisonStatus.PENDING;
        comparison.intentSnapshotHash = requireHash(intentHash, "intentHash");
        comparison.reviewerSummaryHash = requireHash(summaryHash, "summaryHash");
        comparison.promptVersion = requireText(promptVersion, "promptVersion", 100);
        comparison.schemaVersion = requireText(schemaVersion, "schemaVersion", 100);
        comparison.modelId = requireText(modelId, "modelId", 100);
        return comparison;
    }

    public void bindSourceJobId(Long sourceAiJobId) {
        if (status != ReviewIntentComparisonStatus.PENDING) {
            throw new IllegalStateException("대기 중인 비교에만 AI 작업을 연결할 수 있습니다.");
        }
        if (sourceAiJobId == null || sourceAiJobId <= 0) {
            throw new IllegalArgumentException("sourceAiJobId는 양수여야 합니다.");
        }
        this.sourceAiJobId = sourceAiJobId;
    }

    public void succeed(
            ReviewIntentAlignment alignment,
            String differenceSummary,
            String evidenceExcerpt
    ) {
        if (status != ReviewIntentComparisonStatus.PENDING) {
            throw new IllegalStateException("대기 중인 비교만 완료할 수 있습니다.");
        }
        this.alignment = Objects.requireNonNull(alignment, "alignment는 필수입니다.");
        this.differenceSummary = requireText(differenceSummary, "differenceSummary", 1000);
        this.evidenceExcerpt = optionalText(evidenceExcerpt, 300);
        this.failureCode = null;
        this.status = ReviewIntentComparisonStatus.SUCCEEDED;
    }

    public void fail(String failureCode) {
        if (status == ReviewIntentComparisonStatus.SUCCEEDED
                || status == ReviewIntentComparisonStatus.NOT_AVAILABLE) {
            return;
        }
        this.alignment = null;
        this.differenceSummary = null;
        this.evidenceExcerpt = null;
        this.failureCode = requireText(failureCode, "failureCode", 10);
        this.status = ReviewIntentComparisonStatus.FAILED;
    }

    private static String requireHash(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "가 유효하지 않습니다.");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + "가 유효하지 않습니다.");
        }
        return value;
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return requireText(value, "optionalText", maxLength);
    }
}
