package com.wevo.backend.review.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 섹션 검토용 공유 링크. 내부(INTERNAL)/외부(EXTERNAL) 검토자에게 토큰으로 발급된다.
 */
@Entity
@Table(name = "review_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", length = 20)
    private ReviewType reviewType;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_active")
    private Boolean isActive;

    @Builder
    private ReviewLink(ProjectSection projectSection, User createdBy, String token,
                       ReviewType reviewType, LocalDateTime expiresAt, Boolean isActive) {
        this.projectSection = projectSection;
        this.createdBy = createdBy;
        this.token = token;
        this.reviewType = reviewType;
        this.expiresAt = expiresAt;
        this.isActive = isActive;
    }
}
