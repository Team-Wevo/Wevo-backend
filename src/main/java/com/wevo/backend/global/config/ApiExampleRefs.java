package com.wevo.backend.global.config;

/**
 * 공통 실패 응답 예제의 참조 문자열.
 *
 * <p>실제 예제 본문은 {@link OpenApiConfig} 가 {@code components.examples} 에 한 번만 등록하고,
 * 컨트롤러는 이 상수로 가리키기만 한다. 애노테이션 값은 컴파일 타임 상수여야 하므로 참조 경로를
 * 문자열로 직접 쓰게 되는데, 그러면 오타가 나도 컴파일이 통과하고 Swagger 화면에서 예제만
 * 조용히 비어 보인다. 상수로 모아 오타 가능성을 한 곳으로 줄인다.
 *
 * <p>이름은 {@code OpenApiConfig.COMMON_FAILURE_EXAMPLES} 의 키와 반드시 일치해야 한다.
 */
public final class ApiExampleRefs {

    private static final String PREFIX = "#/components/examples/";

    /** 요청 검증 실패 — {@code C001}, 필드 사유가 {@code errors} 에 담긴다. */
    public static final String INVALID_INPUT = PREFIX + "invalidInput";

    /** 인증 실패 — {@code A001}. */
    public static final String UNAUTHORIZED = PREFIX + "unauthorized";

    /** 권한 부족 — {@code A002}, 멤버지만 OWNER 가 아닌 경우. */
    public static final String FORBIDDEN = PREFIX + "forbidden";

    /** 프로젝트 없음 또는 비멤버 — {@code P001}, 존재 숨김. */
    public static final String PROJECT_NOT_FOUND = PREFIX + "projectNotFound";

    /** 섹션 없음 또는 비멤버 — {@code S001}, 존재 숨김. */
    public static final String SECTION_NOT_FOUND = PREFIX + "sectionNotFound";

    /** 상태·버전 충돌 — {@code C003}. */
    public static final String CONFLICT = PREFIX + "conflict";

    private ApiExampleRefs() {
    }
}
