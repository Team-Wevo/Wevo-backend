package com.wevo.backend.review.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 외부 검토 링크 토큰의 생성·해싱을 담당한다.
 *
 * <p>원문 토큰은 추측 불가능한 랜덤(UUIDv4) 이며 URL 로만 전달된다.
 * DB 에는 원문이 아닌 SHA-256 해시만 저장하고, 조회 시 요청 토큰을 같은 방식으로 해시해 비교한다.
 */
@Component
public class ReviewTokenHasher {

    /** URL 로 공유할 원문 토큰을 생성한다.*/
    public String generateRawToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 원문 토큰을 SHA-256 해시로 변환한다. */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 지원하므로 실제 실행 X
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
