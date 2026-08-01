package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.domain.OpinionCluster;
import com.wevo.backend.ai.domain.OpinionClusterInput;
import com.wevo.backend.ai.domain.OpinionClusterMember;
import com.wevo.backend.ai.domain.OpinionClusterSet;
import com.wevo.backend.ai.dto.model.OpinionClusterOutput;
import com.wevo.backend.ai.repository.OpinionClusterInputRepository;
import com.wevo.backend.ai.repository.OpinionClusterMemberRepository;
import com.wevo.backend.ai.repository.OpinionClusterRepository;
import com.wevo.backend.ai.repository.OpinionClusterSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 스냅샷과 partition 결과를 이전 이력을 건드리지 않고 새 set으로 저장한다. */
@Service
public class OpinionClusteringResultWriter {

    private final OpinionClusterSetRepository setRepository;
    private final OpinionClusterRepository clusterRepository;
    private final OpinionClusterMemberRepository memberRepository;
    private final OpinionClusterInputRepository inputRepository;
    private final AiInputSnapshotHasher snapshotHasher;

    public OpinionClusteringResultWriter(
            OpinionClusterSetRepository setRepository,
            OpinionClusterRepository clusterRepository,
            OpinionClusterMemberRepository memberRepository,
            OpinionClusterInputRepository inputRepository,
            AiInputSnapshotHasher snapshotHasher
    ) {
        this.setRepository = setRepository;
        this.clusterRepository = clusterRepository;
        this.memberRepository = memberRepository;
        this.inputRepository = inputRepository;
        this.snapshotHasher = snapshotHasher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long persist(OpinionClusteringPersistCommand command) {
        var job = command.job();
        var context = command.context();
        OpinionClusterSet set = setRepository.save(OpinionClusterSet.builder()
                .sourceJob(job)
                .projectSectionId(context.sectionId())
                .opinionGateGeneration(context.opinionGateGeneration())
                .inputSnapshotHash(job.getInputSnapshotHash())
                .sourceVersion(job.getSourceVersion())
                .promptVersion(job.getPromptVersion())
                .schemaVersion(job.getSchemaVersion())
                .modelId(job.getModelId())
                .totalOpinionCount(context.opinions().size())
                .build());

        int inputOrder = 1;
        for (var opinion : context.opinions()) {
            inputRepository.save(OpinionClusterInput.builder()
                    .clusterSet(set)
                    .opinionId(opinion.opinionId())
                    .submittedContentHash(snapshotHasher.hashCanonical(opinion.submittedContent()))
                    .sortOrder(inputOrder++)
                    .build());
        }

        for (OpinionClusterOutput output : command.output().clusters()) {
            OpinionCluster cluster = clusterRepository.save(OpinionCluster.builder()
                    .clusterSet(set)
                    .sortOrder(output.order())
                    .title(output.title())
                    .summary(output.summary())
                    .build());
            int memberOrder = 1;
            for (Long opinionId : output.opinionIds()) {
                memberRepository.save(OpinionClusterMember.builder()
                        .clusterSetId(set.getId())
                        .cluster(cluster)
                        .opinionId(opinionId)
                        .sortOrder(memberOrder++)
                        .build());
            }
        }
        return set.getId();
    }
}
