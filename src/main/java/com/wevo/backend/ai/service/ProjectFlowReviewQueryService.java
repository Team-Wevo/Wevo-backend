package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.ProjectFlowReviewContextAssembler;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.ProjectFlowFindingSection;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.CheckedSectionResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.FailureResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.FindingResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.LatestJobResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.ResultResponse;
import com.wevo.backend.ai.dto.response.ProjectFlowReviewResponse.SectionExcerptResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.ProjectFlowCheckFindingRepository;
import com.wevo.backend.ai.repository.ProjectFlowCheckInputRepository;
import com.wevo.backend.ai.repository.ProjectFlowCheckRepository;
import com.wevo.backend.ai.repository.ProjectFlowFindingSectionRepository;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectFlowReviewQueryService {
    private static final AiFeature FEATURE = AiFeature.PROJECT_FLOW_REVIEW;
    private final ProjectAccessGuard accessGuard;
    private final ProjectFlowReviewContextAssembler assembler;
    private final AiJobRepository jobRepository;
    private final ProjectFlowCheckRepository checkRepository;
    private final ProjectFlowCheckInputRepository inputRepository;
    private final ProjectFlowCheckFindingRepository findingRepository;
    private final ProjectFlowFindingSectionRepository referenceRepository;
    private final AiJobStatusMapper statusMapper;

    public ProjectFlowReviewQueryService(ProjectAccessGuard accessGuard,
                                         ProjectFlowReviewContextAssembler assembler,
                                         AiJobRepository jobRepository,
                                         ProjectFlowCheckRepository checkRepository,
                                         ProjectFlowCheckInputRepository inputRepository,
                                         ProjectFlowCheckFindingRepository findingRepository,
                                         ProjectFlowFindingSectionRepository referenceRepository,
                                         AiJobStatusMapper statusMapper) {
        this.accessGuard = accessGuard; this.assembler = assembler; this.jobRepository = jobRepository;
        this.checkRepository = checkRepository; this.inputRepository = inputRepository;
        this.findingRepository = findingRepository; this.referenceRepository = referenceRepository;
        this.statusMapper = statusMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectFlowReviewResponse getReview(Long projectId, Long userId) {
        VerifiedProjectAccess access = ProjectAccessGuard.hidingNonMember(ErrorCode.PROJECT_NOT_FOUND,
                () -> accessGuard.requireParticipantAccess(projectId, userId));
        Optional<AiJob> latest = jobRepository.findTopByProject_IdAndFeatureOrderByQueuedAtDescIdDesc(
                projectId, FEATURE);
        if (latest.isEmpty()) return ProjectFlowReviewResponse.notExecuted();
        Optional<AiJob> success = jobRepository
                .findTopByProject_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                        projectId, FEATURE, AiJobStatus.SUCCEEDED);
        boolean outdated = success.isPresent() && !success.get().getInputSnapshotHash().equals(currentHash(access));
        return new ProjectFlowReviewResponse(true, success.isPresent() ? outdated : null,
                latest(latest.get()), success.map(job -> result(job, !outdated)).orElse(null));
    }

    private String currentHash(VerifiedProjectAccess access) {
        try { return assembler.assemble(access).snapshot().inputSnapshotHash(); }
        catch (RuntimeException changedOrIncomplete) { return "0".repeat(64); }
    }

    private LatestJobResponse latest(AiJob job) {
        var mapped = statusMapper.map(job);
        return new LatestJobResponse(job.getRequestId(), mapped.status(), mapped.failed()
                ? new FailureResponse(mapped.failureCode(), mapped.failureMessage()) : null);
    }

    private ResultResponse result(AiJob job, boolean current) {
        var check = checkRepository.findBySourceJob_Id(job.getId())
                .orElseThrow(() -> new IllegalStateException("성공한 전체 흐름 결과가 없습니다."));
        if (!check.getId().equals(job.getResultId())) {
            throw new IllegalStateException("AI 작업과 전체 흐름 결과 연결이 일치하지 않습니다.");
        }
        List<CheckedSectionResponse> sections = inputRepository
                .findAllByFlowCheck_IdOrderBySortOrder(check.getId()).stream()
                .map(input -> new CheckedSectionResponse(input.getProjectSectionId(), input.getSectionKey(),
                        input.getSectionTitle(), input.getConfirmedVersion())).toList();
        Map<Long, List<SectionExcerptResponse>> references = referenceRepository
                .findAllForCheck(check.getId()).stream().collect(Collectors.groupingBy(
                        ref -> ref.getFinding().getId(), LinkedHashMap::new,
                        Collectors.mapping(this::reference, Collectors.toList())));
        List<FindingResponse> findings = findingRepository
                .findAllByFlowCheck_IdOrderBySortOrder(check.getId()).stream()
                .map(finding -> new FindingResponse(finding.getSortOrder(), finding.getType(),
                        List.copyOf(references.getOrDefault(finding.getId(), List.of())),
                        finding.getDescription(), finding.getSuggestion())).toList();
        return new ResultResponse(job.getRequestId(), current, check.getCreatedAt(),
                check.getSectionCount(), check.getFindingCount(), sections, findings);
    }

    private SectionExcerptResponse reference(ProjectFlowFindingSection reference) {
        return new SectionExcerptResponse(reference.getProjectSectionId(),
                reference.getConfirmedVersion(), reference.getTargetExcerpt());
    }
}
