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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** cluster와 실제 Opinion ID의 관계. set 전체 중복 금지는 DB unique 제약으로 보장한다. */
@Entity
@Table(name = "opinion_cluster_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_opinion_cluster_members_set_opinion",
                columnNames = {"cluster_set_id", "opinion_id"}),
        @UniqueConstraint(name = "uk_opinion_cluster_members_cluster_order",
                columnNames = {"cluster_id", "sort_order"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpinionClusterMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_set_id", nullable = false, updatable = false)
    private Long clusterSetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cluster_id", nullable = false, updatable = false)
    private OpinionCluster cluster;

    @Column(name = "opinion_id", nullable = false, updatable = false)
    private Long opinionId;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Builder
    private OpinionClusterMember(
            Long clusterSetId,
            OpinionCluster cluster,
            Long opinionId,
            int sortOrder
    ) {
        if (clusterSetId == null || cluster == null || opinionId == null || sortOrder <= 0) {
            throw new IllegalArgumentException("cluster member 식별값과 순서는 필수입니다.");
        }
        this.clusterSetId = clusterSetId;
        this.cluster = cluster;
        this.opinionId = opinionId;
        this.sortOrder = sortOrder;
    }
}
