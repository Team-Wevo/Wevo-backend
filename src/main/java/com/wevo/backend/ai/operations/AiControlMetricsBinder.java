package com.wevo.backend.ai.operations;

import com.wevo.backend.ai.domain.AiFeature;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** kill switch와 circuit 상태를 외부 호출 없이 gauge로 노출한다. */
@Component
public class AiControlMetricsBinder implements MeterBinder {

    private final AiExecutionControl control;

    public AiControlMetricsBinder(AiExecutionControl control) {
        this.control = control;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("wevo.ai.circuit.state", control,
                        current -> current.state().ordinal())
                .description("0=CLOSED, 1=OPEN, 2=HALF_OPEN")
                .register(registry);
        for (AiFeature feature : AiFeature.values()) {
            Gauge.builder("wevo.ai.execution.enabled", control,
                            current -> current.enabledFor(feature) ? 1.0d : 0.0d)
                    .tag("feature", feature.configKey())
                    .register(registry);
        }
    }
}
