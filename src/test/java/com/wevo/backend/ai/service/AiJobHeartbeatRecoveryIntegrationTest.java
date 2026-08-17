package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * heartbeat 기준 RUNNING 작업 회수의 <b>원자성</b>을 실제 PostgreSQL에서 검증한다.
 *
 * <p>회수 스케줄러의 "오래된 작업 조회 → 실패 전이" 사이에 worker가 heartbeat를 갱신하는 경합에서,
 * 실패 전이 직전 조건 재확인({@link AiJobService#recoverIfHeartbeatStale})이 살아 있는 작업을
 * 오회수하지 않는지 확인한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "wevo.ai.jobs.dispatch-enabled=false"
})
@Import(PostgresTestContainerConfig.class)
class AiJobHeartbeatRecoveryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 24, 14, 0);
    private static final LocalDateTime STALE_HEARTBEAT = NOW.minusMinutes(10);
    private static final LocalDateTime THRESHOLD = NOW.minusMinutes(5); // STALE_HEARTBEAT보다 뒤

    @Autowired private AiJobService aiJobService;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectSectionRepository projectSectionRepository;

    private UUID requestId;

    @BeforeEach
    void setUp() {
        aiJobRepository.deleteAll();
        projectSectionRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        User owner = userRepository.save(User.builder()
                .name("윤호").email("owner@wevo.com").status(UserStatus.ACTIVE).build());
        Project project = projectRepository.save(Project.builder()
                .owner(owner).title("회수 테스트").resultType(OutputType.PROPOSAL)
                .audience("심사위원").status(ProjectStatus.ACTIVE).build());
        ProjectSection section = projectSectionRepository.save(ProjectSection.builder()
                .project(project).title("문제 정의").sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING).build());

        // heartbeat가 오래된 RUNNING 작업을 만든다.
        AiJob job = AiJob.queue(UUID.randomUUID(), project, section, owner, AiFeature.OPINION_SYNTHESIS,
                "a".repeat(64), "opinion-synthesis-source:v1", "opinion-synthesis:v1",
                "opinion-synthesis:v1", "model-x", 4096, "b".repeat(64), STALE_HEARTBEAT);
        job.start(STALE_HEARTBEAT); // RUNNING, lastHeartbeatAt = STALE_HEARTBEAT
        aiJobRepository.saveAndFlush(job);
        requestId = job.getRequestId();
    }

    @Test
    @DisplayName("heartbeat가 실제로 끊긴 작업은 실패로 회수한다")
    void recoversGenuinelyStalledJob() {
        boolean recovered = aiJobService.recoverIfHeartbeatStale(requestId, THRESHOLD);

        assertThat(recovered).isTrue();
        assertThat(aiJobRepository.findByRequestId(requestId)).get()
                .satisfies(job -> assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED));
    }

    @Test
    @DisplayName("조회 이후 heartbeat가 갱신된 살아 있는 작업은 회수하지 않는다 (오회수 방지)")
    void doesNotRecoverJobRevivedByHeartbeat() {
        // 회수 스케줄러가 오래된 작업으로 조회한 뒤, worker가 heartbeat를 갱신한 상황을 재현한다.
        aiJobService.heartbeat(requestId); // lastHeartbeatAt = now → THRESHOLD보다 최신

        boolean recovered = aiJobService.recoverIfHeartbeatStale(requestId, THRESHOLD);

        assertThat(recovered).isFalse();
        assertThat(aiJobRepository.findByRequestId(requestId)).get()
                .satisfies(job -> assertThat(job.getStatus()).isEqualTo(AiJobStatus.RUNNING));
    }

    @Test
    @DisplayName("heartbeat가 최신이어도 startedAt이 application threshold보다 오래되면 회수한다")
    void recoversApplicationTimeoutDespiteFreshHeartbeat() {
        aiJobService.heartbeat(requestId);

        boolean recovered = aiJobService.recoverIfApplicationTimedOut(requestId, THRESHOLD);

        assertThat(recovered).isTrue();
        assertThat(aiJobRepository.findByRequestId(requestId)).get()
                .satisfies(job -> {
                    assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED);
                    assertThat(job.getFinalErrorType()).isEqualTo(AiErrorType.APPLICATION_TIMEOUT);
                });
    }

    @Test
    @DisplayName("조회 뒤 작업이 terminal이 되면 application timeout 재확인은 건너뛴다")
    void applicationTimeoutRecheckSkipsTerminalJob() {
        aiJobService.markApplicationTimedOut(requestId);

        boolean recovered = aiJobService.recoverIfApplicationTimedOut(requestId, THRESHOLD);

        assertThat(recovered).isFalse();
    }
}
