package com.wevo.backend.project.dto.response;

import com.wevo.backend.project.domain.Project;

/**
 * 초대 링크 참여 전 미리보기 응답. 프론트가 "○○ 프로젝트에 초대되었습니다" 화면을 그리는 데 쓴다.
 *
 * @param projectId    프로젝트 식별자
 * @param projectTitle 프로젝트 제목
 * @param memberCount  현재 참여 인원
 * @param maxMembers   최대 인원 (정책 §2.1 — 4)
 * @param full         현재 인원이 상한에 도달했는지 (참여 버튼 비활성화 힌트)
 */
public record InvitePreviewResponse(
        Long projectId,
        String projectTitle,
        long memberCount,
        int maxMembers,
        boolean full
) {

    public static InvitePreviewResponse of(Project project, long memberCount) {
        return new InvitePreviewResponse(
                project.getId(),
                project.getTitle(),
                memberCount,
                Project.MAX_MEMBERS,
                memberCount >= Project.MAX_MEMBERS
        );
    }
}
