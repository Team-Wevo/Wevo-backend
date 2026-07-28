package com.wevo.backend.ai.context;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.service.CurrentSynthesisContext;
import com.wevo.backend.issue.service.ConflictDecisionContext;
import com.wevo.backend.issue.service.GapAnswerContext;
import com.wevo.backend.issue.service.GapIssueContext;
import com.wevo.backend.issue.service.SynthesisSetQueryService;
import com.wevo.backend.issue.service.SynthesisOpinionEvidenceContext;
import com.wevo.backend.opinion.service.SubmittedOpinionContext;
import com.wevo.backend.opinion.service.SubmittedOpinionQueryService;
import com.wevo.backend.project.service.ProjectAiContext;
import com.wevo.backend.project.service.ProjectAiContextQueryService;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.PrerequisiteSectionContent;
import com.wevo.backend.section.service.SectionAiContextQueryService;
import com.wevo.backend.section.service.SectionAiMetadata;
import com.wevo.backend.section.service.SectionVersionedContent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 소유 도메인의 read contract만 사용해 기능별 Provider 중립 context를 조립한다.
 */
@Service
@Transactional(readOnly = true)
public class AiContextAssembler {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ProjectAiContextQueryService projectQueryService;
    private final SubmittedOpinionQueryService opinionQueryService;
    private final SectionAiContextQueryService sectionQueryService;
    private final SynthesisSetQueryService synthesisQueryService;
    private final SectionAccessGuard sectionAccessGuard;
    private final AiProperties aiProperties;
    private final AiInputSnapshotHasher snapshotHasher;

    public AiContextAssembler(
            ProjectAiContextQueryService projectQueryService,
            SubmittedOpinionQueryService opinionQueryService,
            SectionAiContextQueryService sectionQueryService,
            SynthesisSetQueryService synthesisQueryService,
            SectionAccessGuard sectionAccessGuard,
            AiProperties aiProperties,
            AiInputSnapshotHasher snapshotHasher
    ) {
        this.projectQueryService = projectQueryService;
        this.opinionQueryService = opinionQueryService;
        this.sectionQueryService = sectionQueryService;
        this.synthesisQueryService = synthesisQueryService;
        this.sectionAccessGuard = sectionAccessGuard;
        this.aiProperties = aiProperties;
        this.snapshotHasher = snapshotHasher;
    }

    public AssembledAiContext<IssueDetectionContext> assembleIssueDetection(
            VerifiedProjectAccess access,
            Long sectionId
    ) {
        requireAssemblyTarget(access, sectionId);
        return safely(() -> {
            CommonInputs common = commonInputs(access, sectionId);
            IssueDetectionContext context = new IssueDetectionContext(
                    common.projectIdentity(),
                    common.projectBrief(),
                    common.section(),
                    opinions(access, sectionId)
            );
            return assembled(context);
        });
    }

    public AssembledAiContext<OpinionSynthesisContext> assembleOpinionSynthesis(
            VerifiedProjectAccess access,
            Long sectionId
    ) {
        requireAssemblyTarget(access, sectionId);
        return safely(() -> {
            CommonInputs common = commonInputs(access, sectionId);
            OpinionSynthesisContext context = new OpinionSynthesisContext(
                    common.projectIdentity(),
                    common.projectBrief(),
                    common.section(),
                    opinions(access, sectionId),
                    currentSynthesis(access, sectionId),
                    prerequisites(sectionQueryService.findDirectConfirmedPrerequisites(access, sectionId))
            );
            return assembled(context);
        });
    }

