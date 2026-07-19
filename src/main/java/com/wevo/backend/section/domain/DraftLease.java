package com.wevo.backend.section.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 섹션 초안의 단일 편집권(lease).
 *
 * <p>섹션당 한 행만 유지한다. 만료된 lease는 새 요청자가 재사용하므로, 만료 행을 정리하는
 * 별도 배치 작업 없이도 동시 편집을 막을 수 있다.
 */
@Entity
@Table(
        name = "draft_leases",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_draft_leases_section",
                columnNames = "project_section_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftLease extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id", nullable = false)
    private ProjectSection projectSection;

    @Column(name = "holder_user_id", nullable = false)
    private Long holderUserId;

    @Column(name = "lease_until", nullable = false)
    private LocalDateTime leaseUntil;

    @Builder
    private DraftLease(ProjectSection projectSection, Long holderUserId, LocalDateTime leaseUntil) {
        this.projectSection = projectSection;
        this.holderUserId = holderUserId;
        this.leaseUntil = leaseUntil;
    }

    /**
     * 만료 시각과 같은 순간부터는 획득 가능한 상태다.
     */
    public boolean isActiveAt(LocalDateTime now) {
        return leaseUntil.isAfter(now);
    }

    /**
     * 기존 보유자의 재획득 또는 만료 후 새 보유자의 획득을 반영한다.
     */
    public void grantTo(Long holderUserId, LocalDateTime leaseUntil) {
        this.holderUserId = holderUserId;
        this.leaseUntil = leaseUntil;
    }
}
