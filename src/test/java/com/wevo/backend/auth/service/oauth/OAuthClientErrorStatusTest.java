package com.wevo.backend.auth.service.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 토큰 교환에서 제공자가 준 상태코드에 따라 4xx(클라이언트 오류)와 5xx(제공자 장애)를 구분해
 * 각각 A008(400)·A007(500)로 매핑하는지 검증한다. (#288)
 */
class OAuthClientErrorStatusTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    @DisplayName("Google 토큰 교환이 400(invalid_grant)이면 A008(400)로 내린다 — 만료·재사용된 code")
    void googleTokenExchange_400_mapsInvalidAuthorizationCode() throws IOException {
        try (TestServer server = new TestServer(400, "{\"error\":\"invalid_grant\"}")) {
            GoogleOAuthClient client = new GoogleOAuthClient(googleProperties(server));

            assertThatThrownBy(() -> client.fetchUserInfo("used-code", "http://localhost/callback"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OAUTH_INVALID_AUTHORIZATION_CODE);
        }
    }

    @Test
    @DisplayName("Kakao 토큰 교환이 400이면 A008(400)로 내린다")
    void kakaoTokenExchange_400_mapsInvalidAuthorizationCode() throws IOException {
        try (TestServer server = new TestServer(400, "{\"error\":\"invalid_grant\"}")) {
            KakaoOAuthClient client = new KakaoOAuthClient(kakaoProperties(server));

            assertThatThrownBy(() -> client.fetchUserInfo("used-code", "http://localhost/callback"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OAUTH_INVALID_AUTHORIZATION_CODE);
        }
    }

    @Test
    @DisplayName("Google 토큰 교환이 500(제공자 장애)이면 A007(500)을 유지한다 — 클라이언트 오류와 구분")
    void googleTokenExchange_500_keepsProviderError() throws IOException {
        try (TestServer server = new TestServer(500, "{\"error\":\"server_error\"}")) {
            GoogleOAuthClient client = new GoogleOAuthClient(googleProperties(server));

            assertThatThrownBy(() -> client.fetchUserInfo("code", "http://localhost/callback"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private OAuthProperties googleProperties(TestServer server) {
        return new OAuthProperties(
                new OAuthProperties.Provider(
                        "client-id", "client-secret", server.tokenUri(), server.userInfoUri()),
                null,
                new OAuthProperties.Timeout(TIMEOUT, TIMEOUT));
    }

    private OAuthProperties kakaoProperties(TestServer server) {
        return new OAuthProperties(
                null,
                new OAuthProperties.Provider(
                        "client-id", "client-secret", server.tokenUri(), server.userInfoUri()),
                new OAuthProperties.Timeout(TIMEOUT, TIMEOUT));
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer(int tokenStatus, String tokenBody) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/token", exchange -> respond(exchange, tokenStatus, tokenBody));
            server.createContext("/userinfo", exchange -> respond(exchange, 200, "{}"));
            server.start();
        }

        private String tokenUri() {
            return "http://localhost:" + server.getAddress().getPort() + "/token";
        }

        private String userInfoUri() {
            return "http://localhost:" + server.getAddress().getPort() + "/userinfo";
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
                throws IOException {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            } finally {
                exchange.close();
            }
        }
    }
}
