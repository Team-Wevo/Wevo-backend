package com.wevo.backend.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("prod")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:prod-profile-test;MODE=PostgreSQL",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                "spring.data.redis.password=test-redis-password",
                "spring.data.redis.timeout=100ms",
                "spring.data.redis.repositories.enabled=false",
                "management.server.port=0",
                "jwt.secret=test-secret-key-for-wevo-backend-that-is-long-enough-000000",
                "app.cors.allowed-origins[0]=http://localhost",
                "app.invite.base-url=http://localhost/invite/",
                "app.invite.token-secret=test-invite-token-secret-that-is-long-enough-000000",
                // 운영 프로파일은 누락을 허용하지 않는다. 이 테스트는 의도적 AI 비활성 상태다.
                "wevo.ai.provider=none",
                "wevo.ai.audit.recovery-enabled=false"
        })
class ProductionProfileIntegrationTest {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void productionProfileDisablesOpenApiDocuments() throws Exception {
        HttpResponse<String> response = get(serverPort, "/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void internalHealthEndpointReportsRedisFailureWithoutAuthentication() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"status\":\"DOWN\"");
        assertThat(response.body()).doesNotContain("redis", "db");
    }

    private HttpResponse<String> get(int port, String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
