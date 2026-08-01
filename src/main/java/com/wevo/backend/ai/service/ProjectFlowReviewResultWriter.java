package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.domain.ProjectFlowCheck;
import com.wevo.backend.ai.domain.ProjectFlowCheckFinding;
import com.wevo.backend.ai.domain.ProjectFlowCheckInput;
import com.wevo.backend.ai.domain.ProjectFlowFindingSection;
import com.wevo.backend.ai.repository.ProjectFlowCheckFindingRepository;
import com.wevo.backend.ai.repository.ProjectFlowCheckInputRepository;
import com.wevo.backend.ai.repository.ProjectFlowCheckRepository;
import com.wevo.backend.ai.repository.ProjectFlowFindingSectionRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectFlowReviewResultWriter {
    private final ProjectFlowCheckRepository checkRepository;
    private final ProjectFlowCheckInputRepository inputRepository;
    private final ProjectFlowCheckFindingRepository findingRepository;
    private final ProjectFlowFindingSectionRepository referenceRepository;
    private final AiInputSnapshotHasher hasher;

    public ProjectFlowReviewResultWriter(ProjectFlowCheckRepository checkRepository,
                                         ProjectFlowCheckInputRepository inputRepository,
                                         ProjectFlowCheckFindingRepository findingRepository,
                                         ProjectFlowFindingSectionRepository referenceRepository,
                                         AiInputSnapshotHasher hasher) {
        this.checkRepository = checkRepository; this.inputRepository = inputRepository;
        this.findingRepository = findingRepository; this.referenceRepository = referenceRepository;
        this.hasher = hasher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long persist(ProjectFlowReviewPersistCommand command) {
        var job = command.job();
        var context = command.context();
        ProjectFlowCheck check = checkRepository.save(ProjectFlowCheck.builder()
                .project(job.getProject()).sourceJob(job).inputSnapshotHash(job.getInputSnapshotHash())
                .sourceVersion(job.getSourceVersion()).promptVersion(job.getPromptVersion())
                .schemaVersion(job.getSchemaVersion()).modelId(job.getModelId())
                .sectionCount(context.sections().size()).findingCount(command.output().findings().size())
                .build());
        Map<Long, com.wevo.backend.ai.context.ProjectFlowReviewSectionContext> sectionById =
                context.sections().stream().collect(Collectors.toUnmodifiableMap(
                        section -> section.sectionId(), Function.identity()));
        for (var section : context.sections()) {
            inputRepository.save(ProjectFlowCheckInput.builder().flowCheck(check)
                    .projectSectionId(section.sectionId()).confirmedVersion(section.confirmedVersion())
                    .sectionKey(section.sectionKey()).sectionTitle(section.title())
                    .contentHash(hasher.hashCanonical(section.content())).sortOrder(section.order()).build());
        }
        int findingOrder = 1;
        for (var output : command.output().findings()) {
            ProjectFlowCheckFinding finding = findingRepository.save(ProjectFlowCheckFinding.builder()
                    .flowCheck(check).type(output.type()).description(output.description())
                    .suggestion(output.suggestion()).sortOrder(findingOrder++).build());
            int referenceOrder = 1;
            for (var reference : output.sections()) {
                var section = sectionById.get(reference.sectionId());
                if (section == null || !section.content().contains(reference.targetExcerpt())) {
                    throw new IllegalStateException("검증되지 않은 flow finding 참조입니다.");
                }
                referenceRepository.save(ProjectFlowFindingSection.builder().finding(finding)
                        .projectSectionId(section.sectionId()).confirmedVersion(section.confirmedVersion())
                        .targetExcerpt(reference.targetExcerpt()).sortOrder(referenceOrder++).build());
            }
        }
        return check.getId();
    }
}
