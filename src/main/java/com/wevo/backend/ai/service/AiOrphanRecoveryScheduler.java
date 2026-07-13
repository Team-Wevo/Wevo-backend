package com.wevo.backend.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "wevo.ai.audit",
        name = "recovery-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AiOrphanRecoveryScheduler {

    private final AiOrphanRecoveryService recoveryService;

    public AiOrphanRecoveryScheduler(AiOrphanRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Scheduled(
            fixedDelayString = "${wevo.ai.audit.recovery-interval:60s}",
            initialDelayString = "${wevo.ai.audit.recovery-interval:60s}"
    )
    public void recover() {
        recoveryService.recoverOrphans();
    }
}
