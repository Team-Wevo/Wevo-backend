package com.wevo.backend.global.exception;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void oauthProviderErrorUsesDocumentedServerErrorStatus() {
        assertThat(ErrorCode.OAUTH_PROVIDER_ERROR.getStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void allExternalCodesMatchFormat() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getCode())
                    .as("%s 의 외부 코드 형식", errorCode.name())
                    .matches("^[A-Z]{1,3}\\d{3}$");
        }
    }

    @Test
    void allExternalCodesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(seen.add(errorCode.getCode()))
                    .as("%s 의 외부 코드 %s 중복", errorCode.name(), errorCode.getCode())
                    .isTrue();
        }
    }
}
