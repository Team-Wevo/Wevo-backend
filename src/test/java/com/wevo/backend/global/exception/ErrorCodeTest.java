package com.wevo.backend.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void oauthProviderErrorUsesDocumentedServerErrorStatus() {
        assertThat(ErrorCode.OAUTH_PROVIDER_ERROR.getStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
