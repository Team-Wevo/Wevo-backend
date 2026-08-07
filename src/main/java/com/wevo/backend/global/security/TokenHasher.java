package com.wevo.backend.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * URL 로 공유하는 토큰의 생성·해싱을 담당한다.
 *
 * <p>원문 토큰은 URL 로만 전달하고 DB 에는 SHA-256 해시만 저장한다. 조회할 때는 요청으로 들어온
 * 원문을 같은 방식으로 해시해 비교하므로, DB 가 통째로 유출돼도 링크를 그대로 쓸 수는 없다.
 *
 * <p>외부 검토 링크(review)와 초대 링크(project) 두 도메인이 함께 쓰므로 {@code global} 에 둔다.
 * (CLAUDE.md §1 — 두 개 이상 도메인이 공유하는 인프라성 코드)
 *
 * <p>해시는 <b>키 없는</b> SHA-256 이다. 토큰 자체가 추측 불가능한 고엔트로피 난수라 사전 공격
 * 대상이 아니어서 salt·stretching 이 필요 없고, 조회가 해시 일치로 이뤄져야 하므로 결정적이어야 한다.
 */
@Component
public class TokenHasher {

    /** URL 로 공유할 원문 토큰을 생성한다. (UUIDv4 — 122비트 난수) */
    public String generateRawToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 원문 토큰을 SHA-256 해시(64자 hex)로 변환한다. */
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
