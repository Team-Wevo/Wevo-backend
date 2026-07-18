package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.DraftLease;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftLeaseRepository extends JpaRepository<DraftLease, Long> {

    Optional<DraftLease> findByProjectSection_Id(Long projectSectionId);
}
