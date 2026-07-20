package com.wevo.backend.global.realtime;

import com.wevo.backend.global.security.JwtProvider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketConnectionIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String FORBIDDEN_ORIGIN = "https://attacker.example";

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    private WebSocketStompClient stompClient;
    private StompSession connectedSession;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterEach
    void tearDown() {
        if (connectedSession != null && connectedSession.isConnected()) {
            connectedSession.disconnect();
        }
        stompClient.stop();
    }

    @Test
    @DisplayName("허용 Origin과 정상 Access JWT면 실제 STOMP 연결이 성립한다")
    void connect_withAllowedOriginAndValidJwt_succeeds() throws Exception {
        connectedSession = connect(ALLOWED_ORIGIN, jwtProvider.createAccessToken(7L))
                .get(5, TimeUnit.SECONDS);

        assertThat(connectedSession.isConnected()).isTrue();
    }

    @Test
    @DisplayName("Access JWT가 없으면 실제 STOMP CONNECT를 거부한다")
    void connect_withoutJwt_isRejected() {
        CompletableFuture<StompSession> connection = connect(ALLOWED_ORIGIN, null);

        assertThrows(ExecutionException.class,
                () -> connection.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("허용 목록 밖 Origin은 정상 JWT가 있어도 handshake를 거부한다")
    void connect_withForbiddenOrigin_isRejected() {
        CompletableFuture<StompSession> connection = connect(
                FORBIDDEN_ORIGIN,
                jwtProvider.createAccessToken(7L)
        );

        assertThrows(ExecutionException.class,
                () -> connection.get(5, TimeUnit.SECONDS));
    }

    private CompletableFuture<StompSession> connect(String origin, String accessToken) {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin(origin);

        StompHeaders connectHeaders = new StompHeaders();
        if (accessToken != null) {
            connectHeaders.add(
                    WebSocketAuthenticationInterceptor.AUTHORIZATION_HEADER,
                    WebSocketAuthenticationInterceptor.BEARER_PREFIX + accessToken
            );
        }

        return stompClient.connectAsync(
                "ws://localhost:" + port + WebSocketConfig.ENDPOINT,
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        );
    }
}
