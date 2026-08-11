package com.wevo.backend.review.repository;

import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.domain.ReviewIntentComparisonStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewIntentComparisonRepository extends JpaRepository<ReviewIntentComparison, Long> {

    Optional<ReviewIntentComparison> findByReviewSubmission_Id(Long submissionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReviewIntentComparison c where c.reviewSubmission.id = :submissionId")
    Optional<ReviewIntentComparison> findByReviewSubmissionIdForUpdate(
            @Param("submissionId") Long submissionId);

    Optional<ReviewIntentComparison> findBySourceAiJob_Id(Long sourceAiJobId);

    @Query("select c from ReviewIntentComparison c "
            + "join fetch c.reviewSubmission "
            + "left join fetch c.sourceAiJob "
            + "where c.status = :status and c.createdAt < :threshold "
            + "order by c.createdAt asc, c.id asc")
    List<ReviewIntentComparison> findPendingRecoveryCandidates(
            @Param("status") ReviewIntentComparisonStatus status,
            @Param("threshold") LocalDateTime threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReviewIntentComparison c where c.sourceAiJob.id = :jobId")
    Optional<ReviewIntentComparison> findBySourceAiJobIdForUpdate(@Param("jobId") Long jobId);

    @Query("select c from ReviewIntentComparison c "
            + "join fetch c.reviewSubmission s "
            + "where s.id in :submissionIds")
    List<ReviewIntentComparison> findAllWithSubmissionBySubmissionIdIn(
            @Param("submissionIds") List<Long> submissionIds);
}
