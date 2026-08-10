package com.wevo.backend.global.security;

import com.wevo.backend.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청의 {@code Authorization: Bearer} 헤더에서 Access Token 을 추출해 인증 컨텍스트를 설정한다.
 *
 * 토큰이 없거나 유효하지 않으면 인증을 설정하지 않고 다음 필터로 넘긴다.
 * 이후 보호된 엔드포인트에서는 {@link RestAuthenticationEntryPoint} 가 401 응답을 반환한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 토큰 검증 실패 사유를 담는 요청 속성 키. {@link RestAuthenticationEntryPoint} 가 읽는다.
     *
     * <p>필터는 401 을 직접 쓰지 않고 EntryPoint 에 맡기는데(Spring Security 규약), 만료·위조를
     * 구분한 {@code ErrorCode} 는 필터에만 있다. 요청 범위 속성으로 전달해 그 구분을 살린다.
     */
    static final String AUTHENTICATION_ERROR_CODE =
            JwtAuthenticationFilter.class.getName() + ".errorCode";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Long userId = jwtProvider.parseUserId(token, TokenType.ACCESS);
            AuthPrincipal principal = new AuthPrincipal(userId);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, AuthorityUtils.NO_AUTHORITIES);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BusinessException e) {
            // 유효하지 않은/만료된 토큰: 인증을 설정하지 않고 EntryPoint 가 401 을 처리하도록 둔다.
            // 다만 어느 쪽인지는 여기서만 알 수 있으므로 요청 속성으로 넘긴다 — 그러지 않으면
            // EntryPoint 가 모든 401 을 A001 로 뭉개, 클라이언트가 "만료면 재발급, 위조면 로그아웃"
            // 을 구분할 수 없다.
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTHENTICATION_ERROR_CODE, e.getErrorCode());
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
