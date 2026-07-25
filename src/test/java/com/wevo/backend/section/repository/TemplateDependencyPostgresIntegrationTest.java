package com.wevo.backend.section.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.global.config.JpaAuditingConfig;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.TemplateDependencyType;
import com.wevo.backend.section.seed.SectionTemplateSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/** dependency reference data와 JPQL 방향 계약을 실제 PostgreSQL 16에서 검증한다. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaAuditingConfig.class,
        PostgresTestContainerConfig.class,
        SectionTemplateSeeder.class
})
class TemplateDependencyPostgresIntegrationTest {

    @Autowired
    private SectionTemplateSeeder seeder;
    @Autowired
    private SectionTemplateRepository sectionTemplateRepository;
    @Autowired
    private TemplateDependencyRepository dependencyRepository;

    @Test
    void repeatedSeedStoresOnlyPolicyEdgesAndRepositoryUsesDeclaredDirection() throws Exception {
        seeder.run(null);
        seeder.run(null);

        assertThat(dependencyRepository.findAllWithTemplates()).hasSize(16);
        assertThat(dependencyRepository.findAllWithTemplates())
                .filteredOn(dependency ->
                        dependency.getFromTemplate().getResultType() == OutputType.PRESENTATION)
                .hasSize(9);
        assertThat(dependencyRepository.findAllWithTemplates())
                .filteredOn(dependency ->
                        dependency.getFromTemplate().getResultType() == OutputType.PROPOSAL)
                .hasSize(7);

        var solution = sectionTemplateRepository
                .findByResultTypeOrderByOrderNo(OutputType.PRESENTATION).stream()
                .filter(template -> template.getSectionKey().equals("solution-direction"))
                .findFirst()
                .orElseThrow();
        assertThat(dependencyRepository.findDirectPrerequisites(
                solution.getId(), OutputType.PRESENTATION, TemplateDependencyType.REQUIRES))
                .extracting(dependency -> dependency.getToTemplate().getSectionKey())
                .containsExactly("problem-definition", "target-user");
    }
}
