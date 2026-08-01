package com.wevo.backend.ai.domain;

import com.wevo.backend.ai.service.OpinionClusteringContract;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** cluster set 안의 표시 순서가 고정된 불변 cluster. */
@Entity
@Table(name = "opinion_clusters", uniqueConstraints = {
        @UniqueConstraint(name = "uk_opinion_clusters_set_order",
                columnNames = {"cluster_set_id", "sort_order"}),
        @UniqueConstraint(name = "uk_opinion_clusters_id_set",
                columnNames = {"id", "cluster_set_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpinionCluster extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cluster_set_id", nullable = false, updatable = false)
    private OpinionClusterSet clusterSet;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(length = OpinionClusteringContract.MAX_TITLE_LENGTH, nullable = false, updatable = false)
    private String title;

    @Column(length = OpinionClusteringContract.MAX_SUMMARY_LENGTH, nullable = false, updatable = false)
    private String summary;

    @Builder
    private OpinionCluster(OpinionClusterSet clusterSet, int sortOrder, String title, String summary) {
        if (clusterSet == null || sortOrder <= 0) {
            throw new IllegalArgumentException("cluster set과 순서는 필수입니다.");
        }
        this.clusterSet = clusterSet;
        this.sortOrder = sortOrder;
        this.title = requireText(title, OpinionClusteringContract.MAX_TITLE_LENGTH, "title");
        this.summary = requireText(summary, OpinionClusteringContract.MAX_SUMMARY_LENGTH, "summary");
    }

    private String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + "가 유효하지 않습니다.");
        }
        return value;
    }
}
