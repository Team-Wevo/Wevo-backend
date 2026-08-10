package com.wevo.backend.project.service;

import com.wevo.backend.section.domain.ProjectSection;

/**
 * 섹션 기준 참여자 검증을 통과했다는 <b>증거</b>. 검증된 섹션과 프로젝트 접근 증거를 함께 담는다.
 *
 * <p>섹션 기반 조회 API 가 인가 뒤에 필요로 하는 것은 셋 중 하나다 — 섹션 자체({@link #section()}),
 * 조회 서비스에 넘길 접근 증거({@link #access()}), 응답 범위를 가를 역할({@link #isOwner()}).
 * 셋을 각각의 진입점으로 나누면 같은 멤버십 조회 코드가 가드 안에 여러 벌 생기고, 호출측은 필요한
 * 값이 하나 늘 때마다 다른 메서드로 갈아타야 한다. 한 번의 조회 결과를 이 타입 하나로 돌려준다.
 *
 * <p>{@link SectionAccessGuard} 만 생성할 수 있어, 인가를 건너뛴 호출은 컴파일되지 않는다.
 */
public final class VerifiedParticipantSection {

    private final ProjectSection section;
    private final VerifiedProjectAccess access;

    private VerifiedParticipantSection(ProjectSection section, VerifiedProjectAccess access) {
        this.section = section;
        this.access = access;
    }

    /**
     * 검증된 섹션과 프로젝트 접근으로 증거를 만든다. {@link SectionAccessGuard}만 호출할 수 있다.
     */
    static VerifiedParticipantSection of(ProjectSection section, VerifiedProjectAccess access) {
        return new VerifiedParticipantSection(section, access);
    }

    /** 접근이 확인된 섹션. */
    public ProjectSection section() {
        return section;
    }

    /**
     * 프로젝트 접근 증거. {@link VerifiedProjectAccess} 를 인자로 요구하는 조회 서비스에 넘긴다.
     */
    public VerifiedProjectAccess access() {
        return access;
    }

    /**
     * 요청자가 이 프로젝트의 팀장(OWNER)인지. 응답 범위를 역할로 가르는 조회가 쓴다.
     *
     * <p>인가에서 이미 읽은 멤버십에서 그대로 꺼내므로 추가 조회가 없다. 역할을 얻자고 참여자 검사
     * 뒤에 역할 검사를 덧붙이면 멤버십을 두 번 읽게 되고, 두 번째 호출이 존재 숨김 밖에 있어 그 사이
     * 멤버십이 사라졌을 때 {@code 404 S001} 대신 {@code 403 P002} 가 새어 나간다.
     */
    public boolean isOwner() {
        return access.isOwner();
    }
}
