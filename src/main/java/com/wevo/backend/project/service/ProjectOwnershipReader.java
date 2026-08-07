package com.wevo.backend.project.service;

import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.user.service.ProjectOwnershipQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * user 도메인이 요구하는 {@link ProjectOwnershipQuery} 를 project 도메인에서 구현한다.
 * (CLAUDE.md §6 — 도메인 간 접근)
 *
 * <p>{@code ProjectService} 가 직접 구현하지 않고 별도 클래스로 둔 이유는 <b>순환 의존</b> 때문이다.
 * {@code ProjectService} 는 {@code UserService} 를 참조하는데, {@code UserService} 가 이 인터페이스를
 * 주입받으므로 같은 클래스가 구현하면 스프링이 생성자 주입 사이클로 기동에 실패한다. 이 클래스는
 * 리포지토리만 참조해 그 고리를 끊는다.
 */
@Service
@Transactional(readOnly = true)
public class ProjectOwnershipReader implements ProjectOwnershipQuery {

    private final ProjectMemberRepository projectMemberRepository;

    public ProjectOwnershipReader(ProjectMemberRepository projectMemberRepository) {
        this.projectMemberRepository = projectMemberRepository;
    }

    @Override
    public boolean hasActiveOwnedProject(Long userId) {
        return projectMemberRepository.existsActiveOwnedProject(userId);
    }
}
