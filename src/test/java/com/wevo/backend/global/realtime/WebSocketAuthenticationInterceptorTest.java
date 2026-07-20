package com.wevo.backend.global.realtime;

import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.global.security.JwtProperties;
import com.wevo.backend.global.security.JwtProvider;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WebSocketAuthenticationInterceptorTest {

    private static final String SECRET =
            "test-secret-key-for-wevo-backend-that-is-long-enough-000000";
    private static final long ACCESS_VALIDITY_MS = 1_800_000L;
    private static final long REFRESH_VALIDITY_MS = 1_209_600_000L;

    private final JwtProvider jwtProvider = jwtProvider(SECRET, ACCESS_VALIDITY_MS);
    private final WebSocketAuthenticationInterceptor interceptor =
            new WebSocketAuthenticationInterceptor(jwtProvider);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    @DisplayName("유효한 Access JWT로 CONNECT하면 AuthPrincipal이 연결된다")
    void connect_withValidAccessToken_setsPrincipal() {
        Message<byte[]> message = connectMessage(jwtProvider.createAccessToken(7L));

        interceptor.preSend(message, channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
        Authentication authentication = (Authentication) accessor.getUser();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthPrincipal(7L));
        assertThat(authentication.getName()).isEqualTo("7");
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 A001로 CONNECT를 거부한다")
    void connect_withoutAuthorization_rejectsUnauthorized() {
        assertRejected(connectMessage(Map.of()), ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("만료된 Access JWT면 A004로 CONNECT를 거부한다")
    void connect_withExpiredToken_rejectsExpiredToken() {
        JwtProvider expiringProvider = jwtProvider(SECRET, -1_000L);

        assertRejected(connectMessage(expiringProvider.createAccessToken(7L)),
                ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("다른 키로 서명한 JWT면 A003으로 CONNECT를 거부한다")
    void connect_withForgedToken_rejectsInvalidToken() {
        JwtProvider otherProvider = jwtProvider(
                "another-secret-key-that-is-also-long-enough-1234567890",
                ACCESS_VALIDITY_MS
        );

        assertRejected(connectMessage(otherProvider.createAccessToken(7L)),
                ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("Refresh JWT를 CONNECT 인증에 사용하면 A003으로 거부한다")
    void connect_withRefreshToken_rejectsInvalidToken() {
        assertRejected(connectMessage(jwtProvider.createRefreshToken(7L)),
                ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 토큰 원문을 노출하지 않고 A003으로 거부한다")
    void connect_withMalformedAuthorization_doesNotExposeToken() {
        String sensitiveToken = "sensitive-token-value";
        Message<byte[]> message = connectMessage(Map.of(
                WebSocketAuthenticationInterceptor.AUTHORIZATION_HEADER,
                sensitiveToken
        ));

        WebSocketSecurityException exception = assertThrows(
                WebSocketSecurityException.class,
                () -> interceptor.preSend(message, channel)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
        assertThat(exception.getMessage()).doesNotContain(sensitiveToken);
    }

    private void assertRejected(Message<byte[]> message, ErrorCode expected) {
        WebSocketSecurityException exception = assertThrows(
                WebSocketSecurityException.class,
                () -> interceptor.preSend(message, channel)
        );
        assertThat(exception.getErrorCode()).isEqualTo(expected);
        assertThat(exception.getMessage()).isEqualTo(expected.getMessage());
    }

    private Message<byte[]> connectMessage(String token) {
        return connectMessage(Map.of(
                WebSocketAuthenticationInterceptor.AUTHORIZATION_HEADER,
                WebSocketAuthenticationInterceptor.BEARER_PREFIX + token
        ));
    }

    private Message<byte[]> connectMessage(Map<String, String> nativeHeaders) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        nativeHeaders.forEach(accessor::setNativeHeader);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private JwtProvider jwtProvider(String secret, long accessValidityMs) {
        return new JwtProvider(new JwtProperties(
                secret,
                accessValidityMs,
                REFRESH_VALIDITY_MS
        ));
    }
}
