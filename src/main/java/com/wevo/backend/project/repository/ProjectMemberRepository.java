package com.wevo.backend.project.repository;

import com.wevo.backend.project.domain.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    /**
     * 특정 프로젝트에서 해당 사용자의 멤버십(역할 포함)을 조회한다. (권한 검증용)
     */
    Optional<ProjectMember> findByProject_IdAndUser_Id(Long projectId, Long userId);
}
