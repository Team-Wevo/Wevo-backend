package com.wevo.backend.ai.service;

import com.wevo.backend.ai.dto.response.AuthorIntentResponse;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.service.ReviewLinkService;
import com.wevo.backend.section.domain.AuthorIntentTextPolicy;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.SectionAuthorIntentRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorIntentConfirmationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SectionAccessGuard sectionAccessGuard;
    private final SectionDraftRepository draftRepository;
    private final SectionAuthorIntentRepository intentRepository;
    private final UserService userService;
    private final ReviewLinkService reviewLinkService;
    private final AuthorIntentQueryService queryService;
    private final Clock clock;

    public AuthorIntentConfirmationService(
            SectionAccessGuard sectionAccessGuard,
            SectionDraftRepository draftRepository,
            SectionAuthorIntentRepository intentRepository,
            UserService userService,
            ReviewLinkService reviewLinkService,
            AuthorIntentQueryService queryService,
            Clock clock
    ) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.draftRepository = draftRepository;
        this.intentRepository = intentRepository;
        this.userService = userService;
        this.reviewLinkService = reviewLinkService;
        this.queryService = queryService;
        this.clock = clock;
    }

    @Transactional
    public AuthorIntentResponse confirm(
            Long sectionId,
            Long userId,
            int requestedVersion,
            String requestedIntent
    ) {
        if (!AuthorIntentTextPolicy.isValid(requestedIntent)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    List.of(new FieldError(
                            "intent", "단일 행의 비목록 문장으로 300자 이하로 입력해 주세요.")));
        }
        String normalized = AuthorIntentTextPolicy.normalize(requestedIntent);
        ProjectSection section = sectionAccessGuard.requireOwnedSectionForUpdate(sectionId, userId);
        SectionDraft latestDraft = draftRepository
                .findTopByProjectSection_IdOrderByVersionDesc(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_DRAFT_NOT_FOUND));
        if (latestDraft.getVersion() != requestedVersion) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        SectionAuthorIntent intent = intentRepository
                .findByProjectSection_IdAndContentVersion(sectionId, requestedVersion)
                .orElse(null);
        if (intent != null && intent.isConfirmedAs(normalized)) {
            return queryService.responseWithLatest(sectionId, requestedVersion, intent);
        }

        User confirmer = userService.getUserReference(userId);
        if (intent == null) {
            intent = SectionAuthorIntent.confirmed(
                    section,
                    requestedVersion,
                    normalized,
                    confirmer,
                    LocalDateTime.ofInstant(clock.instant(), KST));
        } else {
            intent.confirm(
                    normalized,
                    confirmer,
                    LocalDateTime.ofInstant(clock.instant(), KST));
        }
        try {
            intentRepository.saveAndFlush(intent);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        // 비교 기준이 달라졌으므로 같은 본문이어도 기존 ACTIVE 링크를 유지하지 않는다.
        reviewLinkService.markSectionLinksOutdated(sectionId);
        return queryService.responseWithLatest(sectionId, requestedVersion, intent);
    }
}
