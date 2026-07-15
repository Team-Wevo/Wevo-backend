package com.wevo.backend.project.repository;

import com.wevo.backend.project.domain.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 프로젝트 행을 배타 잠금(PESSIMISTIC_WRITE)으로 조회한다.
     *
     * <p>초대 링크 참여에서 "인원 수 확인 → 멤버 저장" 사이를 직렬화해, 동시 참여가
     * 최대 인원({@link Project#MAX_MEMBERS})을 넘겨 저장되는 경합을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);
}
