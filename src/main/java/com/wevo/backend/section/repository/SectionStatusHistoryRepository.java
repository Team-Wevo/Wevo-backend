package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.SectionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionStatusHistoryRepository extends JpaRepository<SectionStatusHistory, Long> {
}
