package com.wevo.backend.opinion.service;

import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.SectionAiContextQueryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * AI 입력용 제출본 조회 경계.
 *
 * <p>사용자용 "본인이 제출해야 공개" gate는 적용하지 않는다. 호출 전 인증과 OWNER 실행 조건은
 * 상위 AI use case가 검증하고, 이 서비스는 {@link VerifiedProjectAccess}와 section 소속만 강제한다.
 */
@Service
@Transactional(readOnly = true)
public class SubmittedOpinionQueryService {

    private final OpinionRepository opinionRepository;
    private final SectionAiContextQueryService sectionQueryService;

    public SubmittedOpinionQueryService(
            OpinionRepository opinionRepository,
            SectionAiContextQueryService sectionQueryService
    ) {
        this.opinionRepository = opinionRepository;
        this.sectionQueryService = sectionQueryService;
    }

    public List<SubmittedOpinionContext> findSubmittedOpinions(
            VerifiedProjectAccess access, Long sectionId
    ) {
        sectionQueryService.getMetadata(access, sectionId);
        return opinionRepository.findSubmittedForAiContext(access.projectId(), sectionId).stream()
                .map(this::toContext)
                .toList();
    }

    private SubmittedOpinionContext toContext(Opinion opinion) {
        String submittedContent = opinion.getSubmittedContentOrLegacy();
        if (opinion.getId() == null
                || opinion.getSubmittedAt() == null
                || opinion.getAuthor() == null
                || opinion.getAuthor().getId() == null
                || !StringUtils.hasText(submittedContent)) {
            throw new IllegalStateException("AI context 제출 의견 데이터가 유효하지 않습니다.");
        }
        return new SubmittedOpinionContext(
                opinion.getId(),
                submittedContent,
                opinion.getSubmittedAt(),
                opinion.getAuthor().getId());
    }
}
