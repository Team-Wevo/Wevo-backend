package com.wevo.backend.auth.service.oauth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientTimeoutTest {

    private static final Duration TEST_CONNECT_TIMEOUT = Duration.ofMillis(300);
    private static final Duration TEST_READ_TIMEOUT = Duration.ofMillis(300);
    // The slow provider delay must stay above the configured read timeout to trigger timeout deterministically.
    private static final long SLOW_PROVIDER_DELAY_MS = TEST_READ_TIMEOUT.plusMillis(700).toMillis();

    @Test
    void googleClientThrowsProviderErrorWhenTokenExchangeTimesOut() throws IOException {
        try (TestServer server = new TestServer(
                SLOW_PROVIDER_DELAY_MS,
                "{\"access_token\":\"google-token\"}",
                "{\"sub\":\"123\",\"email\":\"user@wevo.com\",\"name\":\"테스터\",\"picture\":\"http://img\"}"
        )) {
            GoogleOAuthClient client = new GoogleOAuthClient(new OAuthProperties(
                    new OAuthProperties.Provider(
                            "client-id",
                            "client-secret",
                            server.tokenUri(),
                            server.userInfoUri()
                    ),
                    null,
                    new OAuthProperties.Timeout(TEST_CONNECT_TIMEOUT, TEST_READ_TIMEOUT)
            ));

            assertThatThrownBy(() -> client.fetchUserInfo("code", "http://localhost/callback"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    @Test
    void kakaoClientThrowsProviderErrorWhenTokenExchangeTimesOut() throws IOException {
        try (TestServer server = new TestServer(
                SLOW_PROVIDER_DELAY_MS,
                "{\"access_token\":\"kakao-token\"}",
                """
                {
                  "id": 123,
                  "kakao_account": {
                    "email": "user@kakao.com",
                    "profile": {
                      "nickname": "테스터",
                      "profile_image_url": "http://img"
                    }
                  }
                }
                """
        )) {
            KakaoOAuthClient client = new KakaoOAuthClient(new OAuthProperties(
                    null,
                    new OAuthProperties.Provider(
                            "client-id",
                            "client-secret",
                            server.tokenUri(),
                            server.userInfoUri()
                    )
                    ,
                    new OAuthProperties.Timeout(TEST_CONNECT_TIMEOUT, TEST_READ_TIMEOUT)
            ));

            assertThatThrownBy(() -> client.fetchUserInfo("code", "http://localhost/callback"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private final long delayMs;

        private TestServer(long delayMs, String tokenResponse, String userInfoResponse) throws IOException {
            this.delayMs = delayMs;
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/token", exchange -> {
                sleepForSlowProvider();
                respondJson(exchange, tokenResponse);
            });
            server.createContext("/userinfo", exchange -> respondJson(exchange, userInfoResponse));
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

        private void sleepForSlowProvider() {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }

        private static void respondJson(HttpExchange exchange, String responseBody) throws IOException {
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(response);
            } finally {
                exchange.close();
            }
        }
    }
}
