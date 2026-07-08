package com.wevo.backend.global.security;

/**
 * JWT 용도 구분. Access Token 과 Refresh Token 을 서로 다른 용도로만 사용하도록 강제한다.
 *
 * <p>토큰의 {@code type} 클레임에 저장되며, 인증 필터는 {@link #ACCESS} 만,
 * 재발급은 {@link #REFRESH} 만 허용한다. (예: Refresh Token 을 Bearer 로 사용하는 것을 차단)
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
