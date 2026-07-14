package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiAuditProperties;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.exception.AiAuditPersistenceException;
import com.wevo.backend.ai.repository.AiUsageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AiOrphanRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AiOrphanRecoveryService.class);

    private final AiUsageLogRepository repository;
    private final AiUsageService usageService;
    private final AiProperties aiProperties;
    private final AiAuditProperties auditProperties;
    private final Clock clock;

    public AiOrphanRecoveryService(
            AiUsageLogRepository repository,
            AiUsageService usageService,
            AiProperties aiProperties,
            AiAuditProperties auditProperties,
            Clock clock
    ) {
        this.repository = repository;
        this.usageService = usageService;
        this.aiProperties = aiProperties;
        this.auditProperties = auditProperties;
        this.clock = clock;
    }

    public int recoverOrphans() {
        LocalDateTime now = LocalDateTime.now(clock);
        int recovered = 0;

        for (AiFeature feature : AiFeature.values()) {
            LocalDateTime threshold = now.minus(
                    aiProperties.optionsFor(feature).timeout().plus(auditProperties.orphanGrace())
            );
            for (UUID requestId : repository.findRequestIdsForOrphanRecovery(
                    AiRequestStatus.REQUESTED, feature, threshold
            )) {
                try {
                    usageService.markOrphaned(requestId, now);
                    recovered++;
                } catch (AiAuditPersistenceException | IllegalStateException exception) {
                    log.warn(
                            "Failed or skipped orphan AI request recovery. requestId={}, exceptionType={}",
                            requestId,
                            exception.getClass().getSimpleName()
                    );
                }
            }
        }
        return recovered;
    }
}
