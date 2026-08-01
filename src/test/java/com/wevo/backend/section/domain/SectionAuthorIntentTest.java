package com.wevo.backend.section.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.user.domain.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SectionAuthorIntentTest {

    @Test
    void aiSuggestionIsNotConfirmedAutomatically() {
        SectionAuthorIntent intent = SectionAuthorIntent.suggested(
                Mockito.mock(ProjectSection.class),
                2,
                "  핵심   의도다. ",
                Mockito.mock(AiJob.class));

        assertThat(intent.getStatus()).isEqualTo(SectionAuthorIntentStatus.SUGGESTED);
        assertThat(intent.getAiSuggestedIntent()).isEqualTo("핵심 의도다.");
        assertThat(intent.getConfirmedIntent()).isNull();
        assertThat(intent.getConfirmedBy()).isNull();
        assertThat(intent.getConfirmedAt()).isNull();
    }

    @Test
    void laterAiSuggestionDoesNotOverwriteHumanConfirmation() {
        User confirmer = Mockito.mock(User.class);
        SectionAuthorIntent intent = SectionAuthorIntent.confirmed(
                Mockito.mock(ProjectSection.class),
                2,
                "사람이 확정한 의도다.",
                confirmer,
                LocalDateTime.of(2026, 7, 31, 21, 0));

        intent.updateSuggestion("나중에 생성된 AI 제안이다.", Mockito.mock(AiJob.class));

        assertThat(intent.getStatus()).isEqualTo(SectionAuthorIntentStatus.CONFIRMED);
        assertThat(intent.getAiSuggestedIntent()).isEqualTo("나중에 생성된 AI 제안이다.");
        assertThat(intent.getConfirmedIntent()).isEqualTo("사람이 확정한 의도다.");
        assertThat(intent.getConfirmedBy()).isSameAs(confirmer);
    }
}
