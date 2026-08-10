package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.section.domain.ProjectSection;

/**
 * 참여자 검증을 통과한 섹션과 <b>요청자의 역할</b>을 함께 담은 증거.
 *
 * <p>"참여자면 통과하되 응답 범위는 팀장(OWNER)에게만 넓히는" 조회 API를 위한 타입이다.
 * 의견 수집 현황(정책서 §4.5)처럼 팀원은 집계만, 팀장은 멤버별 상태까지 보는 화면이 여기 해당한다.
 *
 * <p> {@code requireParticipantSection} 뒤에 역할 검사를 한 번 더 붙이면 멤버십을
 * 두 번 조회하게 되고, 두 번째 호출이 존재 숨김 밖에 있어 그 사이 멤버십이 사라졌을 때
 * {@code 404 S001} 대신 {@code 403 P002}가 새어 나간다({@link SectionAccessGuard} 참고).
 * 인가에서 이미 읽은 멤버십 행에서 역할을 그대로 꺼내 한 번의 조회로 끝낸다.
 *
 * <p>멤버십 <b>엔티티는 밖으로 내보내지 않는다</b> — 타 도메인이 필요한 건 "팀장인가" 하나이므로
 * 값으로만 노출한다. (CLAUDE.md §6)
 */
public final class VerifiedParticipantSection {

    private final ProjectSection section;
    private final boolean owner;

    private VerifiedParticipantSection(ProjectSection section, boolean owner) {
        this.section = section;
        this.owner = owner;
    }

    /**
     * 검증된 섹션과 멤버십으로 증거를 만든다. {@link SectionAccessGuard}만 호출할 수 있다.
     */
    static VerifiedParticipantSection of(ProjectSection section, ProjectMember membership) {
        return new VerifiedParticipantSection(
                section, membership.getRole() == ProjectMemberRole.OWNER);
    }

    /** 접근이 확인된 섹션. */
    public ProjectSection section() {
        return section;
    }

    /** 요청자가 이 프로젝트의 팀장(OWNER)인지. */
    public boolean isOwner() {
        return owner;
    }
}
