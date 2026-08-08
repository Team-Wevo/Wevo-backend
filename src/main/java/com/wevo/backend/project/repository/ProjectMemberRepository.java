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

    /**
     * 이 사용자가 <b>보관되지 않은</b> 프로젝트의 OWNER 로 남아 있는지 확인한다. (회원 탈퇴 차단 조건)
     *
     * <p>MVP 에는 팀장 위임이 없으므로(정책서 §1.2) OWNER 가 그냥 빠지면 프로젝트를 삭제할 사람이
     * 사라진다. 보관(ARCHIVED)된 프로젝트는 이미 정리된 것이라 제외한다 — 포함하면 한 번이라도
     * 프로젝트를 만든 사용자는 영영 탈퇴할 수 없다.
     */
    @Query("SELECT COUNT(pm) > 0 FROM ProjectMember pm "
            + "WHERE pm.user.id = :userId "
            + "AND pm.role = com.wevo.backend.project.domain.ProjectMemberRole.OWNER "
            + "AND pm.project.status <> com.wevo.backend.project.domain.ProjectStatus.ARCHIVED")
    boolean existsActiveOwnedProject(@Param("userId") Long userId);

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
     * 프로젝트에서 특정 역할의 <b>탈퇴하지 않은</b> 멤버를 사용자와 함께 조회한다.
     *
     * <p>{@link #findAllWithUserByProjectIdAndRole} 과 달리 탈퇴 계정을 뺀다. 회원 탈퇴는 소프트
     * 삭제라(§3.3.3) 멤버 행이 그대로 남는데, "앞으로 검토·제출을 할 사람"을 세는 쪽에서는 그 사람이
     * 분모에 들어가면 안 된다 — 영영 도달하지 못하는 미완료가 생긴다.
     */
    @Query("SELECT pm FROM ProjectMember pm JOIN FETCH pm.user u "
            + "WHERE pm.project.id = :projectId AND pm.role = :role "
            + "AND u.status <> com.wevo.backend.user.domain.UserStatus.WITHDRAWN "
            + "ORDER BY pm.joinedAt ASC")
    List<ProjectMember> findActiveWithUserByProjectIdAndRole(@Param("projectId") Long projectId,
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
     * 프로젝트의 <b>탈퇴하지 않은</b> 멤버 전원을 사용자와 함께 조회한다. (정렬 규칙은
     * {@link #findAllWithUserByProjectId} 과 동일 — OWNER 우선 → 참여 시각 오름차순)
     *
     * <p>멤버 목록 API(§3.2.8)는 탈퇴자까지 <b>포함</b>해야 한다 — 그 사람이 쓴 의견의 작성자를
     * 화면이 {@code userId} 로 매칭하기 때문이다. 반면 진행 현황의 분모는 탈퇴자를 빼야 한다.
     * 두 용도가 갈리므로 조회를 따로 둔다.
     */
    @Query("SELECT pm FROM ProjectMember pm JOIN FETCH pm.user u "
            + "WHERE pm.project.id = :projectId "
            + "AND u.status <> com.wevo.backend.user.domain.UserStatus.WITHDRAWN "
            + "ORDER BY CASE WHEN pm.role = com.wevo.backend.project.domain.ProjectMemberRole.OWNER "
            + "THEN 0 ELSE 1 END, pm.joinedAt ASC")
    List<ProjectMember> findActiveWithUserByProjectId(@Param("projectId") Long projectId);

    /**
     * 프로젝트의 <b>탈퇴하지 않은</b> 참여자 수를 센다. (OWNER 포함)
     *
     * <p>1인 프로젝트 판정(§6.3.1)이 이 값을 쓴다. 탈퇴자를 세면 팀원이 모두 나간 프로젝트가
     * "혼자가 아님"으로 판정돼, 승인해 줄 사람이 없는데도 섹션 확정이 막힌다.
     */
    @Query("SELECT COUNT(pm) FROM ProjectMember pm "
            + "WHERE pm.project.id = :projectId "
            + "AND pm.user.status <> com.wevo.backend.user.domain.UserStatus.WITHDRAWN")
    long countActiveByProjectId(@Param("projectId") Long projectId);

    /**
     * 내가 멤버로 속한 프로젝트 목록을 조회한다.
     *
     * <p>{@code JOIN FETCH} 로 프로젝트를 함께 로딩해 N+1 을 방지한다.
     * 멤버십을 함께 반환하므로 프로젝트별 내 역할(role)도 추가 조회 없이 알 수 있다.
     *
     * <p>보관 처리된({@code ARCHIVED}) 프로젝트는 제외한다 — 삭제(§3.2.9)는 하드 삭제가 아니라
     * 상태 전이이므로, 목록에서 빼는 것이 "삭제됐다"는 사용자 기대를 충족하는 지점이다.
     * 개별 조회는 계속 동작한다.
     *
     * <p><b>정렬은 여기서 하지 않는다.</b> 목록의 정렬 기준은 "프로젝트에 속한 섹션들의 마지막
     * 활동 시각 중 최대값"(§3.2.2)이라 이 쿼리만으로는 정할 수 없다. 서비스가 섹션을 함께 읽어
     * 정렬하며, 여기서 다시 정렬하면 그 결과가 덮여 기준이 갈린다.
     */
    @Query("SELECT pm FROM ProjectMember pm JOIN FETCH pm.project p "
            + "WHERE pm.user.id = :userId "
            + "AND p.status <> com.wevo.backend.project.domain.ProjectStatus.ARCHIVED")
    List<ProjectMember> findAllWithProjectByUserId(@Param("userId") Long userId);
}
