package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.issue.service.CurrentSynthesisSetReference;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiJobCurrentSynthesisSetResolverTest {

    private static final Long SECTION_ID = 10L;

    @Mock private AiJobRepository aiJobRepository;
    @Mock private AiJob succeeded;

    private AiJobCurrentSynthesisSetResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AiJobCurrentSynthesisSetResolver(aiJobRepository);
    }

    @Test
    @DisplayName("최신 성공 작업의 requestId와 resultId를 현재 세트 참조로 반환한다")
    void findCurrent_returnsLatestSucceededResult() {
        UUID requestId = UUID.randomUUID();
        givenLatestSucceeded(succeeded);
        given(succeeded.getRequestId()).willReturn(requestId);
        given(succeeded.getResultId()).willReturn(55L);

        Optional<CurrentSynthesisSetReference> result = resolver.findCurrent(SECTION_ID);

        assertThat(result).contains(new CurrentSynthesisSetReference(requestId, 55L));
    }

    @Test
    @DisplayName("성공 이력이 없으면 현재 세트도 없다")
    void findCurrent_withoutSuccess_returnsEmpty() {
        givenLatestSucceeded(null);

        assertThat(resolver.findCurrent(SECTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("성공 작업에 resultId가 없으면 무결성 오류로 드러낸다")
    void findCurrent_missingResultId_fails() {
        UUID requestId = UUID.randomUUID();
        givenLatestSucceeded(succeeded);
        given(succeeded.getRequestId()).willReturn(requestId);
        given(succeeded.getResultId()).willReturn(null);

        assertThatThrownBy(() -> resolver.findCurrent(SECTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(requestId.toString());
    }

    private void givenLatestSucceeded(AiJob job) {
        given(aiJobRepository
                .findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                        SECTION_ID, AiFeature.OPINION_SYNTHESIS, AiJobStatus.SUCCEEDED))
                .willReturn(Optional.ofNullable(job));
    }
}
