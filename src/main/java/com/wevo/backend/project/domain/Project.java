package com.wevo.backend.project.domain;

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

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

    /** 프로젝트 최대 인원 — 팀장 1 + 팀원 3. (제품 정책서 §2.1) */
    public static final int MAX_MEMBERS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "idea_text", columnDefinition = "TEXT")
    private String ideaText;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", length = 30, nullable = false)
    private OutputType resultType;

    @Column(length = 200, nullable = false)
    private String audience;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private ProjectStatus status;

    @Builder
    private Project(User owner, String title, String description, String ideaText,
                    OutputType resultType, String audience, ProjectStatus status) {
        this.owner = owner;
        this.title = title;
        this.description = description;
        this.ideaText = ideaText;
        this.resultType = resultType;
        this.audience = audience;
        this.status = status;
    }

    /**
     * 프로젝트를 보관 처리한다. (API_SPEC §3.2.9 — 하드 삭제가 아니라 상태 전이)
     *
     * <p>여러 팀원이 작성한 의견·초안·검토가 이 프로젝트에 매달려 있어 실제 삭제는 남의 결과물까지
     * 되돌릴 수 없게 지운다. 목록에서 제외하는 것으로 "삭제" 요구를 충족하고 데이터는 보존한다.
     *
     * <p>이미 보관된 프로젝트에 다시 호출해도 상태를 바꾸지 않는다 — 호출측이 멱등하게 응답할 수 있다.
     */
    public void archive() {
        this.status = ProjectStatus.ARCHIVED;
    }

    /**
     * 보관 처리된 프로젝트인지. (목록 제외·중복 삭제 판정에 사용)
     */
    public boolean isArchived() {
        return this.status == ProjectStatus.ARCHIVED;
    }
}
