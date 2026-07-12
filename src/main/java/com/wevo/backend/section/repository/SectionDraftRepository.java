package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SectionDraftRepository extends JpaRepository<SectionDraft, Long> {

    /**
     * 섹션의 최신 버전 초안을 조회한다. (외부 검토자가 읽을 현재 본문)
     */
    Optional<SectionDraft> findTopByProjectSection_IdOrderByVersionDesc(Long projectSectionId);
}
