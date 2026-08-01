package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.dto.response.AuthorIntentResponse;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.service.ReviewLinkService;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.SectionAuthorIntentRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthorIntentConfirmationServiceTest {

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private SectionDraftRepository draftRepository;
    @Mock private SectionAuthorIntentRepository intentRepository;
    @Mock private UserService userService;
    @Mock private ReviewLinkService reviewLinkService;
    @Mock private AuthorIntentQueryService queryService;
    @Mock private ProjectSection section;
    @Mock private SectionDraft draft;
    @Mock private User user;

    private AuthorIntentConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new AuthorIntentConfirmationService(
                sectionAccessGuard,
                draftRepository,
                intentRepository,
                userService,
                reviewLinkService,
                queryService,
                Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), ZoneOffset.UTC));
        given(sectionAccessGuard.requireOwnedSectionForUpdate(10L, 7L)).willReturn(section);
        given(draftRepository.findTopByProjectSection_IdOrderByVersionDesc(10L))
                .willReturn(Optional.of(draft));
        given(draft.getVersion()).willReturn(3);
    }

    @Test
    void directHumanConfirmationWorksWithoutAiSuggestionAndInvalidatesActiveLinks() {
        given(intentRepository.findByProjectSection_IdAndContentVersion(10L, 3))
                .willReturn(Optional.empty());
        given(userService.getUserReference(7L)).willReturn(user);
        given(queryService.responseWithLatest(
                eq(10L), eq(3), any(SectionAuthorIntent.class)))
                .willReturn(new AuthorIntentResponse(3, null, null, "최종 의도다.", 7L, null, null));

        AuthorIntentResponse response = service.confirm(10L, 7L, 3, "  최종   의도다.  ");

        assertThat(response.confirmedIntent()).isEqualTo("최종 의도다.");
        verify(intentRepository).saveAndFlush(any(SectionAuthorIntent.class));
        verify(reviewLinkService).markSectionLinksOutdated(10L);
    }

    @Test
    void sameVersionAndSameSentenceIsIdempotentWithoutInvalidatingLinkAgain() {
        SectionAuthorIntent intent = org.mockito.Mockito.mock(SectionAuthorIntent.class);
        given(intentRepository.findByProjectSection_IdAndContentVersion(10L, 3))
                .willReturn(Optional.of(intent));
        given(intent.isConfirmedAs("같은 의도다.")).willReturn(true);

        service.confirm(10L, 7L, 3, "같은 의도다.");

        verify(intentRepository, never()).saveAndFlush(any());
        verify(reviewLinkService, never()).markSectionLinksOutdated(any());
    }

    @Test
    void staleVersionAndInvalidMultilineAreRejected() {
        assertThatThrownBy(() -> service.confirm(10L, 7L, 3, "첫 줄\n둘째 줄"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        assertThatThrownBy(() -> service.confirm(10L, 7L, 2, "유효한 의도다."))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }
}
