package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.service.GapAnswerInputView;
import com.wevo.backend.issue.service.SynthesisPersistCommand;
import com.wevo.backend.issue.service.SynthesisResultWriteService;
import com.wevo.backend.opinion.service.SubmittedOpinionView;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.service.SectionSynthesisStateService;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.user.domain.User;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SynthesisJobHandlerTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final long SECTION_ID = 10L;
    private static final String HASH = "b".repeat(64);
    private static final String CHANGED_HASH = "c".repeat(64);

    @Mock private AiJobService aiJobService;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private SynthesisSnapshotAssembler snapshotAssembler;
    @Mock private SynthesisInputHasher inputHasher;
    @Mock private ObjectProvider<SynthesisGenerator> synthesisGeneratorProvider;
    @Mock private SynthesisGenerator synthesisGenerator;
    @Mock private SynthesisResultWriteService synthesisResultWriteService;
    @Mock private AiUsageResultLinkService usageResultLinkService;
    @Mock private SectionSynthesisStateService sectionSynthesisStateService;
    @Mock private AiJob job;
    @Mock private ProjectSection section;
    @Mock private Project project;
    @Mock private User user;

    private ScheduledExecutorService heartbeatScheduler;
    private SynthesisJobHandler handler;

    @BeforeEach
    void setUp() {
        heartbeatScheduler = Executors.newScheduledThreadPool(1);
        handler = new SynthesisJobHandler(aiJobService, aiJobRepository, snapshotAssembler, inputHasher,
                synthesisGeneratorProvider, synthesisResultWriteService, usageResultLinkService,
                sectionSynthesisStateService,
                heartbeatScheduler, new AiJobDispatchProperties(true, Duration.ofSeconds(2), 20, 8,
                        Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(60)));

        given(aiJobRepository.findByRequestId(REQUEST_ID)).willReturn(Optional.of(job));
        given(job.getRequestId()).willReturn(REQUEST_ID);
        given(job.getId()).willReturn(77L);
        given(job.getProjectSection()).willReturn(section);
        given(section.getId()).willReturn(SECTION_ID);
        given(job.getProject()).willReturn(project);
        given(project.getId()).willReturn(100L);
        given(job.getRequestedBy()).willReturn(user);
        given(job.getFeature()).willReturn(AiFeature.OPINION_SYNTHESIS);
        given(job.getPromptVersion()).willReturn(SynthesisContract.PROMPT_VERSION);
        given(job.getInputSnapshotHash()).willReturn(HASH);

        given(snapshotAssembler.assemble(SECTION_ID)).willReturn(new SynthesisInputSnapshot(
                List.of(new SubmittedOpinionView(
                        1L, 7L, "김민준", "타겟을 대학생 팀으로 좁히자.",
                        LocalDateTime.of(2026, 7, 28, 10, 0))),
                List.of(new GapAnswerInputView(9L, 3L, "이서연", "지난 근거")),
                2));
        given(inputHasher.hash(any())).willReturn(HASH);

        given(synthesisGeneratorProvider.getIfAvailable()).willReturn(synthesisGenerator);
    }

    @AfterEach
    void tearDown() {
        heartbeatScheduler.shutdownNow();
    }

    private void givenClaimed() {
        given(aiJobService.start(eq(REQUEST_ID), anyString()))
                .willReturn(new AiJobStartResult(REQUEST_ID, AiJobStatus.RUNNING, true));
    }

    private SynthesisAiOutput validOutput() {
        return new SynthesisAiOutput(
                "타겟을 좁히는 데 동의합니다.",
                List.of(1L),
                List.of(1L),
                List.of(new IssueDetectionIssueOutput(
                        IssueType.CONFLICT,
                        "무료 범위가 갈립니다.",
                        List.of(1L),
                        "무료 범위를 어디까지?",
                        List.of("3개 프로젝트", "1개 프로젝트")
                )));
    }

    @Test
    @DisplayName("저장된 스냅샷 해시로 작업을 claim한다")
    void claimsWithStoredHash() {
        givenClaimed();
        given(synthesisGenerator.generate(job, snapshotAssembler.assemble(SECTION_ID)))
                .willReturn(validOutput());
        given(aiJobService.succeed(eq(REQUEST_ID), any(), any()))
                .willReturn(new AiJobCompletionResult(REQUEST_ID, AiJobStatus.SUCCEEDED, 55L, true));
        given(synthesisResultWriteService.persist(any())).willReturn(55L);

        handler.run(REQUEST_ID);

        verify(aiJobService).start(REQUEST_ID, HASH); // 조립 해시가 아니라 저장된 해시로 claim
    }

    @Test
    @DisplayName("시작 claim에 실패하면(이미 실행 중 등) AI를 호출하지 않고 끝낸다")
    void notClaimed_skipsExecution() {
        given(aiJobService.start(eq(REQUEST_ID), anyString()))
                .willReturn(new AiJobStartResult(REQUEST_ID, AiJobStatus.RUNNING, false));

        handler.run(REQUEST_ID);

        verify(synthesisGenerator, never()).generate(any(), any());
        verify(aiJobService, never()).succeed(any(), any(), any());
        verify(aiJobService, never()).fail(any(), any());
    }

    @Test
    @DisplayName("정상 실행이면 정리 세트를 저장하고 재정리 플래그를 해제한다")
    void happyPath_persistsAndClearsStale() {
        givenClaimed();
        given(synthesisGenerator.generate(any(), any())).willReturn(validOutput());
        given(synthesisResultWriteService.persist(any())).willReturn(55L);
        given(aiJobService.succeed(eq(REQUEST_ID), any(), any())).willAnswer(invocation -> {
            AiJobResultWriter writer = invocation.getArgument(2);
            Long setId = writer.persist();
            return new AiJobCompletionResult(REQUEST_ID, AiJobStatus.SUCCEEDED, setId, true);
        });

        handler.run(REQUEST_ID);

        ArgumentCaptor<SynthesisPersistCommand> captor = ArgumentCaptor.forClass(SynthesisPersistCommand.class);
        verify(synthesisResultWriteService).persist(captor.capture());
        SynthesisPersistCommand command = captor.getValue();
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.consensusSummary()).isEqualTo("타겟을 좁히는 데 동의합니다.");
        assertThat(command.consensusEvidence()).singleElement()
                .satisfies(evidence -> assertThat(evidence.opinionId()).isEqualTo(1L));
        assertThat(command.issues()).hasSize(1);
        assertThat(command.issues().get(0).relatedOpinions().get(0).opinionId()).isEqualTo(1L);
        assertThat(command.inheritedGapAnswers()).hasSize(1);
        assertThat(command.inheritedGapAnswers().get(0).sourceAnswerId()).isEqualTo(9L);

        verify(sectionSynthesisStateService).clearSynthesisStale(SECTION_ID);
        verify(usageResultLinkService).linkSuccessfulInvocations(77L, 55L);
        verify(aiJobService, never()).fail(any(), any());
    }

    @Test
    @DisplayName("완료 재대조는 저장 트랜잭션 안에서 섹션을 잠근 뒤 현재 입력을 다시 읽어 계산한다")
    void completionProbe_locksSectionThenRereadsInput() {
        givenClaimed();
        given(synthesisGenerator.generate(any(), any())).willReturn(validOutput());
        given(synthesisResultWriteService.persist(any())).willReturn(55L);
        given(aiJobService.succeed(eq(REQUEST_ID), any(), any())).willAnswer(invocation -> {
            AiJobSnapshotProbe probe = invocation.getArgument(1);
            // 완료 처리 전에는 섹션을 잠그지 않는다 — 잠금·재조회는 probe 평가(=저장 트랜잭션) 시점이다.
            verify(sectionSynthesisStateService, never()).lockForResultCommit(SECTION_ID);

            assertThat(probe.currentSnapshotHash()).isEqualTo(HASH);

            // 잠금이 재조회보다 먼저 — 입력 변경 쓰기 경로와 직렬화된 뒤에 현재 입력을 읽는다.
            InOrder order = inOrder(sectionSynthesisStateService, snapshotAssembler);
            order.verify(sectionSynthesisStateService).lockForResultCommit(SECTION_ID);
            order.verify(snapshotAssembler).assemble(SECTION_ID);

            AiJobResultWriter writer = invocation.getArgument(2);
            return new AiJobCompletionResult(REQUEST_ID, AiJobStatus.SUCCEEDED, writer.persist(), true);
        });

        handler.run(REQUEST_ID);

        verify(synthesisResultWriteService).persist(any());
    }

    @Test
    @DisplayName("claim 후 입력이 바뀌었으면 실행하지 않고 STALE로 종료한다")
    void inputChangedAfterClaim_marksStale() {
        givenClaimed();
        given(inputHasher.hash(any())).willReturn(CHANGED_HASH); // 저장된 HASH와 다름

        handler.run(REQUEST_ID);

        verify(aiJobService).markStale(REQUEST_ID);
        verify(synthesisGenerator, never()).generate(any(), any());
        verify(aiJobService, never()).succeed(any(), any(), any());
    }

    @Test
    @DisplayName("claim 이후 조립 단계에서 예외가 나면 작업을 실패로 종료한다 (poison 방지)")
    void poisonAfterClaim_failsJob() {
        givenClaimed();
        given(snapshotAssembler.assemble(SECTION_ID)).willThrow(new RuntimeException("깨진 데이터"));

        handler.run(REQUEST_ID);

        verify(aiJobService).fail(eq(REQUEST_ID), any(RuntimeException.class));
        verify(synthesisGenerator, never()).generate(any(), any());
    }

    @Test
    @DisplayName("AI 호출 중 예외가 나면 작업을 실패로 종료한다")
    void aiFailure_failsJob() {
        givenClaimed();
        given(synthesisGenerator.generate(any(), any()))
                .willThrow(new RuntimeException("provider 오류"));

        handler.run(REQUEST_ID);

        verify(aiJobService).fail(eq(REQUEST_ID), any(RuntimeException.class));
        verify(aiJobService, never()).succeed(any(), any(), any());
        verify(synthesisResultWriteService, never()).persist(any());
    }

    @Test
    @DisplayName("AI provider가 없으면 작업을 실패로 종료한다")
    void providerMissing_failsJob() {
        givenClaimed();
        given(synthesisGeneratorProvider.getIfAvailable()).willReturn(null);

        handler.run(REQUEST_ID);

        verify(aiJobService).fail(eq(REQUEST_ID), any(IllegalStateException.class));
        verify(synthesisResultWriteService, never()).persist(any());
    }

    @Test
    @DisplayName("실행 중 heartbeat를 주기적으로 갱신한다")
    void heartbeatInvokedDuringExecution() throws Exception {
        SynthesisJobHandler fastHandler = handlerWith(heartbeatScheduler, Duration.ofMillis(10));
        givenClaimed();
        CountDownLatch heartbeatSeen = new CountDownLatch(1);
        willAnswer(invocation -> {
            heartbeatSeen.countDown();
            return null;
        }).given(aiJobService).heartbeat(REQUEST_ID);
        given(synthesisGenerator.generate(any(), any())).willAnswer(invocation -> {
            heartbeatSeen.await(2, TimeUnit.SECONDS); // 실행 중 heartbeat가 최소 1회 발생할 때까지 대기
            return validOutput();
        });
        given(aiJobService.succeed(eq(REQUEST_ID), any(), any()))
                .willReturn(new AiJobCompletionResult(REQUEST_ID, AiJobStatus.SUCCEEDED, 55L, true));

        fastHandler.run(REQUEST_ID);

        verify(aiJobService, atLeastOnce()).heartbeat(REQUEST_ID);
    }

    @Test
    @DisplayName("heartbeat 등록이 거부되면(종료 중 등) 작업을 RUNNING에 남기지 않고 실패로 종료한다")
    void heartbeatRegistrationRejected_failsJob() {
        ScheduledExecutorService deadScheduler = Executors.newScheduledThreadPool(1);
        deadScheduler.shutdown(); // 이후 schedule 호출은 RejectedExecutionException
        SynthesisJobHandler handlerWithDeadScheduler = handlerWith(deadScheduler, Duration.ofSeconds(15));
        givenClaimed();

        handlerWithDeadScheduler.run(REQUEST_ID);

        verify(aiJobService).fail(eq(REQUEST_ID), any(RejectedExecutionException.class));
        verify(synthesisGenerator, never()).generate(any(), any());
        deadScheduler.shutdownNow();
    }

    private SynthesisJobHandler handlerWith(ScheduledExecutorService scheduler, Duration heartbeatInterval) {
        return new SynthesisJobHandler(aiJobService, aiJobRepository, snapshotAssembler, inputHasher,
                synthesisGeneratorProvider, synthesisResultWriteService, usageResultLinkService,
                sectionSynthesisStateService,
                scheduler, new AiJobDispatchProperties(true, Duration.ofSeconds(2), 20, 8,
                        heartbeatInterval, Duration.ofSeconds(60), Duration.ofSeconds(60)));
    }
}
