package com.wevo.backend.user.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    /** 탈퇴 후 모든 화면에 표시되는 이름. (원래 이름을 지우고 이 값으로 덮는다) */
    public static final String WITHDRAWN_NAME = "탈퇴한 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 255, unique = true)
    private String email;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private UserStatus status;

    @Builder
    private User(String name, String email, String profileImageUrl, UserStatus status) {
        this.name = name;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.status = status;
    }

    /**
     * 표시 이름을 변경한다. (제품 정책서 §1.1 — 로그인 후 마이페이지에서 표시 이름 수정 가능)
     *
     * <p>이메일(계정 식별 정보)·프로필 사진(provider URL)은 사용자가 직접 수정하지 않는다.
     */
    public void updateName(String name) {
        this.name = name;
    }

    /**
     * 회원 탈퇴를 처리한다. (API_SPEC §3.3.3 — 소프트 삭제)
     *
     * <p>행을 지우지 않는 이유는 의견·초안·검토가 <b>팀 공동 결과물</b>이기 때문이다. 작성자 행이
     * 사라지면 남은 팀원의 작업물까지 참조가 끊겨 프로젝트가 깨진다. 그래서 행은 남기고
     * <b>개인 식별정보만</b> 지운다 — 표시 이름은 {@link #WITHDRAWN_NAME}, 이메일과 프로필
     * 사진은 {@code null} 이다.
     *
     * <p>이 방식이라 응답 DTO 를 한 곳도 고치지 않아도 모든 화면에서 자동으로 마스킹된다.
     * 멤버 목록에서 탈퇴자를 빼지 않는 것도 같은 이유다 — 빼면 그 사람이 쓴 의견의 작성자를
     * 화면이 찾지 못한다.
     *
     * <p>소셜 연결({@code auth_accounts}) 끊기와 Refresh Token 폐기는 auth 도메인이
     * {@code UserWithdrawnEvent} 를 받아 처리한다.
     */
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.name = WITHDRAWN_NAME;
        this.email = null;
        this.profileImageUrl = null;
    }

    public boolean isWithdrawn() {
        return this.status == UserStatus.WITHDRAWN;
    }
}
