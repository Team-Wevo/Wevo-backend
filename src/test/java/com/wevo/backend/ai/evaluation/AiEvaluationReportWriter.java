package com.wevo.backend.ai.evaluation;

import org.springframework.ai.util.JacksonUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AiEvaluationReportWriter {

    private final JsonMapper jsonMapper = JacksonUtils.getDefaultJsonMapper();

    public String toJson(AiEvaluationReport report) {
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("evaluation JSON report를 생성할 수 없습니다.", exception);
        }
    }

    public String toMarkdown(AiEvaluationReport report) {
        AiEvaluationMetrics metrics = report.metrics();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# AI Evaluation Summary\n\n")
                .append("- Dataset: `").append(report.run().datasetVersion()).append("`\n")
                .append("- Provider / model: `").append(report.run().providerId()).append("` / `")
                .append(report.run().modelId()).append("`\n")
                .append("- Endpoint / reasoning effort: `").append(report.run().endpointType())
                .append("` / `").append(report.run().reasoningEffort()).append("`\n")
                .append("- Prompt / schema: `").append(report.run().promptVersion()).append("` / `")
                .append(report.run().schemaVersion()).append("`\n")
                .append("- Options: temperature=").append(report.run().temperature())
                .append(", maxOutputTokens=").append(report.run().maxOutputTokens()).append("\n")
                .append("- Executed at: `").append(report.run().executedAt()).append("`\n")
                .append("- Git commit: `").append(report.run().gitCommit()).append("`\n\n")
                .append("## Metrics\n\n")
                .append("| Metric | Value |\n")
                .append("| --- | ---: |\n")
                .append("| Schema valid rate | ").append(rate(metrics.schemaValidRate())).append(" |\n")
                .append("| Total attempts | ").append(metrics.totalAttemptCount()).append(" |\n")
                .append("| Evidence coverage | ").append(rate(metrics.evidenceCoverage())).append(" |\n")
                .append("| Issue precision / recall | ").append(rate(metrics.issuePrecision()))
                .append(" / ").append(rate(metrics.issueRecall())).append(" |\n")
                .append("| Unknown evidence IDs | ").append(count(metrics.unknownEvidenceIdCount())).append(" |\n")
                .append("| Correction exhausted | ").append(count(metrics.correctionExhaustedCount())).append(" |\n")
                .append("| Unverified facts | ").append(count(metrics.unverifiedFactCount())).append(" |\n")
                .append("| Input / output tokens | ").append(value(metrics.inputTokens().total()))
                .append(" / ").append(value(metrics.outputTokens().total())).append(" |\n")
                .append("| Input tokens p50 / p95 | ")
                .append(value(metrics.inputTokens().p50())).append(" / ")
                .append(value(metrics.inputTokens().p95())).append(" |\n")
                .append("| Output tokens p50 / p95 | ")
                .append(value(metrics.outputTokens().p50())).append(" / ")
                .append(value(metrics.outputTokens().p95())).append(" |\n")
                .append("| Cache read / write tokens | ")
                .append(value(metrics.cacheReadTokens().total())).append(" / ")
                .append(value(metrics.cacheWriteTokens().total())).append(" |\n")
                .append("| Reasoning tokens | ").append(value(metrics.reasoningTokens().total()))
                .append(" |\n")
                .append("| Estimated cost | ").append(value(metrics.cost().estimatedCost()))
                .append(" (`").append(metrics.cost().status()).append("`) |\n")
                .append("| Successful cost p50 / p95 | ")
                .append(value(metrics.cost().successfulCostP50())).append(" / ")
                .append(value(metrics.cost().successfulCostP95())).append(" |\n")
                .append("| Latency p50 / p95 | ").append(value(metrics.latencyP50Millis()))
                .append(" ms / ").append(value(metrics.latencyP95Millis())).append(" ms |\n\n")
                .append("## Gate\n\n")
                .append("- Automatic: `").append(report.gate().automaticStatus()).append("`\n")
                .append("- Overall: `").append(report.gate().overallStatus()).append("`\n")
                .append("- Reasons: ").append(report.gate().reasons()).append("\n\n")
                .append("## Human review\n\n")
                .append("- Status: `").append(report.humanReview().status()).append("`\n")
                .append("- Reviewed fixtures: ")
                .append(report.humanReview().fixtureScores().size()).append("\n\n")
                .append("## Baseline deltas\n\n");
        if (report.baselineDeltas().isEmpty()) {
            markdown.append("No baseline supplied.\n\n");
        } else {
            report.baselineDeltas().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> markdown.append("- ").append(entry.getKey()).append(": ")
                            .append(String.format(Locale.ROOT, "%+.4f", entry.getValue())).append("\n"));
            markdown.append("\n");
        }
        markdown.append("## Failed fixtures\n\n");
        if (report.failures().isEmpty()) {
            markdown.append("None.\n");
        } else {
            report.failures().forEach(failure -> markdown.append("- `")
                    .append(failure.fixtureId()).append("`: `")
                    .append(failure.reason()).append("`\n"));
        }
        return markdown.toString();
    }

    public WrittenReport write(AiEvaluationReport report, Path directory, String fileStem) {
        if (fileStem == null || !fileStem.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("report file stem은 kebab-case여야 합니다.");
        }
        try {
            Files.createDirectories(directory);
            Path jsonPath = directory.resolve(fileStem + ".json");
            Path markdownPath = directory.resolve(fileStem + ".md");
            Files.writeString(jsonPath, toJson(report));
            Files.writeString(markdownPath, toMarkdown(report));
            return new WrittenReport(jsonPath, markdownPath);
        } catch (IOException exception) {
            throw new IllegalStateException("evaluation report 파일을 저장할 수 없습니다.", exception);
        }
    }

    private String rate(AiEvaluationMetrics.RateMetric metric) {
        return metric.value() == null
                ? "N/A"
                : String.format(Locale.ROOT, "%.2f%%", metric.value() * 100.0d);
    }

    private String value(Object value) {
        return value == null ? "N/A" : value.toString();
    }

    private String count(AiEvaluationMetrics.CountMetric metric) {
        return metric.value() == null ? "N/A" : metric.value().toString();
    }

    public record WrittenReport(Path jsonPath, Path markdownPath) {
    }
}
