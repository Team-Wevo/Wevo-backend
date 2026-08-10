package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;

/**
 * 프로젝트 멤버십 검증을 <b>이미 통과했다는 증거</b>.
 *
 * <p>타 도메인의 조회 전용 공개 서비스(예:
 * {@code SectionConfirmationQueryService})는 프로젝트 ID 대신 이 타입을 받는다.
 * 생성자와 정적 팩터리가 패키지 전용이라 {@link ProjectAccessGuard}를 거치지 않고는
 * 만들 수 없으므로, "호출측이 권한 검사를 했어야 한다"는 계약이 주석이 아니라
 * <b>타입으로 강제</b>된다. 호출측이 인가를 빠뜨리면 컴파일되지 않는다.
 */
public final class VerifiedProjectAccess {

    private final ProjectMember membership;

    private VerifiedProjectAccess(ProjectMember membership) {
        this.membership = membership;
    }

    /**
     * 검증된 멤버십으로 증거를 만든다. {@link ProjectAccessGuard}만 호출할 수 있다.
     */
    static VerifiedProjectAccess of(ProjectMember membership) {
        return new VerifiedProjectAccess(membership);
    }

    public Long projectId() {
        return membership.getProject().getId();
    }

    /**
     * 접근이 확인된 프로젝트. 멤버십 조회 시 함께 로딩되므로 추가 쿼리가 나가지 않는다.
     *
     * <p><b>타 도메인은 이 엔티티를 직접 쓰지 않는다</b> — project 도메인이 공개한 조회 창구
     * ({@link ProjectOutputQueryService}, {@link ProjectAiContextQueryService})가 돌려주는 값
     * 객체를 받는다. 엔티티가 타 도메인의 서비스·DTO 계층까지 새면 프로젝트의 내부 구조 변경이
     * 그대로 전파되기 때문이다. (CLAUDE.md §6)
     */
    public Project project() {
        return membership.getProject();
    }

    public ProjectMember membership() {
        return membership;
    }
}
