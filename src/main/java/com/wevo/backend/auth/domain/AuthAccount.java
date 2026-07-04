package com.wevo.backend.auth.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auth_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AuthProvider provider;

    @Column(name = "provider_user_id", length = 100)
    private String providerUserId;

    @Builder
    private AuthAccount(
            User user,
            AuthProvider provider,
            String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }
}
