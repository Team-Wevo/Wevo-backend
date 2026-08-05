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

    /**
     * 프로젝트 이름을 바꾼다. (API_SPEC §3.2.10)
     *
     * <p>생성 시 이름을 받지 않아 서버 기본값이 저장되므로(§3.2.1) 이 메서드가 유일한 개명 수단이다.
     * 이름은 목록·상세 화면에 항상 노출되는 값이라 빈 값으로 만들 수 없다 — 호출측이 공백을 걸러
     * 넘긴다고 가정하지 않고 여기서도 무시한다.
     */
    public void rename(String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        this.title = title.trim();
    }

    /**
     * 프로젝트 설명을 바꾼다. (API_SPEC §3.2.10)
     *
     * <p>이름과 달리 <b>빈 값을 허용</b>한다 — 설명은 없어도 되는 값이라 지우기가 정상 동작이다.
     * 공백뿐인 문자열은 {@code null} 로 정규화해, 저장된 값이 "없음"인지 "공백"인지 갈리지 않게 한다.
     * (§1.4 — {@code null} 필드는 응답에서 생략된다)
     */
    public void changeDescription(String description) {
        this.description = (description == null || description.isBlank())
                ? null
                : description.trim();
    }
}
