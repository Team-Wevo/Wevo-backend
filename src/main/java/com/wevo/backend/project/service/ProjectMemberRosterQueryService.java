package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 멤버 로스터를 타 도메인에 공개하는 조회 전용 서비스.
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
     * 프로젝트의 전체 멤버를 <b>OWNER 우선 → 참여 시각 오름차순</b>으로 조회한다.
     *
     * <p>역할과 무관하게 전원을 담는다. 역할별 로스터가 필요하면 별도 메서드를 추가하고,
     * 이 메서드의 의미(= 프로젝트 참여자 전원)를 바꾸지 않는다.
     */
    public List<ProjectMemberSummary> getRoster(Long projectId) {
        return projectMemberRepository.findAllWithUserByProjectId(projectId).stream()
                .map(ProjectMemberRosterQueryService::toSummary)
                .toList();
    }

    private static ProjectMemberSummary toSummary(ProjectMember member) {
        return new ProjectMemberSummary(
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getProfileImageUrl());
    }
}
