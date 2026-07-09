package com.wevo.backend.section.repository;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.SectionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectionTemplateRepository extends JpaRepository<SectionTemplate, Long> {

    /** 결과물 유형별 섹션 템플릿을 순서대로 조회. (프로젝트 생성 시 고정 섹션 원형) */
    List<SectionTemplate> findByResultTypeOrderByOrderNo(OutputType resultType);
}
