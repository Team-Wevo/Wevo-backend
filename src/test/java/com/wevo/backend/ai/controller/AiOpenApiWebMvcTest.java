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
                "/api/project-sections/{sectionId}/draft/generate", "post", "202");
        assertResponse(paths,
                "/api/project-sections/{sectionId}/precheck", "post", "202");
        assertResponse(paths, "/api/ai-jobs/{requestId}", "get", "200");
        assertResponse(paths, "/api/ai-jobs/{requestId}", "get", "404");
        assertResponse(paths, "/api/issues/{issueId}/decision", "post", "200");
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
}
