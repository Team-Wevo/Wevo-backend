package com.wevo.backend.section.repository;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.TemplateDependency;
import com.wevo.backend.section.domain.TemplateDependencyType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateDependencyRepository extends JpaRepository<TemplateDependency, Long> {

    @Query("SELECT d FROM TemplateDependency d "
            + "JOIN FETCH d.fromTemplate f JOIN FETCH d.toTemplate t "
            + "ORDER BY f.resultType, f.orderNo, f.id, t.orderNo, t.id")
    List<TemplateDependency> findAllWithTemplates();

    @Query("SELECT d FROM TemplateDependency d JOIN FETCH d.toTemplate t "
            + "WHERE d.fromTemplate.id = :templateId "
            + "AND d.fromTemplate.resultType = :resultType "
            + "AND t.resultType = :resultType "
            + "AND d.dependencyType = :dependencyType "
            + "ORDER BY t.orderNo ASC, t.id ASC")
    List<TemplateDependency> findDirectPrerequisites(
            @Param("templateId") Long templateId,
            @Param("resultType") OutputType resultType,
            @Param("dependencyType") TemplateDependencyType dependencyType);

    @Query("SELECT d FROM TemplateDependency d JOIN FETCH d.fromTemplate f "
            + "WHERE d.toTemplate.id = :templateId "
            + "AND d.toTemplate.resultType = :resultType "
            + "AND f.resultType = :resultType "
            + "AND d.dependencyType = :dependencyType "
            + "ORDER BY f.orderNo ASC, f.id ASC")
    List<TemplateDependency> findDirectDependents(
            @Param("templateId") Long templateId,
            @Param("resultType") OutputType resultType,
            @Param("dependencyType") TemplateDependencyType dependencyType);
}