    public AssembledAiContext<DraftGenerationContext> assembleDraftGeneration(
            VerifiedProjectAccess access,
            Long sectionId
    ) {
        AssembledAiContext<DraftGenerationContext> assembled =
                assembleDraftGenerationSnapshot(access, sectionId);
        DraftGenerationContext context = assembled.context();
        if (context.section().synthesisStale()
                || context.synthesis().opinionGateGeneration()
                != context.section().opinionGateGeneration()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        if (context.synthesis().hasUnresolvedConflict()) {
            throw new BusinessException(ErrorCode.ISSUE_CONFLICT_UNDECIDED);
        }
        return assembled;
    }

    /**
     * 작업 실행·완료 시 입력 변경 대조를 위한 ungated snapshot.
     * 실행 조건이 깨진 상태도 hash로 표현해 기존 작업과 달라지게 한다.
     */
    public AssembledAiContext<DraftGenerationContext> assembleDraftGenerationSnapshot(
            VerifiedProjectAccess access,
            Long sectionId
    ) {
        requireAssemblyTarget(access, sectionId);
        return safely(() -> {
            CommonInputs common = commonInputs(access, sectionId);
            AiDraftSynthesisContext synthesis = currentDraftSynthesis(access, sectionId);
            SectionVersionedContent latest =
                    sectionQueryService.getLatestDraftOrEmpty(access, sectionId);
            DraftGenerationContext context = new DraftGenerationContext(
                    common.projectIdentity(),
                    common.projectBrief(),
                    common.section(),
                    synthesis,
                    new AiBaseDraftContext(
                            latest.contentVersion(),
                            normalizeOptional(latest.content())
                    ),
                    prerequisites(sectionQueryService.findDirectConfirmedPrerequisites(access, sectionId))
            );
            return assembled(context);
        });
    }

    public AssembledAiContext<DraftReviewContext> assembleDraftReview(
            VerifiedProjectAccess access,
            Long sectionId
    ) {
        requireAssemblyTarget(access, sectionId);
        return safely(() -> {
            CommonInputs common = commonInputs(access, sectionId);
            SectionVersionedContent draft = sectionQueryService.getLatestDraft(access, sectionId);
            requireVersionedContent(draft, sectionId);
            List<AiPrerequisiteContext> prerequisites = prerequisites(
                    sectionQueryService.findDirectLatestPrerequisites(access, sectionId));
            String dependencyHash = snapshotHasher.hashCanonical(prerequisites).substring(0, 16);
            DraftReviewContext context = new DraftReviewContext(
                    common.projectIdentity(),
                    common.section(),
                    draft.contentVersion(),
                    normalizeRequired(draft.content()),
                    prerequisites,
                    dependencyHash
            );
            return assembled(context);
        });
    }

    private CommonInputs commonInputs(VerifiedProjectAccess access, Long sectionId) {
        ProjectAiContext project = projectQueryService.getProjectContext(access);
        SectionAiMetadata section = sectionQueryService.getMetadata(access, sectionId);
        if (!access.projectId().equals(project.projectId())
                || !project.projectId().equals(section.projectId())
                || !sectionId.equals(section.sectionId())
                || project.outputType() == null
                || section.status() == null
                || section.opinionGateGeneration() < 0) {
            throw new IllegalStateException("AI context project/section 데이터가 유효하지 않습니다.");
        }
        AiProjectIdentity identity = new AiProjectIdentity(
                project.projectId(),
                normalizeRequired(project.title()),
                project.outputType()
        );
        AiProjectBrief brief = new AiProjectBrief(
                normalizeOptional(project.description()),
                normalizeOptional(project.ideaText()),
                normalizeOptional(project.audience())
        );
        AiSectionContext sectionContext = new AiSectionContext(
                section.sectionId(),
                normalizeRequired(section.title()),
                section.sectionOrder(),
                section.status(),
                section.opinionGateGeneration(),
                section.synthesisStale(),
                new AiTemplateContext(
                        normalizeRequired(section.templateKey()),
                        normalizeOptional(section.templateDescription()),
                        normalizeOptional(section.templateGuide())
                )
        );
        return new CommonInputs(identity, brief, sectionContext);
    }

    private List<AiOpinionContext> opinions(VerifiedProjectAccess access, Long sectionId) {
        List<SubmittedOpinionContext> sorted = new ArrayList<>(
                opinionQueryService.findSubmittedOpinions(access, sectionId));
        sorted.sort(Comparator.comparing(SubmittedOpinionContext::submittedAt)
                .thenComparing(SubmittedOpinionContext::opinionId));

        Set<Long> opinionIds = new HashSet<>();
        Map<Long, String> aliases = new LinkedHashMap<>();
        List<AiOpinionContext> result = new ArrayList<>();
        for (SubmittedOpinionContext opinion : sorted) {
            if (opinion == null
                    || opinion.opinionId() == null
                    || opinion.submittedAt() == null
                    || opinion.authorReference() == null
                    || !opinionIds.add(opinion.opinionId())) {
                throw new IllegalStateException("AI context 제출 의견 식별 데이터가 유효하지 않습니다.");
            }
            String alias = aliases.computeIfAbsent(
                    opinion.authorReference(),
                    ignored -> "member-" + (aliases.size() + 1));
            result.add(new AiOpinionContext(
                    opinion.opinionId(),
                    alias,
                    normalizeRequired(opinion.submittedContent()),
                    DATE_TIME_FORMAT.format(opinion.submittedAt())
            ));
        }
        return List.copyOf(result);
    }

    private AiSynthesisContext currentSynthesis(VerifiedProjectAccess access, Long sectionId) {
        CurrentSynthesisContext current = synthesisQueryService.getCurrentForAiContext(
                sectionAccessGuard.verifySectionAccess(access, sectionId));
        requireCurrentSynthesisIdentity(current);
        List<AiGapAnswerContext> answers = gapAnswers(current);
        return new AiSynthesisContext(
                current.synthesisSetId(),
                current.opinionGateGeneration(),
                normalizeRequired(current.consensusSummary()),
                answers
        );
    }

    private AiDraftSynthesisContext currentDraftSynthesis(
            VerifiedProjectAccess access,
            Long sectionId
    ) {
        CurrentSynthesisContext current = synthesisQueryService.getCurrentForDraftGeneration(
                sectionAccessGuard.verifySectionAccess(access, sectionId));
        requireCurrentSynthesisIdentity(current);
        List<AiDraftGapAnswerContext> answers = draftGapAnswers(current);
        List<AiOpinionEvidenceContext> opinionEvidence =
                current.opinionEvidence().stream()
                        .sorted(Comparator.comparing(SynthesisOpinionEvidenceContext::opinionId))
                        .map(evidence -> new AiOpinionEvidenceContext(
                                requirePositive(evidence.opinionId(), "opinionId"),
                                normalizeRequired(evidence.content())
                        ))
                        .toList();
        List<AiConflictDecisionContext> decisions =
                current.conflictDecisions().stream()
                        .sorted(Comparator.comparing(ConflictDecisionContext::issueId))
                        .map(decision -> new AiConflictDecisionContext(
                                requirePositive(decision.issueId(), "issueId"),
                                requirePositive(decision.decisionId(), "decisionId"),
                                normalizeRequired(decision.description()),
                                normalizeRequired(decision.question()),
                                normalizeRequired(decision.decision()),
                                sortedPositiveIds(decision.evidenceOpinionIds(), "decision evidence")
                        ))
                        .toList();
        List<AiGapIssueContext> gapIssues =
                current.gapIssues().stream()
                        .sorted(Comparator.comparing(GapIssueContext::issueId))
                        .map(gap -> new AiGapIssueContext(
                                requirePositive(gap.issueId(), "issueId"),
                                normalizeRequired(gap.description()),
                                gap.answered(),
                                sortedPositiveIds(gap.evidenceOpinionIds(), "GAP evidence")
                        ))
                        .toList();
        return new AiDraftSynthesisContext(
                current.synthesisSetId(),
                current.opinionGateGeneration(),
                normalizeRequired(current.consensusSummary()),
                answers,
                opinionEvidence,
                decisions,
                gapIssues,
                current.hasUnresolvedConflict()
        );
    }

    private void requireCurrentSynthesisIdentity(CurrentSynthesisContext current) {
        if (current.synthesisSetId() == null || current.opinionGateGeneration() < 0) {
            throw new IllegalStateException("AI context current synthesis 식별 데이터가 유효하지 않습니다.");
        }
    }

    private List<AiGapAnswerContext> gapAnswers(CurrentSynthesisContext current) {
        List<AiGapAnswerContext> answers = new ArrayList<>();
        Set<Long> answerIds = new HashSet<>();
        List<GapAnswerContext> sorted = new ArrayList<>(current.gapAnswers());
        sorted.sort(Comparator.comparing(GapAnswerContext::answeredAt)
                .thenComparing(GapAnswerContext::answerId));
        for (GapAnswerContext answer : sorted) {
            if (answer == null
                    || answer.sourceIssueId() == null
                    || answer.answerId() == null
                    || answer.answeredAt() == null
                    || !answerIds.add(answer.answerId())) {
                throw new IllegalStateException("AI context GAP 답변 식별 데이터가 유효하지 않습니다.");
            }
            answers.add(new AiGapAnswerContext(
                    answer.sourceIssueId(),
                    answer.answerId(),
                    normalizeRequired(answer.content()),
                    DATE_TIME_FORMAT.format(answer.answeredAt())));
        }
        return List.copyOf(answers);
    }

    private List<AiDraftGapAnswerContext> draftGapAnswers(CurrentSynthesisContext current) {
        List<GapAnswerContext> sorted = new ArrayList<>(current.gapAnswers());
        sorted.sort(Comparator.comparing(GapAnswerContext::answeredAt)
                .thenComparing(GapAnswerContext::answerId));
        Set<Long> answerIds = new HashSet<>();
        List<AiDraftGapAnswerContext> result = new ArrayList<>();
        for (GapAnswerContext answer : sorted) {
            if (answer == null || answer.sourceIssueId() == null || answer.answerId() == null
                    || answer.answeredAt() == null || !answerIds.add(answer.answerId())) {
                throw new IllegalStateException("AI context GAP 답변 식별 데이터가 유효하지 않습니다.");
            }
            result.add(new AiDraftGapAnswerContext(
                    answer.sourceIssueId(),
                    answer.answerId(),
                    normalizeRequired(answer.content()),
                    DATE_TIME_FORMAT.format(answer.answeredAt()),
                    answer.inherited()
            ));
        }
        return List.copyOf(result);
    }

    private Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalStateException("AI context " + field + "가 유효하지 않습니다.");
        }
        return value;
    }

    private List<Long> sortedPositiveIds(List<Long> values, String field) {
        if (values == null) {
            throw new IllegalStateException("AI context " + field + "가 유효하지 않습니다.");
        }
        List<Long> sorted = new ArrayList<>(values);
        if (sorted.stream().anyMatch(value -> value == null || value <= 0)
                || new HashSet<>(sorted).size() != sorted.size()) {
            throw new IllegalStateException("AI context " + field + "가 유효하지 않습니다.");
        }
        sorted.sort(Long::compareTo);
        return List.copyOf(sorted);
    }

    private List<AiPrerequisiteContext> prerequisites(List<PrerequisiteSectionContent> inputs) {
        List<PrerequisiteSectionContent> sorted = new ArrayList<>(inputs);
        sorted.sort(Comparator.comparingInt(PrerequisiteSectionContent::sectionOrder)
                .thenComparing(PrerequisiteSectionContent::sectionId));
        Set<Long> sectionIds = new HashSet<>();
        List<AiPrerequisiteContext> result = new ArrayList<>();
        for (PrerequisiteSectionContent input : sorted) {
            if (input == null
                    || input.sectionId() == null
                    || input.contentVersion() <= 0
                    || !sectionIds.add(input.sectionId())) {
                throw new IllegalStateException("AI context 상위 section 식별 데이터가 유효하지 않습니다.");
            }
            result.add(new AiPrerequisiteContext(
                    input.sectionId(),
                    normalizeRequired(input.templateKey()),
                    input.sectionOrder(),
                    input.contentVersion(),
                    normalizeRequired(input.content())
            ));
        }
        return List.copyOf(result);
    }

    private void requireVersionedContent(SectionVersionedContent content, Long sectionId) {
        if (content == null
                || !sectionId.equals(content.sectionId())
                || content.contentVersion() <= 0
                || !StringUtils.hasText(content.content())) {
            throw new IllegalStateException("AI context draft 데이터가 유효하지 않습니다.");
        }
    }

    private <T extends AiFeatureContext> AssembledAiContext<T> assembled(T context) {
        return new AssembledAiContext<>(
                context,
                snapshotHasher.snapshot(context, aiProperties.optionsFor(context.feature()))
        );
    }

    private <T> T safely(ContextSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (AiContextAssemblyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiContextAssemblyException(exception);
        }
    }

    private void requireAssemblyTarget(VerifiedProjectAccess access, Long sectionId) {
        if (access == null || sectionId == null) {
            throw new IllegalArgumentException("검증된 project 접근과 sectionId는 필수입니다.");
        }
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("AI context 필수 문자열이 누락되었습니다.");
        }
        return normalizeLineEndings(value);
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? normalizeLineEndings(value) : null;
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    @FunctionalInterface
    private interface ContextSupplier<T> {
        T get();
    }

    private record CommonInputs(
            AiProjectIdentity projectIdentity,
            AiProjectBrief projectBrief,
            AiSectionContext section
    ) {
    }
}
