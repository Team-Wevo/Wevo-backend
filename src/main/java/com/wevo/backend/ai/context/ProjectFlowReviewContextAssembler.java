package com.wevo.backend.ai.context;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.service.ProjectFlowReviewContract;
import com.wevo.backend.project.service.ProjectAiContextQueryService;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.ConfirmedSectionContent;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
import com.wevo.backend.section.service.SectionDependencyEdge;
import com.wevo.backend.section.service.SectionDependencyQueryService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 공개 read contract만 사용해 결정적인 project-level context와 snapshot을 조립한다. */
@Service
@Transactional(readOnly = true)
public class ProjectFlowReviewContextAssembler {

    private final ProjectAiContextQueryService projectQueryService;
    private final SectionConfirmationQueryService confirmationQueryService;
    private final SectionDependencyQueryService dependencyQueryService;
    private final AiProperties properties;
    private final AiInputSnapshotHasher hasher;

    public ProjectFlowReviewContextAssembler(
            ProjectAiContextQueryService projectQueryService,
            SectionConfirmationQueryService confirmationQueryService,
            SectionDependencyQueryService dependencyQueryService,
            AiProperties properties,
            AiInputSnapshotHasher hasher
    ) {
        this.projectQueryService = projectQueryService;
        this.confirmationQueryService = confirmationQueryService;
        this.dependencyQueryService = dependencyQueryService;
        this.properties = properties;
        this.hasher = hasher;
    }

    public AssembledAiContext<ProjectFlowReviewContext> assemble(VerifiedProjectAccess access) {
        if (access == null) {
            throw new IllegalArgumentException("검증된 project 접근은 필수입니다.");
        }
        try {
            var project = projectQueryService.getProjectContext(access);
            var summary = confirmationQueryService.getConfirmationSummary(access);
            List<ConfirmedSectionContent> raw = new ArrayList<>(
                    confirmationQueryService.findConfirmedContents(access));
            raw.sort(Comparator.comparing(ConfirmedSectionContent::order)
                    .thenComparing(ConfirmedSectionContent::sectionId));

            Set<Long> ids = new HashSet<>();
            Set<String> keys = new HashSet<>();
            Map<String, Long> idByKey = new HashMap<>();
            List<ProjectFlowReviewSectionContext> sections = new ArrayList<>();
            for (ConfirmedSectionContent item : raw) {
                if (item == null || item.sectionId() == null || item.confirmedVersion() == null
                        || item.confirmedVersion() <= 0 || item.order() == null || item.order() <= 0
                        || !ids.add(item.sectionId()) || !keys.add(item.templateKey())) {
                    throw new IllegalStateException("확정 section 식별 데이터가 유효하지 않습니다.");
                }
                idByKey.put(item.templateKey(), item.sectionId());
                sections.add(new ProjectFlowReviewSectionContext(
                        item.sectionId(), required(item.templateKey()), item.order(),
                        item.confirmedVersion(), required(item.title()),
                        new AiTemplateContext(required(item.templateKey()),
                                optional(item.templateDescription()), optional(item.templateGuide())),
                        required(item.content())));
            }

            List<ProjectFlowDependencyContext> dependencies = dependencyQueryService
                    .findRequiresEdges(access).stream()
                    .map(edge -> resolve(edge, idByKey))
                    .sorted(Comparator.comparing(ProjectFlowDependencyContext::fromSectionId)
                            .thenComparing(ProjectFlowDependencyContext::toSectionId))
                    .toList();
            if (new HashSet<>(dependencies).size() != dependencies.size()) {
                throw new IllegalStateException("중복된 section dependency가 있습니다.");
            }

            ProjectFlowReviewContext context = new ProjectFlowReviewContext(
                    new AiProjectIdentity(project.projectId(), required(project.title()), project.outputType()),
                    new AiProjectBrief(optional(project.description()), optional(project.ideaText()),
                            optional(project.audience())),
                    summary.totalCount(), summary.confirmedCount(), sections, dependencies);
            AiProperties.ModelOptions options = properties.optionsFor(AiFeature.PROJECT_FLOW_REVIEW);
            AiInputSnapshot snapshot = hasher.canonicalSnapshot(new SnapshotEnvelope(
                    AiInputSnapshotHasher.CANONICAL_VERSION,
                    ProjectFlowReviewContract.SOURCE_VERSION,
                    ProjectFlowReviewContract.PROMPT_VERSION,
                    ProjectFlowReviewContract.SCHEMA_VERSION,
                    options.model(), options.maxInputTokens(), options.maxOutputTokens(), context));
            return new AssembledAiContext<>(context, snapshot);
        } catch (AiContextAssemblyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiContextAssemblyException(exception);
        }
    }

    private ProjectFlowDependencyContext resolve(SectionDependencyEdge edge, Map<String, Long> idByKey) {
        Long from = idByKey.get(edge.fromTemplateKey());
        Long to = idByKey.get(edge.toTemplateKey());
        if (from == null || to == null) {
            throw new IllegalStateException("dependency가 확정 section과 일치하지 않습니다.");
        }
        return new ProjectFlowDependencyContext(from, to);
    }

    private String required(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("흐름 점검 필수 문자열이 누락되었습니다.");
        }
        return normalize(value);
    }

    private String optional(String value) {
        return StringUtils.hasText(value) ? normalize(value) : null;
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private record SnapshotEnvelope(
            String canonicalVersion,
            String sourceVersion,
            String promptVersion,
            String schemaVersion,
            String modelId,
            int maxInputTokens,
            int maxOutputTokens,
            ProjectFlowReviewContext context
    ) {
    }
}
