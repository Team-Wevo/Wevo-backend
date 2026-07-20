package com.wevo.backend.global.realtime;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.global.security.TokenType;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 프레임의 {@code Authorization: Bearer {accessToken}}을 검증한다.
 */
@Component
public class WebSocketAuthenticationInterceptor implements ChannelInterceptor {

    /** 브라우저 STOMP 클라이언트가 CONNECT native header에 넣는 인증 헤더 이름. */
    static final String AUTHORIZATION_HEADER = "Authorization";
    /** HTTP API와 동일하게 사용하는 Bearer 인증 scheme. */
    static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public WebSocketAuthenticationInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * CONNECT 프레임만 인증하고, 검증된 userId를 이후 프레임에서 재사용할 연결 Principal로 설정한다.
     *
     * <p>토큰 검증 실패는 원문 토큰이나 내부 예외를 전달하지 않고 안전한 공통 오류로 변환한다.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String token = resolveAccessToken(accessor);
        try {
            Long userId = jwtProvider.parseUserId(token, TokenType.ACCESS);
            AuthPrincipal principal = new AuthPrincipal(userId);
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    AuthorityUtils.NO_AUTHORITIES
            ));
            return message;
        } catch (BusinessException exception) {
            throw new WebSocketSecurityException(exception.getErrorCode());
        }
    }

    /**
     * Authorization native header가 정확히 하나이며 Bearer 토큰 형식인지 검사한다.
     */
    private String resolveAccessToken(StompHeaderAccessor accessor) {
        List<String> headers = accessor.getNativeHeader(AUTHORIZATION_HEADER);
        if (headers == null || headers.size() != 1) {
            throw new WebSocketSecurityException(ErrorCode.UNAUTHORIZED);
        }

        String authorization = headers.getFirst();
        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() == BEARER_PREFIX.length()) {
            throw new WebSocketSecurityException(ErrorCode.INVALID_TOKEN);
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
