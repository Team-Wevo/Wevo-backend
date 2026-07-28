package com.wevo.backend.issue.service;

import java.util.Optional;

/**
 * 섹션의 현재 정리 세트를 판정하는 issue 도메인 포트.
 *
 * <p>현재 세트의 단일 근거는 최신 성공 AI 작업의 {@code resultId}다. 구현은 AI 작업 이력을
 * 소유한 ai 도메인에 두어 issue 도메인이 ai 저장소를 직접 참조하지 않게 한다.
 */
public interface CurrentSynthesisSetResolver {

    Optional<CurrentSynthesisSetReference> findCurrent(Long projectSectionId);
}
