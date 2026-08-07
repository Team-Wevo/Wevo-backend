package com.wevo.backend.user.service;

/**
 * 탈퇴 가능 여부를 판단하기 위해 user 도메인이 project 도메인에 요구하는 <b>조회 전용 계약</b>이다.
 * (CLAUDE.md §6 — 도메인 간 접근)
 *
 * <p>인터페이스를 project 가 아니라 <b>user 쪽에 두는 이유</b>는 순환 의존을 막기 위해서다.
 * project 는 이미 {@code UserService} 를 참조하므로(project → user), user 가 {@code ProjectService}
 * 를 직접 참조하면 스프링이 생성자 주입 사이클로 기동에 실패한다. 필요한 쪽이 계약을 선언하고
 * project 가 구현하면 의존 방향이 project → user 한쪽으로만 남는다.
 */
public interface ProjectOwnershipQuery {

    /**
     * 이 사용자가 <b>보관되지 않은</b> 프로젝트의 OWNER 로 남아 있는지 확인한다.
     *
     * <p>보관(ARCHIVED)된 프로젝트는 제외한다 — 이미 정리된 프로젝트까지 탈퇴를 막으면
     * 사용자가 빠져나갈 방법이 영영 없어진다.
     */
    boolean hasActiveOwnedProject(Long userId);
}
