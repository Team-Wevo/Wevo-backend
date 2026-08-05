package com.wevo.backend.ai.operations;

import com.wevo.backend.ai.domain.AiFeature;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/** 외부 호출 없이 kill switch/circuit 상태만 노출하는 health component. */
@Component("aiControl")
public class AiControlHealthIndicator implements HealthIndicator {

    private final AiExecutionControl control;

    public AiControlHealthIndicator(AiExecutionControl control) {
        this.control = control;
    }

    @Override
    public Health health() {
        String disabled = Arrays.stream(AiFeature.values())
                .filter(feature -> !control.enabledFor(feature))
                .map(AiFeature::configKey)
                .collect(Collectors.joining(","));
        return Health.up()
                .withDetail("circuit", control.state().name())
                .withDetail("disabledFeatures", disabled)
                .build();
    }
}
