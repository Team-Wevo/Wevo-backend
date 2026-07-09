package com.wevo.backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 허용 Origin 설정. 프론트엔드가 별도 origin 으로 배포되므로 구성으로 관리한다.
 *
 * @param allowedOrigins 허용할 Origin 목록 (application.yml 의 {@code app.cors.allowed-origins})
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public List<String> allowedOrigins() {
        return allowedOrigins == null ? List.of() : allowedOrigins;
    }
}
