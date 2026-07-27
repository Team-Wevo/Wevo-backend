package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.issue.service.CurrentSynthesisSetReference;
import com.wevo.backend.issue.service.CurrentSynthesisSetResolver;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최신 성공 의견 정리 작업의 {@code resultId}를 현재 정리 세트로 공개한다.
 */
@Service
@Transactional(readOnly = true)
public class AiJobCurrentSynthesisSetResolver implements CurrentSynthesisSetResolver {

    private static final AiFeature FEATURE = AiFeature.OPINION_SYNTHESIS;

    private final AiJobRepository aiJobRepository;

    public AiJobCurrentSynthesisSetResolver(AiJobRepository aiJobRepository) {
        this.aiJobRepository = aiJobRepository;
    }

    @Override
    public Optional<CurrentSynthesisSetReference> findCurrent(Long projectSectionId) {
        return aiJobRepository
                .findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                        projectSectionId, FEATURE, AiJobStatus.SUCCEEDED)
                .map(this::toReference);
    }

    private CurrentSynthesisSetReference toReference(AiJob succeeded) {
        if (succeeded.getResultId() == null) {
            throw new IllegalStateException(
                    "성공한 의견 정리 작업에 resultId가 없습니다: requestId="
                            + succeeded.getRequestId());
        }
        return new CurrentSynthesisSetReference(
                succeeded.getRequestId(), succeeded.getResultId());
    }
}
