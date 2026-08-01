package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContextAssembler;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.ProjectFlowCheck;
import com.wevo.backend.ai.domain.ProjectFlowCheckInput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.ProjectFlowCheckFindingRepository;
import com.wevo.backend.ai.repository.ProjectFlowCheckInputRepository;
import com.wevo.backend.ai.repository.ProjectFlowCheckRepository;
import com.wevo.backend.ai.repository.ProjectFlowFindingSectionRepository;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectFlowReviewQueryServiceTest {
    @Mock ProjectAccessGuard accessGuard; @Mock ProjectFlowReviewContextAssembler assembler;
    @Mock AiJobRepository jobRepository; @Mock ProjectFlowCheckRepository checkRepository;
    @Mock ProjectFlowCheckInputRepository inputRepository;
    @Mock ProjectFlowCheckFindingRepository findingRepository;
    @Mock ProjectFlowFindingSectionRepository referenceRepository;
    @Mock AiJobStatusMapper statusMapper; @Mock VerifiedProjectAccess access;
    @Mock AiJob latest; @Mock AiJob success; @Mock ProjectFlowCheck check;
    @Mock ProjectFlowCheckInput input;

    @Test
    void failedRerunPreservesLastSuccessAsOutdatedStoredVersion() {
        var service = new ProjectFlowReviewQueryService(accessGuard, assembler, jobRepository,
                checkRepository, inputRepository, findingRepository, referenceRepository, statusMapper);
        given(accessGuard.requireParticipantAccess(1L, 7L)).willReturn(access);
        given(jobRepository.findTopByProject_IdAndFeatureOrderByQueuedAtDescIdDesc(
                1L, AiFeature.PROJECT_FLOW_REVIEW)).willReturn(Optional.of(latest));
        given(jobRepository.findTopByProject_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                1L, AiFeature.PROJECT_FLOW_REVIEW, AiJobStatus.SUCCEEDED)).willReturn(Optional.of(success));
        UUID latestId = UUID.randomUUID(); UUID successId = UUID.randomUUID();
        given(latest.getRequestId()).willReturn(latestId);
        given(statusMapper.map(latest)).willReturn(new AiJobStatusMapper.MappedStatus(
                AiRequestStatus.FAILED, "AI009", "처리할 수 없습니다."));
        given(success.getId()).willReturn(20L); given(success.getResultId()).willReturn(30L);
        given(success.getRequestId()).willReturn(successId); given(success.getInputSnapshotHash()).willReturn("a".repeat(64));
        given(assembler.assemble(access)).willReturn(new AssembledAiContext<>(
                new ProjectFlowReviewContext(new AiProjectIdentity(1L, "프로젝트", OutputType.PROPOSAL),
                        new AiProjectBrief(null, null, "고객"), 1, 0, List.of(), List.of()),
                new AiInputSnapshot(new byte[]{1}, "b".repeat(64))));
        given(checkRepository.findBySourceJob_Id(20L)).willReturn(Optional.of(check));
        given(check.getId()).willReturn(30L); given(check.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 1, 10, 0));
        given(check.getSectionCount()).willReturn(1); given(check.getFindingCount()).willReturn(0);
        given(inputRepository.findAllByFlowCheck_IdOrderBySortOrder(30L)).willReturn(List.of(input));
        given(input.getProjectSectionId()).willReturn(10L); given(input.getSectionKey()).willReturn("problem");
        given(input.getSectionTitle()).willReturn("문제"); given(input.getConfirmedVersion()).willReturn(3);
        given(referenceRepository.findAllForCheck(30L)).willReturn(List.of());
        given(findingRepository.findAllByFlowCheck_IdOrderBySortOrder(30L)).willReturn(List.of());

        var response = service.getReview(1L, 7L);

        assertThat(response.outdated()).isTrue();
        assertThat(response.latestJob().status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(response.lastSuccessfulResult().current()).isFalse();
        assertThat(response.lastSuccessfulResult().requestId()).isEqualTo(successId);
        assertThat(response.lastSuccessfulResult().checkedSections()).singleElement()
                .satisfies(section -> assertThat(section.confirmedVersion()).isEqualTo(3));
    }
}
