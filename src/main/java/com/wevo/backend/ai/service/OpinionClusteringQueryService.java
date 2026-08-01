package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.OpinionClusteringContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.OpinionClusterMember;
import com.wevo.backend.ai.domain.OpinionClusterSet;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse.ClusterResponse;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse.CurrentSetResponse;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse.FailureResponse;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse.LatestJobResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.OpinionClusterMemberRepository;
import com.wevo.backend.ai.repository.OpinionClusterRepository;
import com.wevo.backend.ai.repository.OpinionClusterSetRepository;
import com.wevo.backend.opinion.service.OpinionSynthesisQueryService;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpinionClusteringQueryService {

    private static final AiFeature FEATURE = AiFeature.OPINION_CLUSTERING;

    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final OpinionSynthesisQueryService opinionQueryService;
    private final AiContextAssembler contextAssembler;
    private final AiJobRepository jobRepository;
    private final OpinionClusterSetRepository setRepository;
    private final OpinionClusterRepository clusterRepository;
    private final OpinionClusterMemberRepository memberRepository;
    private final AiJobStatusMapper statusMapper;

    public OpinionClusteringQueryService(
            SectionAccessGuard sectionAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            OpinionSynthesisQueryService opinionQueryService,
            AiContextAssembler contextAssembler,
            AiJobRepository jobRepository,
            OpinionClusterSetRepository setRepository,
            OpinionClusterRepository clusterRepository,
            OpinionClusterMemberRepository memberRepository,
            AiJobStatusMapper statusMapper
    ) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.opinionQueryService = opinionQueryService;
        this.contextAssembler = contextAssembler;
        this.jobRepository = jobRepository;
        this.setRepository = setRepository;
        this.clusterRepository = clusterRepository;
        this.memberRepository = memberRepository;
        this.statusMapper = statusMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OpinionClusteringResponse getClustering(Long sectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireParticipantSection(sectionId, userId);
        boolean canView = opinionQueryService.hasSubmittedOpinion(sectionId, userId);
        Optional<AiJob> latest = jobRepository
                .findTopByProjectSection_IdAndFeatureOrderByQueuedAtDescIdDesc(sectionId, FEATURE);
        if (latest.isEmpty()) {
            return OpinionClusteringResponse.notExecuted(canView);
        }

        VerifiedProjectAccess access = projectAccessGuard.requireParticipantAccess(
                section.getProject().getId(), userId);
        AssembledAiContext<OpinionClusteringContext> current =
                contextAssembler.assembleOpinionClustering(access, sectionId);
        Optional<AiJob> lastSuccess = jobRepository
                .findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                        sectionId, FEATURE, AiJobStatus.SUCCEEDED);
        boolean stale = lastSuccess.isPresent()
                && !lastSuccess.get().getInputSnapshotHash()
                .equals(current.snapshot().inputSnapshotHash());
        CurrentSetResponse currentSet = canView && lastSuccess.isPresent() && !stale
                ? toCurrentSet(lastSuccess.get())
                : null;
        return OpinionClusteringResponse.of(
                canView, stale, toLatestJob(latest.get()), currentSet);
    }

    private LatestJobResponse toLatestJob(AiJob job) {
        AiJobStatusMapper.MappedStatus mapped = statusMapper.map(job);
        return new LatestJobResponse(
                job.getRequestId(),
                mapped.status(),
                mapped.failed() ? new FailureResponse(
                        mapped.failureCode(), mapped.failureMessage()) : null
        );
    }

    private CurrentSetResponse toCurrentSet(AiJob job) {
        OpinionClusterSet set = setRepository.findBySourceJob_Id(job.getId())
                .orElseThrow(() -> new IllegalStateException("성공한 의견 분류 결과 set이 없습니다."));
        if (!set.getId().equals(job.getResultId())) {
            throw new IllegalStateException("AI 작업과 의견 분류 결과 set 연결이 일치하지 않습니다.");
        }
        Map<Long, List<Long>> opinionIdsByCluster = memberRepository.findAllForSet(set.getId())
                .stream()
                .collect(Collectors.groupingBy(
                        member -> member.getCluster().getId(),
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(OpinionClusterMember::getOpinionId, Collectors.toList())
                ));
        List<ClusterResponse> clusters = clusterRepository
                .findAllByClusterSet_IdOrderBySortOrder(set.getId()).stream()
                .map(cluster -> new ClusterResponse(
                        cluster.getSortOrder(),
                        cluster.getTitle(),
                        cluster.getSummary(),
                        List.copyOf(opinionIdsByCluster.getOrDefault(cluster.getId(), List.of()))
                ))
                .toList();
        int coveredCount = opinionIdsByCluster.values().stream().mapToInt(List::size).sum();
        return new CurrentSetResponse(
                job.getRequestId(),
                set.getOpinionGateGeneration(),
                set.getTotalOpinionCount(),
                coveredCount,
                set.getCreatedAt(),
                clusters
        );
    }
}
