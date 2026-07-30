package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiSectionCheck;
import com.wevo.backend.ai.dto.response.PrecheckResponse;
import com.wevo.backend.ai.dto.response.PrecheckResponse.CurrentResultResponse;
import com.wevo.backend.ai.dto.response.PrecheckResponse.FailureResponse;
import com.wevo.backend.ai.dto.response.PrecheckResponse.FindingResponse;
import com.wevo.backend.ai.dto.response.PrecheckResponse.LatestJobResponse;
import com.wevo.backend.ai.dto.response.PrecheckResponse.RewriteResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.AiSectionCheckFindingRepository;
import com.wevo.backend.ai.repository.AiSectionCheckRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 사전 검토의 최신 실행과 마지막 성공 결과를 일관된 DB snapshot에서 조립한다. */
@Service
public class PrecheckQueryService {

    private static final AiFeature FEATURE = AiFeature.DRAFT_REVIEW;

    private final SectionAccessGuard sectionAccessGuard;
    private final AiJobRepository jobRepository;
    private final AiSectionCheckRepository checkRepository;
    private final AiSectionCheckFindingRepository findingRepository;
    private final AiJobStatusMapper statusMapper;

    public PrecheckQueryService(
            SectionAccessGuard sectionAccessGuard,
            AiJobRepository jobRepository,
            AiSectionCheckRepository checkRepository,
            AiSectionCheckFindingRepository findingRepository,
            AiJobStatusMapper statusMapper
    ) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.jobRepository = jobRepository;
        this.checkRepository = checkRepository;
        this.findingRepository = findingRepository;
        this.statusMapper = statusMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PrecheckResponse getPrecheck(Long sectionId, Long userId) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSection(sectionId, userId);
        Optional<AiJob> latest = jobRepository
                .findTopByProjectSection_IdAndFeatureOrderByQueuedAtDescIdDesc(
                        sectionId, FEATURE);
        if (latest.isEmpty()) {
            return PrecheckResponse.notExecuted();
        }
        CurrentResultResponse current = currentResult(sectionId);
        return PrecheckResponse.of(
                current == null ? null : section.getAiCheckStatus(),
                latestJob(latest.get()),
                current
        );
    }

    private CurrentResultResponse currentResult(Long sectionId) {
        Optional<AiJob> successful = jobRepository
                .findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                        sectionId, FEATURE, AiJobStatus.SUCCEEDED);
        if (successful.isEmpty()) {
            return null;
        }
        AiJob job = successful.get();
        AiSectionCheck check = checkRepository.findByIdWithSourceJob(job.getResultId())
                .orElseThrow(() -> new IllegalStateException(
                        "성공한 사전 검토 작업의 결과가 없습니다."));
        if (!job.getRequestId().equals(check.getSourceJob().getRequestId())
                || !sectionId.equals(check.getProjectSection().getId())) {
            throw new IllegalStateException("사전 검토 작업과 결과 연결이 일치하지 않습니다.");
        }
        return new CurrentResultResponse(
                job.getRequestId(),
                check.getCheckedContentVersion(),
                findingRepository.findBySectionCheck_IdOrderBySortOrderAsc(check.getId()).stream()
                        .map(item -> new FindingResponse(
                                item.getType(),
                                item.getTargetExcerpt(),
                                item.getComment(),
                                item.getSuggestion()))
                        .toList(),
                new RewriteResponse(check.getRewriteContent(), check.getChangedCount()),
                check.isRewriteApplied(),
                check.getAppliedContentVersion()
        );
    }

    private LatestJobResponse latestJob(AiJob job) {
        AiJobStatusMapper.MappedStatus mapped = statusMapper.map(job);
        return new LatestJobResponse(
                job.getRequestId(),
                mapped.status(),
                mapped.failed()
                        ? new FailureResponse(mapped.failureCode(), mapped.failureMessage())
                        : null
        );
    }
}
