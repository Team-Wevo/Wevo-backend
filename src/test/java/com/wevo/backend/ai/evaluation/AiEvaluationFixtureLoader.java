package com.wevo.backend.ai.evaluation;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AiEvaluationFixtureLoader {

    public static final String DEFAULT_SCHEMA = "ai/evaluation/schema/fixture-v1.schema.json";

    private final JsonMapper jsonMapper;
    private final Schema schema;

    public AiEvaluationFixtureLoader() {
        this(DEFAULT_SCHEMA);
    }

    AiEvaluationFixtureLoader(String schemaResource) {
        this.jsonMapper = JacksonUtils.getDefaultJsonMapper();
        this.schema = compileSchema(schemaResource);
    }

    public AiEvaluationFixture load(String resourcePath) {
        JsonNode node = readTree(resourcePath);
        List<String> validationErrors = schema.validate(node).stream()
                .map(Object::toString)
                .sorted()
                .toList();
        if (!validationErrors.isEmpty()) {
            throw new AiEvaluationFixtureException(
                    "평가 fixture schema 검증에 실패했습니다: " + String.join("; ", validationErrors)
            );
        }

        try {
            AiEvaluationFixture fixture = jsonMapper.treeToValue(node, AiEvaluationFixture.class);
            validateSemantics(fixture);
            return fixture;
        } catch (AiEvaluationFixtureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiEvaluationFixtureException("평가 fixture 타입 변환에 실패했습니다.", exception);
        }
    }

    public List<AiEvaluationFixture> loadDataset(String indexResourcePath) {
        DatasetIndex index;
        try (InputStream input = resource(indexResourcePath)) {
            index = jsonMapper.readValue(input, DatasetIndex.class);
        } catch (IOException | RuntimeException exception) {
            throw new AiEvaluationFixtureException("평가 dataset index를 읽을 수 없습니다.", exception);
        }
        if (index == null || index.fixtures() == null || index.fixtures().isEmpty()) {
            throw new AiEvaluationFixtureException("평가 dataset index에는 fixture가 하나 이상 필요합니다.");
        }
        int separator = indexResourcePath.lastIndexOf('/');
        String basePath = separator < 0 ? "" : indexResourcePath.substring(0, separator + 1);
        List<AiEvaluationFixture> fixtures = index.fixtures().stream()
                .map(path -> load(basePath + path))
                .toList();
        Set<String> ids = new HashSet<>();
        com.wevo.backend.ai.domain.AiFeature datasetFeature = fixtures.getFirst().metadata().feature();
        for (AiEvaluationFixture fixture : fixtures) {
            if (!ids.add(fixture.metadata().id())) {
                throw new AiEvaluationFixtureException("평가 dataset에 중복 fixture ID가 있습니다.");
            }
            if (!fixture.metadata().datasetVersion().equals(index.datasetVersion())) {
                throw new AiEvaluationFixtureException("fixture와 dataset index의 version이 일치하지 않습니다.");
            }
            if (fixture.metadata().feature() != datasetFeature) {
                throw new AiEvaluationFixtureException("하나의 dataset에는 한 AI 기능 fixture만 둘 수 있습니다.");
            }
        }
        return fixtures;
    }

    private Schema compileSchema(String schemaResource) {
        try {
            JsonNode schemaNode = readTree(schemaResource);
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaNode);
        } catch (RuntimeException exception) {
            throw new AiEvaluationFixtureException("평가 fixture JSON Schema가 올바르지 않습니다.", exception);
        }
    }

    private JsonNode readTree(String resourcePath) {
        try (InputStream input = resource(resourcePath)) {
            JsonNode node = jsonMapper.readTree(input);
            if (node == null) {
                throw new AiEvaluationFixtureException("평가 fixture 리소스가 비어 있습니다.");
            }
            return node;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof AiEvaluationFixtureException fixtureException) {
                throw fixtureException;
            }
            throw new AiEvaluationFixtureException("평가 fixture 리소스를 읽을 수 없습니다.", exception);
        }
    }

    private InputStream resource(String resourcePath) throws IOException {
        return new ClassPathResource(resourcePath).getInputStream();
    }

    private void validateSemantics(AiEvaluationFixture fixture) {
        if (fixture.schemaVersion() != 1) {
            throw new AiEvaluationFixtureException("지원하지 않는 fixture schema version입니다.");
        }
        if (!fixture.metadata().id().startsWith(fixture.metadata().feature().configKey() + "/")) {
            throw new AiEvaluationFixtureException("fixture ID prefix와 AI 기능이 일치하지 않습니다.");
        }
        Set<String> opinionIds = new HashSet<>();
        for (AiEvaluationFixture.Opinion opinion : fixture.input().opinions()) {
            if (!opinionIds.add(opinion.id())) {
                throw new AiEvaluationFixtureException("fixture 내부 opinion ID는 중복될 수 없습니다.");
            }
        }
        Set<String> allowedIds = fixture.input().allowedEvidenceIds();
        for (AiEvaluationFixture.ExpectedIssue issue : fixture.expected().expectedIssues()) {
            if (!allowedIds.containsAll(issue.evidenceIds())) {
                throw new AiEvaluationFixtureException("기대 쟁점의 evidence ID는 제출된 입력 allowlist에 있어야 합니다.");
            }
        }
        if (!allowedIds.containsAll(fixture.expected().expectedEvidenceIds())) {
            throw new AiEvaluationFixtureException("기대 evidence ID는 제출된 입력 allowlist에 있어야 합니다.");
        }
    }

    private record DatasetIndex(String datasetVersion, List<String> fixtures) {
    }
}
