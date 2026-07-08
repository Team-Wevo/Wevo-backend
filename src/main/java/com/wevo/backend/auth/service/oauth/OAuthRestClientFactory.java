package com.wevo.backend.auth.service.oauth;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

final class OAuthRestClientFactory {

    private OAuthRestClientFactory() {
    }

    static RestClient create(OAuthProperties.Timeout timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout.connect())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout.read());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
