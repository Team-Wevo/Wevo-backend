package com.wevo.backend.ai.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 점검 당시 사용한 section/version/content hash. */
@Entity
@Table(name = "project_flow_check_inputs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_flow_inputs_check_section", columnNames = {"flow_check_id", "project_section_id"}),
        @UniqueConstraint(name = "uk_project_flow_inputs_check_order", columnNames = {"flow_check_id", "sort_order"})})
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectFlowCheckInput extends BaseTimeEntity {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_check_id", nullable = false, updatable = false) private ProjectFlowCheck flowCheck;
    @Column(name = "project_section_id", nullable = false, updatable = false) private Long projectSectionId;
    @Column(name = "confirmed_version", nullable = false, updatable = false) private int confirmedVersion;
    @Column(name = "section_key", length = 100, nullable = false, updatable = false) private String sectionKey;
    @Column(name = "section_title", length = 200, nullable = false, updatable = false) private String sectionTitle;
    @Column(name = "content_hash", length = 64, nullable = false, updatable = false) private String contentHash;
    @Column(name = "sort_order", nullable = false, updatable = false) private int sortOrder;

    @Builder
    private ProjectFlowCheckInput(ProjectFlowCheck flowCheck, Long projectSectionId,
                                  int confirmedVersion, String sectionKey, String sectionTitle,
                                  String contentHash, int sortOrder) {
        if (flowCheck == null || projectSectionId == null || projectSectionId <= 0 || confirmedVersion <= 0 || sortOrder <= 0
                || contentHash == null || !SHA_256.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("flow check input이 유효하지 않습니다.");
        }
        this.flowCheck = flowCheck; this.projectSectionId = projectSectionId;
        if (sectionKey == null || sectionKey.isBlank() || sectionKey.length() > 100
                || sectionTitle == null || sectionTitle.isBlank() || sectionTitle.length() > 200) {
            throw new IllegalArgumentException("flow check section 표시값이 유효하지 않습니다.");
        }
        this.sectionKey = sectionKey; this.sectionTitle = sectionTitle;
        this.confirmedVersion = confirmedVersion; this.contentHash = contentHash; this.sortOrder = sortOrder;
    }
}
