package com.wevo.backend.section.domain;

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

/**
 * 섹션 편집 점유(락). 동시 편집 충돌 방지를 위해 일정 시간(lease_until) 동안 편집권을 보유한다.
 */
@Entity
@Table(name = "draft_leases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftLease extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "holder_user_id")
    private User holder;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Builder
    private DraftLease(ProjectSection projectSection, User holder, LocalDateTime leaseUntil) {
        this.projectSection = projectSection;
        this.holder = holder;
        this.leaseUntil = leaseUntil;
    }
}
