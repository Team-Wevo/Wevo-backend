package com.wevo.backend.auth.service.oauth;

import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 요청한 {@link AuthProvider} 에 맞는 {@link OAuthClient} 를 반환한다.
 */
@Component
public class OAuthClientRouter {

    private final Map<AuthProvider, OAuthClient> clients;

    public OAuthClientRouter(List<OAuthClient> clients) {
        this.clients = clients.stream()
                .collect(Collectors.toMap(OAuthClient::provider, Function.identity()));
    }

    public OAuthClient getClient(AuthProvider provider) {
        OAuthClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_AUTH_PROVIDER);
        }
        return client;
    }
}
