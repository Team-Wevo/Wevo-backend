package com.wevo.backend.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 빈 PostgreSQL에 전체 Flyway 마이그레이션을 적용한 뒤 Hibernate 엔티티 매핑 검증까지 통과하는지 확인한다.
 * GitHub Actions의 PostgreSQL service container처럼 호출자가 제공한 빈 DB에서만 명시적으로 실행한다.
 * 일반 통합 테스트의 격리된 PostgreSQL 검증은 {@link PostgresTestContainerConfig}를 사용한다.
 */
@Tag("postgres-schema")
@EnabledIfEnvironmentVariable(named = "WEVO_POSTGRES_SCHEMA_TEST", matches = "(?i)true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class PostgresSchemaIntegrationTest {

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironment("WEVO_POSTGRES_JDBC_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironment("WEVO_POSTGRES_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("WEVO_POSTGRES_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProjectRepository projectRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void migrationsCreateAllEntityTablesAndHibernateValidates() {
        Integer entityTableCount = new JdbcTemplate(dataSource).queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                """,
                Integer.class
        );

        assertThat(entityTableCount).isEqualTo(26);
    }

    @Test
    @Transactional
    void submittedOpinionRequiresSubmittedAt() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long ownerId = jdbcTemplate.queryForObject(
                "INSERT INTO users (name, status) VALUES ('owner', 'ACTIVE') RETURNING id",
                Long.class
        );
        Long authorId = jdbcTemplate.queryForObject(
                "INSERT INTO users (name, status) VALUES ('author', 'ACTIVE') RETURNING id",
                Long.class
        );
        Long projectId = jdbcTemplate.queryForObject(
                """
                INSERT INTO projects (owner_user_id, title, result_type, audience, status)
                VALUES (?, 'schema-test', 'PROPOSAL', 'test-audience', 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerId
        );
        Long sectionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO project_sections (project_id, title, section_order, status)
                VALUES (?, 'schema-test-section', 1, 'COLLECTING')
                RETURNING id
                """,
                Long.class,
                projectId
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO opinions (
                    project_section_id, author_user_id, content, submitted_content, status, submitted_at
                ) VALUES (?, ?, 'working-copy', 'submitted-copy', 'SUBMITTED', NULL)
                """,
                sectionId,
                authorId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 도메인 담당자가 확정한 Review/Draft 필수값과 기본값이 실제 PostgreSQL에서도 적용되는지 검증한다.
     */
    @Test
    @Transactional
    void reviewAndDraftDefaultsMatchApprovedSchema() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long ownerId = jdbcTemplate.queryForObject(
                "INSERT INTO users (name, status) VALUES ('owner', 'ACTIVE') RETURNING id",
                Long.class
        );
        Long reviewerId = jdbcTemplate.queryForObject(
                "INSERT INTO users (name, status) VALUES ('reviewer', 'ACTIVE') RETURNING id",
                Long.class
        );
        Long projectId = jdbcTemplate.queryForObject(
                """
                INSERT INTO projects (owner_user_id, title, result_type, audience, status)
                VALUES (?, 'review-schema-test', 'PROPOSAL', 'test-audience', 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerId
        );
        Long sectionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO project_sections (project_id, title, section_order, status)
                VALUES (?, 'review-schema-test-section', 1, 'DRAFTING')
                RETURNING id
                """,
                Long.class,
                projectId
        );

        Integer draftVersion = jdbcTemplate.queryForObject(
                """
                INSERT INTO section_drafts (project_section_id, content)
                VALUES (?, 'draft')
                RETURNING version
                """,
                Integer.class,
                sectionId
        );
        assertThat(draftVersion).isZero();

        Long teamReviewId = jdbcTemplate.queryForObject(
                """
                INSERT INTO team_reviews (
                    project_section_id, reviewer_user_id, status, reviewed_content_version
                ) VALUES (?, ?, 'APPROVED', 1)
                RETURNING id
                """,
                Long.class,
                sectionId,
                reviewerId
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT resolved FROM team_reviews WHERE id = ?",
                Boolean.class,
                teamReviewId
        )).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT outdated FROM team_reviews WHERE id = ?",
                Boolean.class,
                teamReviewId
        )).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM team_reviews WHERE id = ?",
                Long.class,
                teamReviewId
        )).isZero();

        Long reviewLinkId = jdbcTemplate.queryForObject(
                """
                INSERT INTO review_links (
                    project_section_id, token_hash, content_snapshot, content_version, status
                ) VALUES (?, 'required-signal-token', 'draft', 1, 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                sectionId
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO review_submissions (
                    review_link_id, anonymous_reviewer_id, understanding_signal
                ) VALUES (?, 'anonymous-reviewer', NULL)
                """,
                reviewLinkId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void projectOwnerForeignKeyRejectsMissingUser() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO projects (owner_user_id, title, result_type, audience, status)
                VALUES (?, 'invalid-owner', 'PROPOSAL', 'test-audience', 'ACTIVE')
                """,
                Long.MAX_VALUE
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void projectMemberUniqueConstraintRejectsDuplicateMembership() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long userId = insertUser(jdbcTemplate, "unique-member");
        Long projectId = insertProject(jdbcTemplate, userId, "unique-membership");

        jdbcTemplate.update(
                """
                INSERT INTO project_members (project_id, user_id, role, joined_at)
                VALUES (?, ?, 'OWNER', CURRENT_TIMESTAMP)
                """,
                projectId,
                userId
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO project_members (project_id, user_id, role, joined_at)
                VALUES (?, ?, 'MEMBER', CURRENT_TIMESTAMP)
                """,
                projectId,
                userId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void productEnumsAreStoredAndLoadedAsStrings() {
        User owner = User.builder()
                .name("enum-owner")
                .status(UserStatus.ACTIVE)
                .build();
        Project project = Project.builder()
                .owner(owner)
                .title("enum-project")
                .resultType(OutputType.PRESENTATION)
                .audience("enum-audience")
                .status(ProjectStatus.ACTIVE)
                .build();
        entityManager.persist(owner);
        entityManager.persist(project);
        entityManager.flush();

        String storedResultType = new JdbcTemplate(dataSource).queryForObject(
                "SELECT result_type FROM projects WHERE id = ?",
                String.class,
                project.getId()
        );
        String storedStatus = new JdbcTemplate(dataSource).queryForObject(
                "SELECT status FROM projects WHERE id = ?",
                String.class,
                project.getId()
        );
        entityManager.clear();

        Project loaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(storedResultType).isEqualTo("PRESENTATION");
        assertThat(storedStatus).isEqualTo("ACTIVE");
        assertThat(loaded.getResultType()).isEqualTo(OutputType.PRESENTATION);
        assertThat(loaded.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    @Transactional
    void draftLeaseUniqueConstraintAllowsOnlyOneLeasePerSection() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long ownerId = insertUser(jdbcTemplate, "lease-owner");
        Long memberId = insertUser(jdbcTemplate, "lease-member");
        Long projectId = insertProject(jdbcTemplate, ownerId, "lease-unique");
        Long sectionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO project_sections (project_id, title, section_order, status)
                VALUES (?, 'lease-section', 1, 'DRAFTING')
                RETURNING id
                """,
                Long.class,
                projectId
        );

        jdbcTemplate.update(
                """
                INSERT INTO draft_leases (project_section_id, holder_user_id, lease_until)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '5 minutes')
                """,
                sectionId,
                ownerId
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO draft_leases (project_section_id, holder_user_id, lease_until)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '5 minutes')
                """,
                sectionId,
                memberId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertUser(JdbcTemplate jdbcTemplate, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class,
                name
        );
    }

    private Long insertProject(JdbcTemplate jdbcTemplate, Long ownerId, String title) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO projects (owner_user_id, title, result_type, audience, status)
                VALUES (?, ?, 'PROPOSAL', 'test-audience', 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerId,
                title
        );
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경 변수가 필요합니다.");
        }
        return value;
    }
}
