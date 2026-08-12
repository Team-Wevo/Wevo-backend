package com.wevo.backend.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "wevo.ai.guardrails.recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AiGuardrailRecoveryScheduler {

    private final AiGuardrailLifecycleService lifecycleService;

    public AiGuardrailRecoveryScheduler(AiGuardrailLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(
            fixedDelayString = "${wevo.ai.guardrails.recovery.interval:60s}",
            initialDelayString = "${wevo.ai.guardrails.recovery.interval:60s}"
    )
    public void recover() {
        lifecycleService.recoverOrphanReservations();
        lifecycleService.recoverUnsettledJobs();
    }
}
