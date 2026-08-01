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

/** 결과 set 생성 당시 입력 의견 ID와 제출본 hash를 보존하는 snapshot 항목. */
@Entity
@Table(name = "opinion_cluster_inputs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_opinion_cluster_inputs_set_opinion",
                columnNames = {"cluster_set_id", "opinion_id"}),
        @UniqueConstraint(name = "uk_opinion_cluster_inputs_set_order",
                columnNames = {"cluster_set_id", "sort_order"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpinionClusterInput extends BaseTimeEntity {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cluster_set_id", nullable = false, updatable = false)
    private OpinionClusterSet clusterSet;

    @Column(name = "opinion_id", nullable = false, updatable = false)
    private Long opinionId;

    @Column(name = "submitted_content_hash", length = 64, nullable = false, updatable = false)
    private String submittedContentHash;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Builder
    private OpinionClusterInput(
            OpinionClusterSet clusterSet,
            Long opinionId,
            String submittedContentHash,
            int sortOrder
    ) {
        if (clusterSet == null || opinionId == null || sortOrder <= 0
                || submittedContentHash == null
                || !SHA_256.matcher(submittedContentHash).matches()) {
            throw new IllegalArgumentException("cluster input snapshot이 유효하지 않습니다.");
        }
        this.clusterSet = clusterSet;
        this.opinionId = opinionId;
        this.submittedContentHash = submittedContentHash;
        this.sortOrder = sortOrder;
    }
}
