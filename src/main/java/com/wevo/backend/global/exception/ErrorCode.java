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

    // Opinion
    OPINION_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "의견을 찾을 수 없습니다."),
    OPINION_COLLECTION_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "O003", "의견 수집이 마감되었습니다."),

    // AI
    AI_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "AI001", "AI 요청 형식이 올바르지 않습니다."),
    AI_PROVIDER_AUTHENTICATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI002", "AI 제공자 인증 설정을 확인해주세요."),
    AI_PROVIDER_PERMISSION_DENIED(HttpStatus.INTERNAL_SERVER_ERROR, "AI003", "AI 제공자 접근 권한을 확인해주세요."),
    AI_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "AI004", "AI 요청이 많습니다. 잠시 후 다시 시도해주세요."),
    AI_PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI005", "AI 응답 시간이 초과되었습니다."),
    AI_PROVIDER_OVERLOADED(HttpStatus.SERVICE_UNAVAILABLE, "AI006", "AI 제공자가 혼잡합니다. 잠시 후 다시 시도해주세요."),
    AI_MODEL_NOT_AVAILABLE(HttpStatus.BAD_GATEWAY, "AI007", "설정된 AI 모델을 사용할 수 없습니다."),
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI008", "AI 제공자에 일시적으로 연결할 수 없습니다."),
    AI_PROVIDER_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI009", "AI 응답을 처리할 수 없습니다."),
    AI_AUDIT_LOG_PERSISTENCE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI010", "AI 요청 감사 로그를 저장할 수 없습니다."),
    AI_USAGE_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "AI011", "AI 사용 로그를 찾을 수 없습니다."),
    AI_PROMPT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "AI012", "AI 프롬프트를 찾을 수 없습니다."),
    AI_PROMPT_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "AI013", "AI 프롬프트 설정이 올바르지 않습니다."),
    AI_PROMPT_VARIABLE_INVALID(HttpStatus.BAD_REQUEST, "AI014", "AI 프롬프트 입력값이 올바르지 않습니다."),
    AI_PROVIDER_REFUSAL(HttpStatus.UNPROCESSABLE_ENTITY, "AI015", "AI 제공자가 요청 처리를 거절했습니다."),
    AI_PROVIDER_MAX_TOKENS(HttpStatus.BAD_GATEWAY, "AI016", "AI 응답이 출력 한도에 도달해 완료되지 않았습니다."),
    AI_STRUCTURED_OUTPUT_JSON_PARSE_FAILED(HttpStatus.BAD_GATEWAY, "AI017", "AI 응답 JSON을 해석할 수 없습니다."),
    AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED(HttpStatus.BAD_GATEWAY, "AI018", "AI 응답이 출력 형식을 충족하지 않습니다."),
    AI_STRUCTURED_OUTPUT_CONVERSION_FAILED(HttpStatus.BAD_GATEWAY, "AI019", "AI 응답을 결과 타입으로 변환할 수 없습니다."),
    AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED(HttpStatus.BAD_GATEWAY, "AI020", "AI 응답의 참조값이 유효하지 않습니다."),
    AI_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "AI021", "AI 작업을 찾을 수 없습니다."),
    AI_JOB_INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "AI022", "허용되지 않은 AI 작업 상태 전이입니다."),
    AI_JOB_RETRY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "AI023", "AI 작업 재실행 한도를 초과했습니다."),
    AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "AI999", "AI 처리 중 오류가 발생했습니다."),

    // Review
    REVIEW_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "검토 링크를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
