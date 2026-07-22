package com.wevo.backend.project.repository;

import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
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
     * 프로젝트에서 특정 역할의 멤버를 사용자와 함께 조회한다. (팀 검토 로스터 — MEMBER 목록)
     *
     * <p>{@code JOIN FETCH} 로 사용자를 함께 로딩해, 검토자 이름을 뽑을 때 N+1 이 나지 않게 한다.
     */
    @Query("SELECT pm FROM ProjectMember pm JOIN FETCH pm.user "
            + "WHERE pm.project.id = :projectId AND pm.role = :role")
    List<ProjectMember> findAllWithUserByProjectIdAndRole(@Param("projectId") Long projectId,
                                                          @Param("role") ProjectMemberRole role);

    /**
     * 프로젝트 멤버십을 프로젝트와 함께 조회한다.
     *
     * <p>{@code JOIN FETCH} 로 프로젝트를 함께 로딩해, 멤버십 검증 직후 프로젝트 정보를 쓰는
     * 경로(최종 결과물 조회 등)에서 {@code getProject()} 지연 로딩 쿼리가 추가로 나가지 않게 한다.
     * 프로젝트가 필요 없는 경로는 {@link #findByProjectIdAndUserId} 를 그대로 쓴다.
     */
    @Query("SELECT pm FROM ProjectMember pm JOIN FETCH pm.project "
            + "WHERE pm.project.id = :projectId AND pm.user.id = :userId")
    Optional<ProjectMember> findWithProjectByProjectIdAndUserId(@Param("projectId") Long projectId,
                                                                @Param("userId") Long userId);

    /**
     * 프로젝트의 모든 멤버를 사용자와 함께 조회한다. (멤버 목록 — API_SPEC §3.2.8)
     *
     * <p>정렬은 <b>OWNER 우선 → 같은 역할이면 참여 시각 오름차순</b>이다. 팀장이 항상 맨 앞에 오므로
     * 클라이언트가 별도 정렬 없이 그대로 표시할 수 있다. 역할은 문자열로 저장돼(`MEMBER` &lt; `OWNER`)
     * 이름순 정렬이 의도와 반대이므로 {@code CASE} 로 우선순위를 명시한다.
     *
     * <p>{@code JOIN FETCH} 로 사용자를 함께 로딩해 이름·프로필 조회에서 N+1 이 나지 않게 한다.
     */
    @Query("SELECT pm FROM ProjectMember pm JOIN FETCH pm.user "
            + "WHERE pm.project.id = :projectId "
            + "ORDER BY CASE WHEN pm.role = com.wevo.backend.project.domain.ProjectMemberRole.OWNER "
            + "THEN 0 ELSE 1 END, pm.joinedAt ASC")
    List<ProjectMember> findAllWithUserByProjectId(@Param("projectId") Long projectId);

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
