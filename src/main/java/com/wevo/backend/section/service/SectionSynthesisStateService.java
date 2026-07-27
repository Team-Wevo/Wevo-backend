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
     * 정리 결과를 반영하는 트랜잭션에서 섹션 행을 <b>배타 잠금</b>한다. (§3.8.1 오래된 결과 반영 방지)
     *
     * <p>의견 임시저장·제출·수집 마감/재오픈이 모두 같은 섹션 행을 배타 잠금하므로, 완료 처리가 이
     * 잠금을 먼저 잡으면 "현재 입력 재대조 → 결과 저장"이 입력 변경과 직렬화된다. 잠금 없이 재대조하면
     * 대조와 저장 사이에 커밋된 입력 변경을 놓쳐 낡은 결과가 저장될 수 있다.
     *
     * <p>반드시 결과 저장과 <b>같은 트랜잭션</b>에서 호출한다({@link Propagation#MANDATORY}) —
     * 별도 트랜잭션에서 잠그면 즉시 해제되어 직렬화 효과가 없다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForResultCommit(Long projectSectionId) {
        projectSectionRepository.findByIdForUpdate(projectSectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
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
