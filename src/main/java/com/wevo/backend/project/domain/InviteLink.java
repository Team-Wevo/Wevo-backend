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
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    /**
     * 원문 토큰의 SHA-256 해시. <b>원문은 저장하지 않는다.</b>
     *
     * <p>DB 가 유출돼도 초대 링크를 그대로 쓸 수 없게 하기 위해서다. 원문은
     * {@link com.wevo.backend.project.service.InviteTokenFactory} 가 프로젝트 ID 로부터 다시
     * 계산하므로, 해시만 저장해도 같은 링크를 계속 돌려줄 수 있다.
     *
     * <p>공개 조회 키이므로 DB에서도 중복을 허용하지 않는다.
     */
    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Builder
    private InviteLink(Project project, User createdBy, String tokenHash,
                       LocalDateTime expiresAt, Boolean isActive) {
        this.project = project;
        this.createdBy = createdBy;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.isActive = isActive;
    }

    /**
     * 재사용 초대 링크를 발급한다. MVP에서는 만료를 두지 않으므로 {@code expiresAt=null},
     * 활성 상태({@code isActive=true})로 생성한다. (제품 정책서 §2.1 — 만료·비활성화는 MVP 이후)
     *
     * @param tokenHash 원문 토큰의 SHA-256 해시. 원문을 넘기지 않도록 호출측에서 해시해 전달한다.
     */
    public static InviteLink issue(Project project, User createdBy, String tokenHash) {
        return InviteLink.builder()
                .project(project)
                .createdBy(createdBy)
                .tokenHash(tokenHash)
                .expiresAt(null)
                .isActive(true)
                .build();
    }

    /**
     * 초대 링크를 비활성화한다. (API_SPEC §3.2.9 — 프로젝트 보관 시 새 멤버 합류 차단)
     *
     * <p>토큰 행은 지우지 않는다. 이미 공유된 링크로 접근하면 미리보기·참여가
     * {@code INVITE_LINK_NOT_FOUND}(P003)로 거부된다.
     *
     * <p>사용자가 개별 링크를 끄는 기능(정책서 §2.1 — MVP 이후)과는 별개이며,
     * 여기서는 프로젝트 보관의 부수 효과로만 호출한다.
     */
    public void deactivate() {
        this.isActive = false;
    }
}
