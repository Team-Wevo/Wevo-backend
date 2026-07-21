package com.wevo.backend.global.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL 동작을 검증하는 통합 테스트가 공유하는 Testcontainers 구성.
 *
 * <p>운영·로컬 Docker Compose와 동일한 PostgreSQL 16을 사용한다. 컨테이너는 Spring 테스트
 * 컨텍스트의 생명주기에 맞춰 시작·종료되며, {@link ServiceConnection}이 DataSource와 Flyway
 * 연결 정보를 함께 제공한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfig {

    static final String POSTGRES_IMAGE = "postgres:16";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("wevo_test")
                .withUsername("wevo_test")
                .withPassword("wevo_test");
    }
}
