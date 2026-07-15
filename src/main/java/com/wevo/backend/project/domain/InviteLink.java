package com.wevo.backend.project.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "invite_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(length = 255)
    private String token;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_active")
    private Boolean isActive;

    @Builder
    private InviteLink(Project project, User createdBy, String token,
                       LocalDateTime expiresAt, Boolean isActive) {
        this.project = project;
        this.createdBy = createdBy;
        this.token = token;
        this.expiresAt = expiresAt;
        this.isActive = isActive;
    }

    /**
     * 재사용 초대 링크를 발급한다. MVP에서는 만료를 두지 않으므로 {@code expiresAt=null},
     * 활성 상태({@code isActive=true})로 생성한다. (제품 정책서 §2.1 — 만료·비활성화는 MVP 이후)
     */
    public static InviteLink issue(Project project, User createdBy, String token) {
        return InviteLink.builder()
                .project(project)
                .createdBy(createdBy)
                .token(token)
                .expiresAt(null)
                .isActive(true)
                .build();
    }
}
