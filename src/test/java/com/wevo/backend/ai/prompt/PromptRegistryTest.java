package com.wevo.backend.ai.prompt;

import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPromptByNameAndVersion() {
        PromptRegistry registry = new PromptRegistry(new PromptResourceLoader());

        PromptDefinition definition = registry.get(new PromptTemplateId("contract-summary", 1));

        assertThat(definition.id().trackingValue()).isEqualTo("contract-summary:v1");
        assertThat(definition.requiredVariables()).containsExactly("sourceText");
        assertThat(definition.systemTemplate()).contains("contract-test assistant");
        assertThat(definition.userTemplate()).contains("{{sourceText}}");
    }

    @Test
    void rejectsUnknownPromptVersion() {
        PromptRegistry registry = new PromptRegistry(new PromptResourceLoader());

        assertThatThrownBy(() -> registry.get(new PromptTemplateId("contract-summary", 2)))
                .isInstanceOf(PromptException.class)
                .extracting(exception -> ((PromptException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_PROMPT_NOT_FOUND);
    }

    @Test
    void rejectsMissingPromptPair() throws Exception {
        writePrompt(tempDir, "missing-pair", "v1", "system.md", "system");

        assertInvalidResources(List.of(tempDir));
    }

    @Test
    void rejectsEmptyTemplate() throws Exception {
        writePrompt(tempDir, "empty-template", "v1", "system.md", "system");
        writePrompt(tempDir, "empty-template", "v1", "user.md", "   ");

        assertInvalidResources(List.of(tempDir));
    }

    @Test
    void rejectsInvalidVersionAndPlaceholderDelimiter() throws Exception {
        writePrompt(tempDir, "invalid-version", "v0", "system.md", "system");
        writePrompt(tempDir, "invalid-version", "v0", "user.md", "user");

        assertInvalidResources(List.of(tempDir));

        Path secondRoot = Files.createDirectory(tempDir.resolve("second"));
        writePrompt(secondRoot, "invalid-delimiter", "v1", "system.md", "system");
        writePrompt(secondRoot, "invalid-delimiter", "v1", "user.md", "Hello {{sourceText}}");

        assertInvalidResources(List.of(secondRoot));
    }

    @Test
    void rejectsDuplicatePromptIdAcrossClasspathRoots() throws Exception {
        Path firstRoot = Files.createDirectory(tempDir.resolve("first"));
        Path secondRoot = Files.createDirectory(tempDir.resolve("second"));
        writePair(firstRoot, "duplicate", "v1");
        writePair(secondRoot, "duplicate", "v1");

        assertInvalidResources(List.of(firstRoot, secondRoot));
    }

    private void writePair(Path root, String name, String version) throws Exception {
        writePrompt(root, name, version, "system.md", "system");
        writePrompt(root, name, version, "user.md", "user");
    }

    private void writePrompt(Path root, String name, String version, String fileName, String content) throws Exception {
        Path directory = root.resolve("prompts/ai").resolve(name).resolve(version);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(fileName), content);
    }

    private void assertInvalidResources(List<Path> roots) throws Exception {
        URLClassLoader classLoader = new URLClassLoader(
                roots.stream().map(this::toUrl).toArray(java.net.URL[]::new),
                null
        );
        try (classLoader) {
            PromptResourceLoader loader = new PromptResourceLoader(
                    new PathMatchingResourcePatternResolver(classLoader)
            );
            assertThatThrownBy(loader::loadAll)
                    .isInstanceOf(PromptException.class)
                    .extracting(exception -> ((PromptException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.AI_PROMPT_INVALID);
        }
    }

    private java.net.URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (java.net.MalformedURLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
