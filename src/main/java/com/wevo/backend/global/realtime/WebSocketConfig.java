package com.wevo.backend.global.realtime;

import com.wevo.backend.global.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 도메인 이벤트 알림이 공통으로 사용하는 WebSocket/STOMP 기반 설정.
 *
 * <p>브라우저 WebSocket handshake에는 임의 Authorization 헤더를 안정적으로 넣을 수 없으므로
 * HTTP upgrade 경로는 공개하고, 최초 STOMP {@code CONNECT} 프레임의 Access JWT를
 * {@link WebSocketAuthenticationInterceptor}에서 검증한다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 클라이언트가 HTTP upgrade를 요청하는 단일 STOMP endpoint. */
    public static final String ENDPOINT = "/ws";
    /** 클라이언트가 서버의 메시지 처리기로 명령을 보낼 때 사용하는 prefix. */
    public static final String APPLICATION_PREFIX = "/app";
    /** 프로젝트·섹션 단위 다중 사용자 알림을 구독하는 broker prefix. */
    public static final String TOPIC_PREFIX = "/topic";
    /** 사용자별 queue를 내부에서 전달할 때 simple broker가 사용하는 prefix. */
    public static final String BROKER_QUEUE_PREFIX = "/queue";
    /** 클라이언트가 자신의 개인 queue를 구독할 때 사용하는 prefix. */
    public static final String USER_PREFIX = "/user";

    private final CorsProperties corsProperties;
    private final WebSocketAuthenticationInterceptor authenticationInterceptor;
    private final WebSocketSubscriptionAuthorizationInterceptor authorizationInterceptor;

    public WebSocketConfig(
            CorsProperties corsProperties,
            WebSocketAuthenticationInterceptor authenticationInterceptor,
            WebSocketSubscriptionAuthorizationInterceptor authorizationInterceptor
    ) {
        this.corsProperties = corsProperties;
        this.authenticationInterceptor = authenticationInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
    }

    /**
     * STOMP endpoint를 등록하고 HTTP API와 동일한 허용 Origin 목록을 handshake에도 적용한다.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    /**
     * 서버 명령, 공용 topic, 사용자 queue의 destination prefix를 일관되게 설정한다.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(TOPIC_PREFIX, BROKER_QUEUE_PREFIX);
        registry.setApplicationDestinationPrefixes(APPLICATION_PREFIX);
        registry.setUserDestinationPrefix(USER_PREFIX);
    }

    /**
     * 모든 inbound 프레임에 인증을 먼저, 구독 대상 인가를 다음 순서로 적용한다.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor, authorizationInterceptor);
    }
}
