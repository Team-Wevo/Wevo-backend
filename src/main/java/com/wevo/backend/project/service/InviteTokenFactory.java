package com.wevo.backend.project.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 프로젝트 초대 토큰을 만든다.
 *
 * <p>토큰을 난수로 뽑지 않고 <b>{@code HMAC-SHA256(비밀키, projectId)} 로 파생</b>한다. 초대 링크
 * 생성은 멱등이라(API_SPEC §3.2.5 — 이미 활성 링크가 있으면 그대로 반환) 두 번째 호출에서도
 * <b>같은 URL</b>을 돌려줘야 하는데, DB 에 해시만 저장하면 원문을 되살릴 방법이 없기 때문이다.
 * 파생 방식이면 언제든 {@code projectId} 로 다시 계산할 수 있어 계약이 그대로 유지된다.
 *
 * <p>DB 에는 이 토큰의 <b>SHA-256 해시</b>만 저장한다({@code InviteLink.tokenHash}). DB 만 유출되면
 * 해시에서 원문을 되돌릴 수 없고, 비밀키 없이는 {@code projectId} 로 다시 계산할 수도 없다.
 *
 * <p><b>한계</b> — 토큰이 {@code projectId} 의 함수라 링크 하나만 골라 회전시킬 수 없다. 비밀키를
 * 바꾸면 전체가 한꺼번에 바뀐다. 링크 재생성·비활성화는 MVP 범위 밖이라(정책서 §2.1) 지금은
 * 문제되지 않지만, 개별 회전이 필요해지면 난수 토큰 + 계약 변경으로 가야 한다.
 */
@Component
public class InviteTokenFactory {

    private static final String ALGORITHM = "HmacSHA256";
    /** 같은 비밀키를 다른 용도로 재사용하더라도 값이 겹치지 않도록 용도를 접두어로 넣는다. */
    private static final String PURPOSE_PREFIX = "invite:";

    private final SecretKeySpec key;

    public InviteTokenFactory(@Value("${app.invite.token-secret}") String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "app.invite.token-secret 이 비어 있습니다. INVITE_TOKEN_SECRET 을 주입하세요.");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /**
     * 이 프로젝트의 초대 토큰(64자 hex)을 계산한다. 같은 프로젝트면 항상 같은 값이다.
     */
    public String tokenFor(Long projectId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] derived = mac.doFinal(
                    (PURPOSE_PREFIX + projectId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(derived);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 은 모든 JVM 이 지원하고 키는 생성자에서 검증했으므로 실제 실행 X
            throw new IllegalStateException("초대 토큰을 생성할 수 없습니다.", e);
        }
    }
}
