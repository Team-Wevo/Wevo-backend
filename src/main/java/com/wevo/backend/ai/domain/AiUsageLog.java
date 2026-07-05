package com.wevo.backend.ai.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 기능 호출 사용량/비용 로그. 토큰 수와 추정 비용, 요청 상태를 기록한다.
 */
@Entity
@Table(name = "ai_usage_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_section_id")
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @Column(name = "feature_name", length = 50)
    private String featureName;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "estimated_cost", precision = 12, scale = 4)
    private BigDecimal estimatedCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", length = 20)
    private AiRequestStatus requestStatus;

    @Builder
    private AiUsageLog(
            Project project,
            ProjectSection projectSection,
            User requestedBy,
            String featureName,
            Integer promptTokens,
            Integer completionTokens,
            BigDecimal estimatedCost,
            AiRequestStatus requestStatus) {
        this.project = project;
        this.projectSection = projectSection;
        this.requestedBy = requestedBy;
        this.featureName = featureName;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.estimatedCost = estimatedCost;
        this.requestStatus = requestStatus;
    }
}
