package com.wevo.backend.global.security;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 자체 JWT(Access/Refresh)를 발급하고 검증한다.
 *
 * 토큰의 subject 에는 사용자 식별자(userId)를 담는다.
 */
@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    /** Refresh Token 의 세션 계보(패밀리) 식별자. 회전은 같은 값을 유지하고 로그인은 새로 발급한다. (#290) */
    private static final String FAMILY_CLAIM = "fam";

    private final SecretKey secretKey;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtProvider(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = properties.accessTokenValidityMs();
        this.refreshTokenValidityMs = properties.refreshTokenValidityMs();
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, accessTokenValidityMs, TokenType.ACCESS, null);
    }

    /**
     * Refresh Token 을 발급한다. {@code familyId} 는 세션 계보를 식별한다 — 로그인은 새 값을,
     * 회전(재발급)은 기존 값을 그대로 넘겨 같은 계보를 잇는다. 저장소가 "밀려난 옛 계보"와
     * "같은 계보에서 재사용된 토큰(탈취)"을 구분하는 근거다. (#290)
     */
    public String createRefreshToken(Long userId, String familyId) {
        return createToken(userId, refreshTokenValidityMs, TokenType.REFRESH, familyId);
    }

    /**
     * Refresh Token 을 검증하고 세션 계보(패밀리) 식별자를 반환한다. 용도가 REFRESH 가 아니거나
     * 계보 클레임이 없으면(구버전 토큰 등) 무효로 본다.
     */
    public String parseRefreshFamily(String token) {
        Claims claims = parseClaims(token);
        if (!TokenType.REFRESH.name().equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        String familyId = claims.get(FAMILY_CLAIM, String.class);
        if (familyId == null || familyId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return familyId;
    }

    public long getRefreshTokenValidityMs() {
        return refreshTokenValidityMs;
    }

    /**
     * 토큰을 검증하고 subject 에 담긴 사용자 식별자를 반환한다.
     *
     * <p>토큰의 {@code type} 클레임이 {@code expectedType} 과 일치하는지도 확인한다.
     * (예: Refresh Token 을 Access 용도로 사용하는 것을 차단)
     *
     * @param token        검증할 토큰
     * @param expectedType 기대하는 토큰 용도
     * @throws BusinessException 만료(EXPIRED_TOKEN), 서명/형식 오류 또는 용도 불일치(INVALID_TOKEN)
     */
    public Long parseUserId(String token, TokenType expectedType) {
        Claims claims = parseClaims(token);
        if (!expectedType.name().equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return Long.valueOf(claims.getSubject());
    }

    private String createToken(Long userId, long validityMs, TokenType type, String familyId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, type.name())
                .issuedAt(now)
                .expiration(expiry);
        if (familyId != null) {
            builder.claim(FAMILY_CLAIM, familyId);
        }
        return builder.signWith(secretKey).compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
}
