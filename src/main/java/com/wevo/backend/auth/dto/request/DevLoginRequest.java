package com.wevo.backend.auth.dto.request;

/**
 * 개발 편의용 임시 로그인 요청. (local 프로파일 전용)
 *
 * <p>둘 다 선택 값이며, 생략하면 기본 개발 사용자로 로그인한다.
 *
 * @param email 로그인할(없으면 생성할) 사용자 이메일
 * @param name  표시 이름
 */
public record DevLoginRequest(
        String email,
        String name
) {
}
