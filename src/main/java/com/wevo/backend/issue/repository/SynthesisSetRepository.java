package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.SynthesisSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynthesisSetRepository extends JpaRepository<SynthesisSet, Long> {

    boolean existsByProjectSectionId(Long projectSectionId);

    Optional<SynthesisSet> findByRequestId(UUID requestId);

    /**
     * 섹션의 현재 정리 세트를 {@code createdAt DESC, id DESC} 기준으로 조회한다.
     */
    Optional<SynthesisSet> findTopByProjectSectionIdOrderByCreatedAtDescIdDesc(Long projectSectionId);
}
