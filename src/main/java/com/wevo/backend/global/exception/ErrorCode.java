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
    INVITE_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "P003", "유효하지 않은 초대 링크입니다."),
    PROJECT_MEMBER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "P004", "프로젝트 최대 인원을 초과했습니다."),

    // Section
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "섹션을 찾을 수 없습니다."),
    INVALID_SECTION_STATUS_TRANSITION(HttpStatus.CONFLICT, "S002", "허용되지 않은 섹션 상태 전이입니다."),
    SECTION_DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "S003", "초안이 없습니다."),
    DRAFT_LEASE_HELD_BY_OTHER(HttpStatus.CONFLICT, "S004", "다른 사용자가 편집 중입니다."),
    DRAFT_LEASE_NOT_HELD(HttpStatus.CONFLICT, "S005", "편집권이 없습니다."),

    // Opinion
    OPINION_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "의견을 찾을 수 없습니다."),
    OPINION_COLLECTION_CLOSED(HttpStatus.CONFLICT, "O003", "의견 수집이 마감되었습니다."),
    NO_SUBMITTED_OPINION(HttpStatus.CONFLICT, "O004", "제출된 의견이 없습니다."),

    // Issue
    ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "I001", "쟁점을 찾을 수 없습니다."),
    ISSUE_CONFLICT_UNDECIDED(HttpStatus.CONFLICT, "I002", "결정되지 않은 충돌 쟁점이 있습니다."),
    EVIDENCE_REQUEST_ALREADY_SENT(HttpStatus.CONFLICT, "I003", "추가 근거 요청은 쟁점당 1회만 가능합니다."),

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
    AI_JOB_INPUT_CHANGED(HttpStatus.CONFLICT, "AI024", "입력이 변경되어 결과를 폐기했습니다."),
    AI_REQUEST_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI025", "AI 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),
    AI_PROJECT_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI026", "프로젝트의 AI 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),
    AI_PROJECT_COST_BUDGET_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI027", "프로젝트의 AI 비용 한도를 초과했습니다. 한도가 갱신된 후 다시 시도해주세요."),
    AI_GUARDRAIL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI028", "AI 사용 한도를 확인할 수 없어 새 AI 요청을 시작할 수 없습니다."),
    AI_PRICING_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AI029", "AI 비용 정책을 확인할 수 없어 새 AI 요청을 시작할 수 없습니다."),
    AI_SYNTHESIS_RESULT_REQUIRED(HttpStatus.CONFLICT, "AI030", "AI 의견 정리 결과가 필요합니다."),
    AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "AI999", "AI 처리 중 오류가 발생했습니다."),

    // Review
    REVIEW_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "검토 링크를 찾을 수 없습니다."),
    REVIEW_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "R002", "이미 검토를 제출했어요."),
    REVIEW_SUBMISSION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "R003", "이 링크는 검토 정원(20명)이 모두 찼어요."),
    REVIEW_LINK_OUTDATED(HttpStatus.CONFLICT, "R004", "외부 검토 링크가 이전 본문 기준이라 만료됐어요."),
    // 상수명이 수동 종료 성공 code("REVIEW_LINK_CLOSED", API_SPEC §3.5.9)와 겹치지 않게 ALREADY 를 붙인다.
    // ErrorCode 상수명은 개발자용이고 외부에 노출되는 값은 "R005" 뿐이다. (CLAUDE.md §5.8)
    REVIEW_LINK_ALREADY_CLOSED(HttpStatus.CONFLICT, "R005", "종료된 외부 검토 링크예요."),
    TEAM_REVIEW_SECTION_NOT_REVIEWING(HttpStatus.CONFLICT, "R006", "검토 단계(REVIEWING)의 섹션만 검토할 수 있습니다."),
    TEAM_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "R007", "팀 검토를 찾을 수 없습니다."),
    TEAM_REVIEW_NOT_CHANGES_REQUESTED(HttpStatus.CONFLICT, "R008", "수정 요청 상태의 검토만 처리할 수 있습니다."),
    REVIEW_LINK_DRAFT_REQUIRED(HttpStatus.CONFLICT, "R009", "본문 초안이 없어 외부 검토 링크를 발급할 수 없습니다."),
    REVIEW_LINK_AUTHOR_INTENT_REQUIRED(HttpStatus.CONFLICT, "R010", "확정된 작성자 의도가 없어 외부 검토 링크를 발급할 수 없습니다."),
    // 수동 종료(API_SPEC §3.5.9) 전용 — ACTIVE 링크만 종료할 수 있고, 이미 끝난 링크는 사유를 구분해 거절한다.
    // R004·R005 를 재사용하지 않는 이유: 그 둘은 외부 검토자의 제출 거절 문구라 안내 대상과 맥락이 다르다.
    // 팀장 화면은 두 사유를 각각 다른 문구로 보여줘야 하므로 클라이언트가 분기할 코드가 필요하다. (CLAUDE.md §5.8)
    REVIEW_LINK_CLOSE_ALREADY_CLOSED(HttpStatus.CONFLICT, "R011", "이미 종료된 링크입니다."),
    REVIEW_LINK_CLOSE_ALREADY_OUTDATED(HttpStatus.CONFLICT, "R012", "이미 만료된 링크입니다."),

    // Export
    FINAL_OUTPUT_ASSEMBLY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "E001", "완성본을 조립할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
