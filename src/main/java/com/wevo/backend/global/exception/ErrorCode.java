package com.wevo.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력입니다."),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "C002", "업무 규칙을 위반했습니다."),
    CONFLICT(HttpStatus.CONFLICT, "C003", "요청이 현재 상태와 충돌합니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 오류가 발생했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A005", "유효하지 않은 리프레시 토큰입니다."),
    UNSUPPORTED_AUTH_PROVIDER(HttpStatus.BAD_REQUEST, "A006", "지원하지 않는 로그인 제공자입니다."),
    OAUTH_PROVIDER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "A007", "소셜 로그인 처리 중 오류가 발생했습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 다른 방식으로 가입된 이메일입니다."),

    // Project
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "프로젝트를 찾을 수 없습니다."),
    NOT_PROJECT_MEMBER(HttpStatus.FORBIDDEN, "P002", "프로젝트 멤버가 아닙니다."),

    // Section
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "섹션을 찾을 수 없습니다."),
    INVALID_SECTION_STATUS_TRANSITION(HttpStatus.CONFLICT, "S002", "허용되지 않은 섹션 상태 전이입니다."),

    // Review
    REVIEW_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "검토 링크를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
