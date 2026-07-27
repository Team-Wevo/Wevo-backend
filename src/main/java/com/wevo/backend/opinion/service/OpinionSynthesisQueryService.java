package com.wevo.backend.opinion.service;

import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.repository.OpinionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 의견 정리(synthesis)가 사용하는 opinion 도메인의 읽기 경계. (CLAUDE.md §6 — 타 도메인은
 * {@link OpinionRepository}를 직접 참조하지 않고 이 서비스를 통해서만 제출 의견을 조회한다.)
 *
 * <p>인가는 호출측(정리 실행 서비스)이 이미 수행하므로 여기서는 순수 조회만 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class OpinionSynthesisQueryService {

    private final OpinionRepository opinionRepository;

    public OpinionSynthesisQueryService(OpinionRepository opinionRepository) {
        this.opinionRepository = opinionRepository;
    }

    /**
     * 섹션에 <b>제출된</b> 의견을 제출 시각 순으로 조회한다. (작업본이 아닌 제출본 — §4.1)
     * AI 정리 입력 스냅샷과 쟁점-의견 연결의 원천이다.
     */
    public List<SubmittedOpinionView> findSubmittedOpinions(Long projectSectionId) {
        return opinionRepository
                .findAllWithAuthorByProjectSectionIdAndStatus(projectSectionId, OpinionStatus.SUBMITTED)
                .stream()
                .map(SubmittedOpinionView::from)
                .toList();
    }
}
