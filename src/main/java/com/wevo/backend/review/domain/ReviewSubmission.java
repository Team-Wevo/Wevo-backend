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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검토 링크를 통해 제출된 검토 결과. 검토자가 이해한 내용(summary)과 이해도 신호를 담는다.
 * (공개 제출이므로 reviewer_name 문자열만 보관하고 User 와 직접 연결하지 않는다.)
 */
@Entity
@Table(name = "review_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewSubmission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_link_id")
    private ReviewLink reviewLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "understanding_signal", length = 20)
    private UnderstandingSignal understandingSignal;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "reviewer_name", length = 100)
    private String reviewerName;

    @Builder
    private ReviewSubmission(ReviewLink reviewLink, UnderstandingSignal understandingSignal,
                            String summary, String reviewerName) {
        this.reviewLink = reviewLink;
        this.understandingSignal = understandingSignal;
        this.summary = summary;
        this.reviewerName = reviewerName;
    }
}
