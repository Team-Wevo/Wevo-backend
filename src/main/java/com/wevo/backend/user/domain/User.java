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
}
