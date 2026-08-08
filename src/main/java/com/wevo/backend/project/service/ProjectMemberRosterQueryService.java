package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 멤버 로스터를 타 도메인에 공개하는 조회 전용 서비스.
 *
 * <p><b>이 서비스가 돌려주는 것은 "지금 팀에 있는 사람"이며 탈퇴 계정은 빠진다.</b> 회원 탈퇴는
 * 소프트 삭제라(§3.3.3) 멤버 행이 그대로 남지만, 여기서 나가는 값은 전부 "앞으로 의견을 내거나
 * 검토를 할 사람"의 분모로 쓰인다. 탈퇴자를 세면 영영 채워지지 않는 미완료가 생긴다.
 *
 * <p>탈퇴자까지 <b>포함</b>해야 하는 곳은 멤버 목록 API(§3.2.8) 하나뿐이다 — 그 화면은 작성자를
 * {@code userId} 로 매칭하므로 과거 참여자도 남아 있어야 한다. 그 경로는 이 서비스를 쓰지 않고
 * {@code ProjectService} 가 리포지토리를 직접 호출한다.
 *
 * <p>"이 프로젝트에 누가 있는가"는 의견 수집 현황(§4.5)·팀 검토 현황(§6.1)처럼 여러 도메인이
 * 분모로 삼는 정보다. 각 도메인이 {@code ProjectMemberRepository} 를 직접 주입하면 project 도메인의
 * 저장 구조에 묶이므로({@code CLAUDE.md} §6 — 타 도메인의 리포지토리·엔티티 직접 참조 금지),
 * project 도메인이 {@link ProjectMemberSummary} 경계 타입으로만 내보낸다.
 *
 * <p><b>권한 검사는 하지 않는다</b> — 호출자가 이미 {@link SectionAccessGuard}·
 * {@link ProjectAccessGuard} 로 접근 권한을 확인한 뒤 쓰는 내부 조회다. 컨트롤러가 이 서비스를
 * 직접 호출하지 않도록 한다.
 */
@Service
@Transactional(readOnly = true)
public class ProjectMemberRosterQueryService {

    private final ProjectMemberRepository projectMemberRepository;

    public ProjectMemberRosterQueryService(ProjectMemberRepository projectMemberRepository) {
        this.projectMemberRepository = projectMemberRepository;
    }

    /**
     * 프로젝트의 <b>탈퇴하지 않은 참여자 전원</b>(OWNER + MEMBER)을 OWNER 우선 → 참여 시각 오름차순으로 조회한다.
     *
     * <p>의견 수집 현황(§4.5)처럼 "팀장도 작성·제출하는 참여자"인 기능의 분모다.
     */
    public List<ProjectMemberSummary> getParticipants(Long projectId) {
        return projectMemberRepository.findActiveWithUserByProjectId(projectId).stream()
                .map(ProjectMemberRosterQueryService::toSummary)
                .toList();
    }

    /**
     * 프로젝트의 <b>탈퇴하지 않은 팀원(MEMBER)만</b> 조회한다. 팀장(OWNER)은 빠진다.
     *
     * <p>팀 검토(§6.1)처럼 "팀장은 검토자가 아니라 확정 실행자"인 기능의 분모다.
     * {@link #getParticipants} 와 결과가 다른 것은 <b>의도된 차이</b>이므로 한쪽으로 통일하지 않는다.
     */
    public List<ProjectMemberSummary> getTeamMembers(Long projectId) {
        return projectMemberRepository
                .findActiveWithUserByProjectIdAndRole(projectId, ProjectMemberRole.MEMBER).stream()
                .map(ProjectMemberRosterQueryService::toSummary)
                .toList();
    }

    /**
     * 프로젝트의 <b>탈퇴하지 않은</b> 참여자 수(OWNER 포함)를 센다.
     *
     * <p>1인 프로젝트 판정(§6.3.1 — 팀장 혼자면 팀원 동의 조건 면제)처럼 명단이 아니라 인원수만
     * 필요한 경로가 로스터 전체를 읽지 않도록 분리해 둔다.
     */
    public long countParticipants(Long projectId) {
        return projectMemberRepository.countActiveByProjectId(projectId);
    }

    private static ProjectMemberSummary toSummary(ProjectMember member) {
        return new ProjectMemberSummary(
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getProfileImageUrl());
    }
}
