package com.wevo.backend.section.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 정리 결과 반영에 필요한 섹션 상태 변경을 section 도메인 안에서 제공하는 경계. (CLAUDE.md §6 —
 * ai 도메인이 {@link ProjectSection}을 직접 쓰지 않고 이 서비스를 통해서만 상태를 바꾼다.)
 */
@Service
public class SectionSynthesisStateService {

    private final ProjectSectionRepository projectSectionRepository;

    public SectionSynthesisStateService(ProjectSectionRepository projectSectionRepository) {
        this.projectSectionRepository = projectSectionRepository;
    }

    /**
     * 정리 성공 반영 시 재정리 필요 플래그를 해제한다. (§3.8.1 — {@code synthesisStale=false})
     *
     * <p>반드시 정리 결과 저장과 <b>같은 트랜잭션</b> 안에서 호출한다({@link Propagation#MANDATORY}) —
     * AI 작업 완료 처리·정리 세트 저장과 원자적으로 반영되어, 세트만 저장되고 플래그는 남는
     * 어긋난 상태를 만들지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clearSynthesisStale(Long projectSectionId) {
        ProjectSection section = projectSectionRepository.findByIdForUpdate(projectSectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
        section.clearSynthesisStale();
    }

    /**
     * 섹션의 현재 의견 수집 마감 세대를 조회한다. AI 정리 입력 스냅샷의 경계값으로 쓰인다(§3.8.1) —
     * 의견·답변이 같아도 재오픈으로 세대가 바뀌면 이전 마감의 결과를 재사용하지 않게 한다.
     */
    @Transactional(readOnly = true)
    public long currentOpinionGateGeneration(Long projectSectionId) {
        return projectSectionRepository.findById(projectSectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND))
                .getOpinionGateGeneration();
    }
}
