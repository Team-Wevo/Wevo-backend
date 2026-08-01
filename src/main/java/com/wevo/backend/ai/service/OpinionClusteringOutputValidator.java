package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.dto.model.OpinionClusterOutput;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** cluster 모양과 전체 의견의 정확한 partition을 검증한다. */
@Component
public class OpinionClusteringOutputValidator
        implements StructuredOutputValidator<OpinionClusteringOutput> {

    @Override
    public void validate(
            OpinionClusteringOutput output,
            StructuredOutputValidationContext context
    ) {
        if (output == null || context == null) {
            throw reject();
        }
        List<OpinionClusterOutput> clusters = output.clusters();
        int allowedSize = context.allowedResourceIds().size();
        if (clusters == null || clusters.isEmpty()
                || clusters.size() > OpinionClusteringContract.MAX_CLUSTER_COUNT
                || clusters.size() > allowedSize) {
            throw reject();
        }

        Set<Long> covered = new HashSet<>();
        for (int index = 0; index < clusters.size(); index++) {
            OpinionClusterOutput cluster = clusters.get(index);
            if (cluster == null || cluster.order() != index + 1) {
                throw reject();
            }
            requireText(cluster.title(), OpinionClusteringContract.MAX_TITLE_LENGTH);
            requireText(cluster.summary(), OpinionClusteringContract.MAX_SUMMARY_LENGTH);
            if (cluster.opinionIds() == null || cluster.opinionIds().isEmpty()
                    || cluster.opinionIds().size() > allowedSize) {
                throw reject();
            }
            for (Long opinionId : cluster.opinionIds()) {
                context.requireAllowedResourceId(opinionId);
                if (!covered.add(opinionId)) {
                    throw reject();
                }
            }
        }
        if (!covered.equals(context.allowedResourceIds())) {
            throw reject();
        }
    }

    private void requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw reject();
        }
    }

    private StructuredOutputSemanticException reject() {
        return new StructuredOutputSemanticException();
    }
}
