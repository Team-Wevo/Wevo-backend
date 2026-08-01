package com.wevo.backend.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.OpinionClusteringContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.OpinionClusterOutput;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;
import com.wevo.backend.ai.service.OpinionClusteringPersistCommand;
import com.wevo.backend.ai.service.OpinionClusteringResultWriter;
import com.wevo.backend.global.config.JpaAuditingConfig;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.repository.OpinionRepository;
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
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingConfig.class,
        PostgresTestContainerConfig.class,
        AiInputSnapshotHasher.class,
        OpinionClusteringResultWriter.class
})
class OpinionClusteringPersistenceIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectSectionRepository sectionRepository;
    @Autowired private OpinionRepository opinionRepository;
    @Autowired private AiJobRepository jobRepository;
    @Autowired private OpinionClusterSetRepository setRepository;
    @Autowired private OpinionClusterRepository clusterRepository;
    @Autowired private OpinionClusterMemberRepository memberRepository;
    @Autowired private OpinionClusteringResultWriter resultWriter;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private Project project;
    private ProjectSection section;
    private AiJob job;
    private List<Opinion> opinions;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .name("owner").email("cluster-owner@wevo.com").status(UserStatus.ACTIVE).build());
        project = projectRepository.save(Project.builder()
                .owner(owner).title("분류 테스트").resultType(OutputType.PROPOSAL)
                .audience("팀").status(ProjectStatus.ACTIVE).build());
        section = sectionRepository.save(ProjectSection.builder()
                .project(project).title("문제 정의").sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING).build());
        opinions = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            User author = userRepository.save(User.builder()
                    .name("member-" + index).email("cluster-member-" + index + "@wevo.com")
                    .status(UserStatus.ACTIVE).build());
            Opinion opinion = Opinion.builder()
                    .projectSection(section).author(author)
                    .content("충분한 길이의 제출 의견 내용입니다. 번호 " + index)
                    .status(OpinionStatus.DRAFT).build();
            opinion.submit(NOW.plusMinutes(index));
            opinions.add(opinionRepository.save(opinion));
        }
        job = jobRepository.saveAndFlush(AiJob.queue(
                UUID.randomUUID(), project, section, owner, AiFeature.OPINION_CLUSTERING,
                "a".repeat(64), "opinion-clustering-source:v1",
                "opinion-clustering:v1", "opinion-clustering-output:v1",
                "test-model", 1024, "b".repeat(64), NOW));
    }

    @Test
    void writerPersistsCompletePartitionAndSnapshotHashes() {
        Long setId = persistResult();
        entityManager.flush();
        entityManager.clear();

        assertThat(setRepository.findById(setId)).isPresent()
                .get().satisfies(set -> {
                    assertThat(set.getTotalOpinionCount()).isEqualTo(3);
                    assertThat(set.getInputSnapshotHash()).isEqualTo("a".repeat(64));
                });
        assertThat(clusterRepository.findAllByClusterSet_IdOrderBySortOrder(setId))
                .singleElement().satisfies(cluster ->
                        assertThat(cluster.getTitle()).isEqualTo("공통 문제"));
        assertThat(memberRepository.findAllForSet(setId))
                .extracting(member -> member.getOpinionId())
                .containsExactlyElementsOf(opinions.stream().map(Opinion::getId).toList());
        Integer inputCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM opinion_cluster_inputs WHERE cluster_set_id = ?",
                Integer.class, setId);
        assertThat(inputCount).isEqualTo(3);
    }

    @Test
    void databaseRejectsSameOpinionInTwoClustersOfOneSet() {
        Long setId = persistResult();
        entityManager.flush();
        Long secondClusterId = jdbcTemplate.queryForObject(
                """
                INSERT INTO opinion_clusters (cluster_set_id, sort_order, title, summary)
                VALUES (?, 2, '다른 묶음', '다른 요약') RETURNING id
                """, Long.class, setId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO opinion_cluster_members (
                    cluster_set_id, cluster_id, opinion_id, sort_order
                ) VALUES (?, ?, ?, 1)
                """, setId, secondClusterId, opinions.getFirst().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownOpinionReference() {
        Long setId = persistResult();
        entityManager.flush();
        Long clusterId = clusterRepository
                .findAllByClusterSet_IdOrderBySortOrder(setId).getFirst().getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO opinion_cluster_members (
                    cluster_set_id, cluster_id, opinion_id, sort_order
                ) VALUES (?, ?, ?, 4)
                """, setId, clusterId, Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long persistResult() {
        List<AiOpinionContext> contextOpinions = opinions.stream()
                .map(opinion -> new AiOpinionContext(
                        opinion.getId(),
                        "member-" + opinion.getId(),
                        opinion.getSubmittedContentOrLegacy(),
                        opinion.getSubmittedAt().toString()))
                .toList();
        OpinionClusteringContext context = new OpinionClusteringContext(
                project.getId(), section.getId(), section.getTitle(), 0,
                new AiTemplateContext("problem", null, null), contextOpinions);
        OpinionClusteringOutput output = new OpinionClusteringOutput(List.of(
                new OpinionClusterOutput(
                        1, "공통 문제", "세 의견의 공통 문제를 요약합니다.",
                        opinions.stream().map(Opinion::getId).toList())));
        return resultWriter.persist(new OpinionClusteringPersistCommand(job, context, output));
    }
}
