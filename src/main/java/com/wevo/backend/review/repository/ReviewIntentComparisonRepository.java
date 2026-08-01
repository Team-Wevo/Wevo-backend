package com.wevo.backend.review.repository;

import com.wevo.backend.review.domain.ReviewIntentComparison;
import jakarta.persistence.LockModeType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReviewIntentComparison c where c.sourceAiJob.id = :jobId")
    Optional<ReviewIntentComparison> findBySourceAiJobIdForUpdate(@Param("jobId") Long jobId);

    @Query("select c from ReviewIntentComparison c "
            + "join fetch c.reviewSubmission s "
            + "where s.id in :submissionIds")
    List<ReviewIntentComparison> findAllWithSubmissionBySubmissionIdIn(
            @Param("submissionIds") List<Long> submissionIds);
}
