package com.wevo.backend.project.repository;

import com.wevo.backend.project.domain.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    long countByProjectId(Long projectId);

    /**
     * 내가 멤버로 속한 프로젝트 목록을 최신순으로 조회한다.
     *
     * <p>{@code JOIN FETCH} 로 프로젝트를 함께 로딩해 N+1 을 방지한다.
     * 멤버십을 함께 반환하므로 프로젝트별 내 역할(role)도 추가 조회 없이 알 수 있다.
     */
    @Query("SELECT pm FROM ProjectMember pm JOIN FETCH pm.project p "
            + "WHERE pm.user.id = :userId ORDER BY p.createdAt DESC")
    List<ProjectMember> findAllWithProjectByUserId(@Param("userId") Long userId);
}
