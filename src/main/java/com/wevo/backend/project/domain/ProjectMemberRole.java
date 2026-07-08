package com.wevo.backend.project.domain;

/**
 * 프로젝트 내 역할. (제품 정책서 §1.2 — MVP는 두 역할만. 외부 검토자는 role이 아니라 링크 기반 별도 §1.3)
 */
public enum ProjectMemberRole {
    OWNER,   // 팀장 (프로젝트 생성자)
    MEMBER   // 팀원 (초대받아 참여)
}
