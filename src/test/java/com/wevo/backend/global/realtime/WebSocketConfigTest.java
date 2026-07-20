package com.wevo.backend.global.realtime;

import com.wevo.backend.global.config.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    private final WebSocketAuthenticationInterceptor authenticationInterceptor =
            mock(WebSocketAuthenticationInterceptor.class);
    private final WebSocketSubscriptionAuthorizationInterceptor authorizationInterceptor =
            mock(WebSocketSubscriptionAuthorizationInterceptor.class);

    @Test
    @DisplayName("STOMP endpoint는 HTTP API와 같은 허용 Origin만 등록한다")
    void registerStompEndpoints_usesConfiguredOrigins() {
        CorsProperties corsProperties = new CorsProperties(List.of(
                "http://localhost:3000",
                "https://app.wevo.example"
        ));
        WebSocketConfig config = config(corsProperties);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration =
                mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint(WebSocketConfig.ENDPOINT)).thenReturn(registration);
        when(registration.setAllowedOrigins(
                "http://localhost:3000",
                "https://app.wevo.example"
        )).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(registration).setAllowedOrigins(
                "http://localhost:3000",
                "https://app.wevo.example"
        );
    }

    @Test
    @DisplayName("application, topic, queue, user destination prefix를 공통 규칙대로 설정한다")
    void configureMessageBroker_registersDestinationPrefixes() {
        WebSocketConfig config = config(new CorsProperties(List.of()));
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix("/user");
    }

    @Test
    @DisplayName("인증 인터셉터가 구독 인가 인터셉터보다 먼저 실행된다")
    void configureClientInboundChannel_registersSecurityInterceptorsInOrder() {
        WebSocketConfig config = config(new CorsProperties(List.of()));
        ChannelRegistration registration = mock(ChannelRegistration.class);

        config.configureClientInboundChannel(registration);

        verify(registration).interceptors(
                authenticationInterceptor,
                authorizationInterceptor
        );
    }

    private WebSocketConfig config(CorsProperties corsProperties) {
        return new WebSocketConfig(
                corsProperties,
                authenticationInterceptor,
                authorizationInterceptor
        );
    }
}
