package com.wevo.backend.ai.repository;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.global.config.JpaAuditingConfig;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ai_jobs / ai_usage_logs 의 feature CHECK 제약이 {@link AiFeature} enum 값을 모두 허용하는지 검증한다.
 *
 * <p>enum 에 기능을 추가하면서 제약 마이그레이션을 빠뜨리면, 부팅(ddl-auto=validate)은 컬럼 타입만
 * 검사하므로 통과하지만 해당 기능의 job/usage INSERT 가 런타임에 조용히 제약 위반으로 실패한다.
 * 제목 자동 생성(PROJECT_TITLE_SUGGESTION)이 운영에서 조용히 안 되던 장애가 이 경우다 — 리스너가
 * 예외를 삼켜 사용자에겐 "제목만 안 생김"으로만 보였다.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PostgresTestContainerConfig.class})
class AiFeatureConstraintIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 10, 0);
    private static final String HASH = "a".repeat(64);

    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AiJobRepository jobRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private Project project;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .name("owner").email("feature-check@wevo.com").status(UserStatus.ACTIVE).build());
        project = projectRepository.save(Project.builder()
                .owner(owner).title("제목 없는 프로젝트").resultType(OutputType.PROPOSAL)
                .audience("팀").status(ProjectStatus.ACTIVE).build());
    }

    @Test
    @DisplayName("ai_jobs 는 PROJECT_TITLE_SUGGESTION feature job 저장을 허용한다")
    void aiJobsAllowsProjectTitleSuggestion() {
        AiJob job = AiJob.queue(
                UUID.randomUUID(), project, null, owner, AiFeature.PROJECT_TITLE_SUGGESTION,
                HASH, "project-v1", "project-title:v1", "project-title-output:v1",
                "gpt-5.6-luna", 128, "b".repeat(64), NOW);

        assertThatCode(() -> jobRepository.saveAndFlush(job)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ai_usage_logs 는 PROJECT_TITLE_SUGGESTION feature 사용량 기록을 허용한다")
    void aiUsageLogsAllowsProjectTitleSuggestion() {
        assertThatCode(() -> jdbcTemplate.update(
                """
                INSERT INTO ai_usage_logs (
                    request_id, provider, project_id, requested_by_user_id, feature,
                    model_id, prompt_version, input_snapshot_hash, request_status, started_at
                ) VALUES (?, 'openai', ?, ?, 'PROJECT_TITLE_SUGGESTION',
                    'gpt-5.6-luna', 'project-title:v1', ?, 'SUCCEEDED', ?)
                """,
                UUID.randomUUID(), project.getId(), owner.getId(), HASH, Timestamp.valueOf(NOW)))
                .doesNotThrowAnyException();
    }
}
