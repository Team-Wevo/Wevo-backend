package com.wevo.backend.section.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 섹션 상태 전이 이력 (COLLECTING → ... → CONFIRMED 등).
 */
@Entity
@Table(name = "section_status_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionStatusHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "event_type", length = 30)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private ProjectSectionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30)
    private ProjectSectionStatus toStatus;

    @Column
    private Integer version;

    @Builder
    private SectionStatusHistory(ProjectSection projectSection, User actor, String eventType,
                                 ProjectSectionStatus fromStatus, ProjectSectionStatus toStatus, Integer version) {
        this.projectSection = projectSection;
        this.actor = actor;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.version = version;
    }
}
