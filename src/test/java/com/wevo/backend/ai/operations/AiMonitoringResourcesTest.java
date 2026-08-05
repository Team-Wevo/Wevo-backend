package com.wevo.backend.ai.operations;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiMonitoringResourcesTest {

    private static final Path DASHBOARD = Path.of("ops/monitoring/ai-dashboard.json");
    private static final Path ALERTS = Path.of("ops/monitoring/ai-alert-rules.yml");
    private static final Path RUNBOOK = Path.of("ops/monitoring/README.md");

    @Test
    void dashboardIsValidJsonAndCoversRequiredSignals() throws Exception {
        JsonNode dashboard = new ObjectMapper().readTree(Files.readString(DASHBOARD));
        String source = dashboard.toString();

        assertThat(dashboard.get("panels").size()).isGreaterThanOrEqualTo(6);
        assertThat(source).contains(
                "wevo_ai_requests_total",
                "wevo_ai_schema_results_total",
                "wevo_ai_provider_latency_seconds_bucket",
                "wevo_ai_end_to_end_latency_seconds_bucket",
                "wevo_ai_tokens_sum",
                "wevo_ai_cost_usd_sum",
                "wevo_ai_jobs",
                "wevo_ai_circuit_state",
                "wevo_ai_guardrail_rejections_total");
        assertThat(source).doesNotContain("project_id", "section_id", "user_id", "request_id");
    }

    @Test
    void alertRulesCoverFailureQualityCostQuotaAndMinimumSampleGuards() throws Exception {
        String source = Files.readString(ALERTS);

        assertThat(source).contains(
                "WevoAiProviderAuthenticationError",
                "WevoAiRateLimitSpike",
                "WevoAiProviderFailureRateHigh",
                "WevoAiSchemaValidRateLow",
                "WevoAiCostSpike",
                "WevoAiGuardrailUnavailable",
                "WevoAiQueueBacklog",
                "WevoAiCircuitOpen",
                ">= 20",
                ">= 30");
        assertThat(source).doesNotContain("project_id", "section_id", "user_id", "request_id");
    }

    @Test
    void promptAndCompletionObservationLoggingRemainDisabledByDefault() throws Exception {
        String source = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(source).contains("log-prompt: false", "log-completion: false");
    }

    @Test
    void runbookDefinesOwnerChannelRetentionAndRollback() throws Exception {
        String source = Files.readString(RUNBOOK);

        assertThat(source).contains(
                "기본 소유자",
                "#wevo-ai-alerts",
                "보존 기간",
                "승인된 rollout과 rollback",
                "AI_OPERATIONS_ENABLED=false");
    }
}
