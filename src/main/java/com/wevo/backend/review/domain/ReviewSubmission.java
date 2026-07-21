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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검토 링크를 통해 제출된 검토 결과. 검토자가 이해한 내용(summary)과 이해도 신호를 담는다.
 * (공개 제출이므로 reviewer_name 문자열만 보관하고 User 와 직접 연결하지 않는다.)
 *
 * <p><b>브라우저당 1회</b> — 브라우저에 저장된 익명 검토자 키({@code anonymousReviewerId})와 링크의 조합을
 * 유니크 제약으로 묶어, 같은 브라우저·기기에서의 중복 제출을 DB 수준에서 차단한다.
 * (쿠키 삭제·시크릿 모드 재제출까지는 막지 않는다 — MVP 수용 범위)
 */
@Entity
@Table(
        name = "review_submissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_submission_link_reviewer",
                columnNames = {"review_link_id", "anonymous_reviewer_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewSubmission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_link_id", nullable = false)
    private ReviewLink reviewLink;

    /** 브라우저·기기별 익명 검토자 키. 링크 내에서 중복 제출을 막는 식별자다. */
    @Column(name = "anonymous_reviewer_id", length = 64, nullable = false)
    private String anonymousReviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "understanding_signal", length = 20, nullable = false)
    private UnderstandingSignal understandingSignal;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "reviewer_name", length = 100)
    private String reviewerName;

    @Builder
    private ReviewSubmission(ReviewLink reviewLink, String anonymousReviewerId,
                            UnderstandingSignal understandingSignal, String summary, String reviewerName) {
        this.reviewLink = reviewLink;
        this.anonymousReviewerId = anonymousReviewerId;
        this.understandingSignal = understandingSignal;
        this.summary = summary;
        this.reviewerName = reviewerName;
    }
}
