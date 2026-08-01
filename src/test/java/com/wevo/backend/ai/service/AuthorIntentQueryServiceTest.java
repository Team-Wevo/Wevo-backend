package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.dto.response.AuthorIntentResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.SectionAuthorIntentRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorIntentQueryServiceTest {

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private SectionDraftRepository draftRepository;
    @Mock private SectionAuthorIntentRepository intentRepository;
    @Mock private AiJobRepository jobRepository;
    @Mock private SectionDraft draft;
    @Mock private SectionAuthorIntent intent;
    @Mock private AiJob job;

    private AuthorIntentQueryService service;

    @BeforeEach
    void setUp() {
        service = new AuthorIntentQueryService(
                sectionAccessGuard,
                draftRepository,
                intentRepository,
                jobRepository,
                new AiJobStatusMapper());
    }

    @Test
    void returnsOnlyIntentAndLatestJobForCurrentDraftVersion() {
        given(draftRepository.findTopByProjectSection_IdOrderByVersionDesc(10L))
                .willReturn(Optional.of(draft));
        given(draft.getVersion()).willReturn(4);
        given(intentRepository.findByProjectSection_IdAndContentVersion(10L, 4))
                .willReturn(Optional.of(intent));
        given(jobRepository
                .findTopByProjectSection_IdAndFeatureAndSourceVersionOrderByQueuedAtDescIdDesc(
                        10L, AiFeature.AUTHOR_INTENT_EXTRACTION, "draft-v4"))
                .willReturn(Optional.of(job));
        given(job.getStatus()).willReturn(AiJobStatus.QUEUED);

        AuthorIntentResponse response = service.getCurrent(10L, 7L);

        assertThat(response.contentVersion()).isEqualTo(4);
        assertThat(response.latestJob()).isNotNull();
        verify(sectionAccessGuard).requireOwnedSection(10L, 7L);
        verify(intentRepository).findByProjectSection_IdAndContentVersion(10L, 4);
        verify(jobRepository)
                .findTopByProjectSection_IdAndFeatureAndSourceVersionOrderByQueuedAtDescIdDesc(
                        10L, AiFeature.AUTHOR_INTENT_EXTRACTION, "draft-v4");
    }
}
