package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.OpinionClusteringContext;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.OpinionCluster;
import com.wevo.backend.ai.domain.OpinionClusterMember;
import com.wevo.backend.ai.domain.OpinionClusterSet;
import com.wevo.backend.ai.dto.response.OpinionClusteringResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.OpinionClusterMemberRepository;
import com.wevo.backend.ai.repository.OpinionClusterRepository;
import com.wevo.backend.ai.repository.OpinionClusterSetRepository;
import com.wevo.backend.opinion.service.OpinionSynthesisQueryService;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpinionClusteringQueryServiceTest {

    private static final long SECTION_ID = 10L;
    private static final long USER_ID = 20L;
    private static final String HASH = "a".repeat(64);

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private OpinionSynthesisQueryService opinionQueryService;
    @Mock private AiContextAssembler contextAssembler;
    @Mock private AiJobRepository jobRepository;
    @Mock private OpinionClusterSetRepository setRepository;
    @Mock private OpinionClusterRepository clusterRepository;
    @Mock private OpinionClusterMemberRepository memberRepository;
    @Mock private AiJobStatusMapper statusMapper;
    @Mock private ProjectSection section;
    @Mock private Project project;
    @Mock private VerifiedProjectAccess access;
    @Mock private AiJob successJob;
    @Mock private AiJob latestJob;
    @Mock private OpinionClusterSet set;
    @Mock private OpinionCluster cluster;
    @Mock private OpinionClusterMember member;

    private OpinionClusteringQueryService service;

    @BeforeEach
    void setUp() {
        service = new OpinionClusteringQueryService(
                sectionAccessGuard, projectAccessGuard, opinionQueryService,
                contextAssembler, jobRepository, setRepository, clusterRepository,
                memberRepository, statusMapper);
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID)).willReturn(section);
        given(section.getProject()).willReturn(project);
        given(project.getId()).willReturn(30L);
        given(projectAccessGuard.requireParticipantAccess(30L, USER_ID)).willReturn(access);
        given(contextAssembler.assembleOpinionClustering(access, SECTION_ID))
                .willReturn(new AssembledAiContext<>(
                        new OpinionClusteringContext(
                                30L, SECTION_ID, "문제 정의", 2,
                                new AiTemplateContext("problem", null, null), List.of()),
                        new AiInputSnapshot(new byte[]{1}, HASH)));
        given(successJob.getId()).willReturn(41L);
        given(successJob.getResultId()).willReturn(51L);
        given(successJob.getInputSnapshotHash()).willReturn(HASH);
        given(successJob.getRequestId()).willReturn(UUID.randomUUID());
        given(setRepository.findBySourceJob_Id(41L)).willReturn(Optional.of(set));
        given(set.getId()).willReturn(51L);
        given(set.getOpinionGateGeneration()).willReturn(2L);
        given(set.getTotalOpinionCount()).willReturn(3);
        given(set.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 1, 10, 0));
        given(cluster.getId()).willReturn(61L);
        given(cluster.getSortOrder()).willReturn(1);
        given(cluster.getTitle()).willReturn("공통 문제");
        given(cluster.getSummary()).willReturn("세 의견의 공통점입니다.");
        given(member.getCluster()).willReturn(cluster);
        given(member.getOpinionId()).willReturn(71L);
        given(clusterRepository.findAllByClusterSet_IdOrderBySortOrder(51L))
                .willReturn(List.of(cluster));
        given(memberRepository.findAllForSet(51L)).willReturn(List.of(member));
    }

    @Test
    void failedRerunKeepsLastSuccessfulCurrentSet() {
        given(opinionQueryService.hasSubmittedOpinion(SECTION_ID, USER_ID)).willReturn(true);
        given(jobRepository.findTopByProjectSection_IdAndFeatureOrderByQueuedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(SECTION_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(Optional.of(latestJob));
        given(jobRepository.findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(SECTION_ID), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AiJobStatus.SUCCEEDED)))
                .willReturn(Optional.of(successJob));
        given(latestJob.getRequestId()).willReturn(UUID.randomUUID());
        given(statusMapper.map(latestJob)).willReturn(new AiJobStatusMapper.MappedStatus(
                AiRequestStatus.FAILED, "AI018", "AI 응답 형식이 잘못되었습니다."));

        OpinionClusteringResponse response = service.getClustering(SECTION_ID, USER_ID);

        assertThat(response.stale()).isFalse();
        assertThat(response.latestJob().status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(response.currentSet()).isNotNull();
        assertThat(response.currentSet().clusters()).singleElement()
                .satisfies(item -> assertThat(item.opinionIds()).containsExactly(71L));
    }

    @Test
    void changedSnapshotMarksOldSuccessStaleAndDoesNotExposeIt() {
        given(opinionQueryService.hasSubmittedOpinion(SECTION_ID, USER_ID)).willReturn(true);
        given(successJob.getInputSnapshotHash()).willReturn("b".repeat(64));
        given(jobRepository.findTopByProjectSection_IdAndFeatureOrderByQueuedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(SECTION_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(Optional.of(successJob));
        given(jobRepository.findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(SECTION_ID), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AiJobStatus.SUCCEEDED)))
                .willReturn(Optional.of(successJob));
        given(statusMapper.map(successJob)).willReturn(new AiJobStatusMapper.MappedStatus(
                AiRequestStatus.SUCCEEDED, null, null));

        OpinionClusteringResponse response = service.getClustering(SECTION_ID, USER_ID);

        assertThat(response.stale()).isTrue();
        assertThat(response.currentSet()).isNull();
        verify(setRepository, never()).findBySourceJob_Id(41L);
    }

    @Test
    void existingCurrentResultIsHiddenBeforeRequesterSubmits() {
        given(opinionQueryService.hasSubmittedOpinion(SECTION_ID, USER_ID)).willReturn(false);
        given(jobRepository.findTopByProjectSection_IdAndFeatureOrderByQueuedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(SECTION_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(Optional.of(successJob));
        given(jobRepository.findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(SECTION_ID), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AiJobStatus.SUCCEEDED)))
                .willReturn(Optional.of(successJob));
        given(statusMapper.map(successJob)).willReturn(new AiJobStatusMapper.MappedStatus(
                AiRequestStatus.SUCCEEDED, null, null));

        OpinionClusteringResponse response = service.getClustering(SECTION_ID, USER_ID);

        assertThat(response.canViewResult()).isFalse();
        assertThat(response.currentSet()).isNull();
        verify(setRepository, never()).findBySourceJob_Id(41L);
    }
}
