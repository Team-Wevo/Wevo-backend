package com.wevo.backend.ai.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** 비동기 AI 실행 endpoint에 공통 quota·비용 실패 계약을 노출한다. */
@Configuration(proxyBeanMethods = false)
public class AiGuardrailOpenApiConfig {

    private static final Set<String> EXECUTION_SUFFIXES = Set.of(
            "/synthesis",
            "/draft/generate",
            "/precheck",
            "/author-intent/extractions",
            "/opinion-clusters",
            "/flow-check"
    );

    @Bean
    public OpenApiCustomizer aiGuardrailOpenApiCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            Operation operation = item.getPost();
            if (operation == null || EXECUTION_SUFFIXES.stream().noneMatch(path::endsWith)) {
                return;
            }
            if (operation.getResponses() == null) {
                operation.setResponses(new ApiResponses());
            }
            operation.getResponses().addApiResponse(
                    "429",
                    new ApiResponse().description(
                            "AI025 요청 quota 초과 / AI026 프로젝트 quota 초과 / AI027 비용 예산 초과; Retry-After 포함"));
            operation.getResponses().addApiResponse(
                    "503",
                    new ApiResponse().description(
                            "AI028 Redis guardrail 확인 불가 / AI029 pricing snapshot 없음"));
        });
    }
}
