package com.wevo.backend.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 빈 PostgreSQL에 Flyway V1을 적용한 뒤 Hibernate 엔티티 매핑 검증까지 통과하는지 확인한다.
 * CI의 PostgreSQL/Testcontainers 구성은 별도 이슈 범위이므로 현재는 명시적으로 제공된 DB에서만 실행한다.
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

    @Test
    void v1CreatesAllEntityTablesAndHibernateValidates() {
        Integer entityTableCount = new JdbcTemplate(dataSource).queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                """,
                Integer.class
        );

        assertThat(entityTableCount).isEqualTo(18);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경 변수가 필요합니다.");
        }
        return value;
    }
}
