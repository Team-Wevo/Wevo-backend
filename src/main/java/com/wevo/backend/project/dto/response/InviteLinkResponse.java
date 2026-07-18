package com.wevo.backend.project.dto.response;

/**
 * 초대 링크 생성 응답.
 *
 * @param token     초대 토큰 (참여 API {@code POST /api/invites/{token}/join} 에 사용)
 * @param inviteUrl 사용자가 클릭할 공유용 링크 (프론트 초대 페이지 URL)
 */
public record InviteLinkResponse(
        String token,
        String inviteUrl
) {

    public static InviteLinkResponse of(String token, String inviteUrl) {
        return new InviteLinkResponse(token, inviteUrl);
    }
}
