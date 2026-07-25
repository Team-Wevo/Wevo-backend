package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 의견 정리 실행 요청을 받아 비동기 작업을 큐잉하고 {@code requestId}를 반환한다. (§3.8.1)
 *
 * <p><b>검증 순서 고정</b> (§3.8 머리말): ① 존재·인증·권한(OWNER) → ② 동일 스냅샷 기존 작업의
 * 멱등 재사용 → ③ 상태 검증(SYNTHESIZING). 재사용 가능한 작업이 있으면 상태 검증 없이 그 작업을
 * 반환한다.
 *
 * <p><b>실행 트리거</b> — 이 서비스는 작업을 <b>QUEUED로 만들기만</b> 하고, 실제 실행은
 * {@link AiJobDispatchScheduler}가 DB 큐를 폴링해 처리한다. 요청 트랜잭션에서 섹션을 잠그지
 * 않는다 — 잠그면 AI 작업 INSERT가 잡는 FK {@code KEY SHARE} 잠금과 {@code FOR UPDATE}가
 * 충돌해(작업 INSERT는 별도 트랜잭션) self-wait가 생긴다. 동시 요청의 중복 생성 방지는 AI 작업의
 * 멱등키 유니크 제약이, 실행 중 입력 변경 방지는 완료 시점 스냅샷 재대조가 담당하므로 요청 시점
 * 섹션 잠금은 필요하지 않다.
 */
@Service
public class SynthesisRequestService {

    private static final Set<AiJobStatus> REUSABLE_STATUSES =
            Set.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.SUCCEEDED);

    private final SectionAccessGuard sectionAccessGuard;
    private final SynthesisSnapshotAssembler snapshotAssembler;
    private final SynthesisInputHasher inputHasher;
    private final AiJobService aiJobService;
    private final AiProperties aiProperties;
    private final UserService userService;

    public SynthesisRequestService(SectionAccessGuard sectionAccessGuard,
                                   SynthesisSnapshotAssembler snapshotAssembler,
                                   SynthesisInputHasher inputHasher,
                                   AiJobService aiJobService,
                                   AiProperties aiProperties,
                                   UserService userService) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.snapshotAssembler = snapshotAssembler;
        this.inputHasher = inputHasher;
        this.aiJobService = aiJobService;
        this.aiProperties = aiProperties;
        this.userService = userService;
    }

    /**
     * 의견 정리를 실행 요청한다.
     *
     * @return 폴링에 사용할 AI 작업 {@code requestId} (재사용 시 기존 작업의 것)
     * @throws BusinessException 섹션이 없거나 비멤버면 {@code SECTION_NOT_FOUND}(404),
     *                           MEMBER면 {@code FORBIDDEN}(403),
     *                           새 작업을 만들어야 하는데 섹션이 {@code SYNTHESIZING}이 아니면
     *                           {@code INVALID_SECTION_STATUS_TRANSITION}(409)
     */
    @Transactional(readOnly = true)
    public UUID requestSynthesis(Long projectSectionId, Long userId) {
        // ① 존재·인증·권한 — OWNER만. 섹션을 잠그지 않는다(클래스 주석의 self-wait 회피).
        ProjectSection section = sectionAccessGuard.requireOwnedSection(projectSectionId, userId);

        // 입력 스냅샷과 해시 계산 (요청 시점 상태 기준)
        SynthesisInputSnapshot snapshot = snapshotAssembler.assemble(projectSectionId);
        String inputSnapshotHash = inputHasher.hash(snapshot);
        AiProperties.ModelOptions options = aiProperties.optionsFor(AiFeature.OPINION_SYNTHESIS);

        // ② 멱등 재사용 — 재사용 가능한 기존 작업이 있으면 상태 검증 없이 반환
        Optional<AiJobCreateResult> existing = aiJobService.findLatest(
                idempotencyInput(section, inputSnapshotHash, options));
        if (existing.isPresent() && REUSABLE_STATUSES.contains(existing.get().status())) {
            return existing.get().requestId();
        }

        // ③ 새 작업을 만들어야 하는 경우에만 상태 검증
        if (section.getStatus() != ProjectSectionStatus.SYNTHESIZING) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }

        User requestedBy = userService.getUserReference(userId);
        AiJobCreateResult result = existing
                .map(previous -> aiJobService.retry(previous.requestId(), requestedBy))
                .orElseGet(() -> aiJobService.createOrGet(
                        createCommand(section, requestedBy, inputSnapshotHash, options)));
        return result.requestId();
    }

    private AiJobIdempotencyInput idempotencyInput(ProjectSection section, String inputSnapshotHash,
                                                   AiProperties.ModelOptions options) {
        return new AiJobIdempotencyInput(
                AiFeature.OPINION_SYNTHESIS,
                section.getProject().getId(),
                section.getId(),
                inputSnapshotHash,
                SynthesisContract.SOURCE_VERSION,
                SynthesisContract.PROMPT_VERSION,
                SynthesisContract.SCHEMA_VERSION,
                options.model(),
                options.maxOutputTokens()
        );
    }

    private AiJobCreateCommand createCommand(ProjectSection section, User requestedBy,
                                             String inputSnapshotHash, AiProperties.ModelOptions options) {
        return new AiJobCreateCommand(
                section.getProject(),
                section,
                requestedBy,
                AiFeature.OPINION_SYNTHESIS,
                inputSnapshotHash,
                SynthesisContract.SOURCE_VERSION,
                SynthesisContract.PROMPT_VERSION,
                SynthesisContract.SCHEMA_VERSION,
                options.model(),
                options.maxOutputTokens()
        );
    }
}
