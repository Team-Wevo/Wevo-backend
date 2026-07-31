package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.domain.SectionAuthorIntentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionAuthorIntentRepository extends JpaRepository<SectionAuthorIntent, Long> {

    Optional<SectionAuthorIntent> findByProjectSection_IdAndContentVersion(
            Long sectionId, Integer contentVersion);

    Optional<SectionAuthorIntent> findByProjectSection_IdAndContentVersionAndStatus(
            Long sectionId, Integer contentVersion, SectionAuthorIntentStatus status);
}
