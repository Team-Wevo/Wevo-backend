package com.wevo.backend.section.service;

import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 확정 상태·확정본의 <b>조회 전용 공개 창구</b>.
 *
 * <p>타 도메인(export 등)이 섹션 리포지토리·엔티티를 직접 참조하지 않고 이 서비스로만 접근하도록
 * 한다. 반환 타입도 엔티티가 아니라 값 객체({@link SectionConfirmationSummary}, {@link ConfirmedSectionContent})라, 섹션·초안의
 * 내부 구조가 바뀌어도 호출측이 영향받지 않는다.
 *
 * <p>권한 검사는 하지 않는다 — 호출측이 자신의 접근 규칙(존재 숨김 등)을 적용한 뒤 호출한다.
 */
@Service
@Transactional(readOnly = true)
public class SectionConfirmationQueryService {

    private final ProjectSectionRepository projectSectionRepository;
    private final SectionDraftRepository sectionDraftRepository;

    public SectionConfirmationQueryService(ProjectSectionRepository projectSectionRepository,
                                           SectionDraftRepository sectionDraftRepository) {
        this.projectSectionRepository = projectSectionRepository;
        this.sectionDraftRepository = sectionDraftRepository;
    }

    /**
     * 프로젝트의 섹션 확정 진행도를 반환한다.
     */
    public SectionConfirmationSummary getConfirmationSummary(Long projectId) {
        long totalCount = projectSectionRepository.countByProjectId(projectId);
        long confirmedCount = projectSectionRepository
                .countByProjectIdAndStatus(projectId, ProjectSectionStatus.CONFIRMED);

        return new SectionConfirmationSummary((int) totalCount, (int) confirmedCount);
    }

    /**
     * 확정된 섹션들의 확정본을 섹션 순서대로 반환한다.
     *
     * <p>각 섹션의 {@code confirmedVersion} 에 해당하는 초안만 담는다 — 확정 이후 본문이 수정됐어도
     * 최신본이 아니라 <b>확정 당시 본문</b>을 결과물에 담는다.
     */
    public List<ConfirmedSectionContent> findConfirmedContents(Long projectId) {
        return sectionDraftRepository.findConfirmedDraftsByProjectId(projectId).stream()
                .map(draft -> new ConfirmedSectionContent(
                        draft.getProjectSection().getSectionOrder(),
                        draft.getProjectSection().getTitle(),
                        draft.getContent()))
                .toList();
    }
}
