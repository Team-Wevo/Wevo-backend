package com.wevo.backend.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "wevo.ai.jobs.dispatch-enabled=false")
@AutoConfigureMockMvc
class AiOpenApiWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void documentsAsyncResponsesPollingAndIssueEndpoints() throws Exception {
        String content = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode paths = objectMapper.readTree(content).path("paths");

        assertResponse(paths,
                "/api/project-sections/{sectionId}/synthesis", "post", "202");
        assertResponse(paths,
                "/api/project-sections/{sectionId}/synthesis", "post", "503");
        assertResponse(paths,
                "/api/project-sections/{sectionId}/draft/generate", "post", "202");
        assertResponse(paths,
                "/api/project-sections/{sectionId}/draft/generate", "post", "503");
        assertResponse(paths,
                "/api/project-sections/{sectionId}/precheck", "post", "202");
        assertResponse(paths,
                "/api/project-sections/{sectionId}/precheck", "post", "503");
        assertResponse(paths,
                "/api/project-sections/{sectionId}/author-intent/extractions", "post", "503");
        assertResponse(paths,
                "/api/project-sections/{sectionId}/opinion-clusters", "post", "503");
        assertResponse(paths,
                "/api/projects/{projectId}/flow-check", "post", "503");
        assertResponse(paths, "/api/ai-jobs/{requestId}", "get", "200");
        assertResponse(paths, "/api/ai-jobs/{requestId}", "get", "404");
        assertResponse(paths, "/api/issues/{issueId}/decision", "post", "200");
    }

    @Test
    void documentsNestedResponseRecordsWithDistinctSchemaNames() throws Exception {
        String content = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode schemas = objectMapper.readTree(content).path("components").path("schemas");

        assertSchemaReference(
                schemas,
                "SynthesisResponse",
                "currentSet",
                "SynthesisCurrentSetResponse"
        );
        assertSchemaProperties(
                schemas,
                "SynthesisCurrentSetResponse",
                "setId",
                "consensusSummary",
                "issues",
                "inheritedGapAnswers"
        );
        assertRequiredProperties(
                schemas,
                "SynthesisCurrentSetResponse",
                "setId",
                "consensusSummary",
                "issues",
                "inheritedGapAnswers"
        );

        assertSchemaReference(
                schemas,
                "OpinionClusteringResponse",
                "currentSet",
                "OpinionClusteringCurrentSetResponse"
        );
        assertSchemaProperties(
                schemas,
                "OpinionClusteringCurrentSetResponse",
                "setId",
                "sourceGateGeneration",
                "totalOpinionCount",
                "coveredCount",
                "createdAt",
                "clusters"
        );
        assertRequiredProperties(
                schemas,
                "OpinionClusteringCurrentSetResponse",
                "setId",
                "sourceGateGeneration",
                "totalOpinionCount",
                "coveredCount",
                "createdAt",
                "clusters"
        );

        assertArrayItemReference(
                schemas,
                "CurrentResultResponse",
                "findings",
                "PrecheckFindingResponse"
        );
        assertSchemaProperties(
                schemas,
                "PrecheckFindingResponse",
                "type",
                "targetExcerpt",
                "comment",
                "suggestion"
        );
        assertRequiredProperties(
                schemas,
                "PrecheckFindingResponse",
                "type",
                "targetExcerpt",
                "comment",
                "suggestion"
        );

        assertArrayItemReference(
                schemas,
                "ResultResponse",
                "findings",
                "ProjectFlowFindingResponse"
        );
        assertSchemaProperties(
                schemas,
                "ProjectFlowFindingResponse",
                "order",
                "type",
                "sections",
                "description",
                "suggestion"
        );
        assertRequiredProperties(
                schemas,
                "ProjectFlowFindingResponse",
                "order",
                "type",
                "sections",
                "description",
                "suggestion"
        );
    }

    @Test
    void projectSectionEndpointsUseSectionIdAsTheOpenApiPathParameter() throws Exception {
        String content = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode paths = objectMapper.readTree(content).path("paths");

        assertPathParameter(paths,
                "/api/project-sections/{sectionId}/opinion-clusters", "get", "sectionId");
        assertPathParameter(paths,
                "/api/project-sections/{sectionId}/my-opinion/draft", "patch", "sectionId");
        assertThat(paths.has("/api/project-sections/{projectSectionId}/my-opinion/draft"))
                .isFalse();
    }

    private void assertResponse(
            JsonNode paths,
            String path,
            String method,
            String responseCode
    ) {
        assertThat(paths.path(path).path(method).path("responses").has(responseCode))
                .as("%s %s 응답 %s가 OpenAPI에 있어야 한다", method, path, responseCode)
                .isTrue();
    }

    private void assertPathParameter(
            JsonNode paths,
            String path,
            String method,
            String parameterName
    ) {
        assertThat(paths.path(path).path(method).path("parameters").values().stream()
                .filter(parameter -> "path".equals(parameter.path("in").stringValue()))
                .map(parameter -> parameter.path("name").stringValue())
                .toList())
                .containsExactly(parameterName);
    }

    private void assertSchemaReference(
            JsonNode schemas,
            String schemaName,
            String propertyName,
            String referencedSchemaName
    ) {
        assertThat(schemas.path(schemaName)
                .path("properties")
                .path(propertyName)
                .path("$ref")
                .stringValue())
                .isEqualTo("#/components/schemas/" + referencedSchemaName);
    }

    private void assertArrayItemReference(
            JsonNode schemas,
            String schemaName,
            String propertyName,
            String referencedSchemaName
    ) {
        assertThat(schemas.path(schemaName)
                .path("properties")
                .path(propertyName)
                .path("items")
                .path("$ref")
                .stringValue())
                .isEqualTo("#/components/schemas/" + referencedSchemaName);
    }

    private void assertSchemaProperties(
            JsonNode schemas,
            String schemaName,
            String... propertyNames
    ) {
        JsonNode properties = schemas.path(schemaName).path("properties");
        assertThat(properties.propertyNames())
                .as("%s 스키마의 필드 구성이 정확해야 한다", schemaName)
                .containsExactlyInAnyOrder(propertyNames);
    }

    private void assertRequiredProperties(
            JsonNode schemas,
            String schemaName,
            String... propertyNames
    ) {
        assertThat(schemas.path(schemaName).path("required").values().stream()
                .map(JsonNode::stringValue)
                .toList())
                .as("%s 스키마의 필수 필드 구성이 정확해야 한다", schemaName)
                .containsExactlyInAnyOrder(propertyNames);
    }
}
