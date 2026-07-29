package com.wevo.backend.ai.service;

import com.wevo.backend.ai.repository.AiUsageLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Provider 성공 시점에는 없던 최종 도메인 결과 ID를 같은 작업의 usage log에 연결한다. */
@Service
public class AiUsageResultLinkService {

    private final AiUsageLogRepository repository;

    public AiUsageResultLinkService(AiUsageLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int linkSuccessfulInvocations(Long aiJobId, Long resultId) {
        if (aiJobId == null || resultId == null || resultId <= 0) {
            throw new IllegalArgumentException("AI 작업 ID와 결과 ID는 필수입니다.");
        }
        return repository.linkSuccessfulInvocationsToResult(aiJobId, resultId);
    }
}
