package com.wevo.backend.section.service;

/**
 * 확정된 섹션 하나의 확정본. (section 도메인이 타 도메인에 공개하는 조회 결과)
 *
 * <p>엔티티를 그대로 넘기지 않고 필요한 값만 담아, 타 도메인이 섹션·초안의 내부 구조에
 * 의존하지 않게 한다.
 *
 * @param order   섹션 순서 ({@code sectionOrder})
 * @param title   섹션 제목
 * @param content 확정본 본문 — 섹션의 {@code confirmedVersion} 에 해당하는 초안
 */
public record ConfirmedSectionContent(Integer order, String title, String content) {
}
