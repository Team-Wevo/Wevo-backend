package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.OutputType;

/**
 * 결과물 머리말에 필요한 프로젝트 최소 정보. (project 도메인이 타 도메인에 공개하는 조회 결과)
 *
 * <p>엔티티를 그대로 넘기지 않고 필요한 값만 담아, 타 도메인이 프로젝트의 내부 구조에
 * 의존하지 않게 한다. (CLAUDE.md §6 — 타 도메인의 엔티티를 직접 참조하지 않는다)
 *
 * <p>담는 값은 <b>결과물 문서의 머리에 들어가는 것</b>으로 한정한다 — 아이디어 원문·전달 대상처럼
 * AI 입력에만 필요한 값은 {@link ProjectAiContext} 가 따로 소유하므로, 소비처가 쓰지 않는 값이
 * 딸려 나가지 않는다.
 *
 * @param projectId  프로젝트 ID
 * @param title      프로젝트 이름
 * @param resultType 결과물 유형 — 고정 섹션 구성을 결정한다 (정책서 §2.3)
 */
public record ProjectOutputHeader(
        Long projectId,
        String title,
        OutputType resultType
) {
}
