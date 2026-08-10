package com.wevo.backend.global.security;

import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청이 보호된 리소스에 접근할 때 공통 실패 응답(401)을 반환한다.
 *
 * <p>토큰이 있었지만 검증에 실패한 경우에는 {@link JwtAuthenticationFilter} 가 판별한 사유
 * ({@code A004} 만료 / {@code A003} 위조)를 그대로 내려준다. 셋을 모두 {@code A001} 로 뭉치면
 * 클라이언트가 "만료면 조용히 재발급, 그 밖에는 재로그인"을 구분할 수 없다.
 *
 * <p>토큰 자체가 없는 요청은 검증할 것이 없으므로 {@code A001} 이다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = resolveErrorCode(request);
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)));
    }

    /**
     * 필터가 남긴 검증 실패 사유를 쓰되, 401 이 아닌 코드가 실려 오면 무시한다 — 상태(401)와
     * 코드의 의미가 어긋난 응답이 나가지 않게 한다.
     */
    private ErrorCode resolveErrorCode(HttpServletRequest request) {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTHENTICATION_ERROR_CODE);
        if (attribute instanceof ErrorCode errorCode
                && errorCode.getStatus() == ErrorCode.UNAUTHORIZED.getStatus()) {
            return errorCode;
        }
        return ErrorCode.UNAUTHORIZED;
    }
}
