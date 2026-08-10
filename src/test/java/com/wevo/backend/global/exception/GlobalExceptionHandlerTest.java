package com.wevo.backend.global.exception;

import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.ai.exception.AiGuardrailExceededException;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionReturnsErrorResponse() throws Exception {
        mockMvc.perform(get("/test/business-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("P001"))
                .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void quotaExceededReturnsProductCodeAndSafeRetryAfterHeader() throws Exception {
        mockMvc.perform(get("/test/quota-exceeded"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("AI025"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void businessExceptionWithFieldErrorsReturnsErrorDetails() throws Exception {
        mockMvc.perform(get("/test/business-exception-with-field-errors"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].reason").value("프로젝트 이름은 필수입니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void uniqueViolationReturnsConflictResponse() throws Exception {
        // 같은 값이 이미 있다 = 저장된 상태와의 충돌(§5.6). 동시 요청에서 정상적으로 발생한다.
        mockMvc.perform(get("/test/unique-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.message").value("요청이 현재 상태와 충돌합니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void nonUniqueIntegrityViolationReturnsInternalServerError() throws Exception {
        // FK·NOT NULL·CHECK 위반은 서버가 보낼 수 없는 데이터를 보낸 것이라 409 로 뭉개면
        // 사용자는 재시도만 하고 서버 버그는 드러나지 않는다.
        mockMvc.perform(get("/test/foreign-key-violation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("C999"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    @Test
    void integrityViolationWithoutSqlStateReturnsInternalServerError() throws Exception {
        // 원인 체인에 SQLException 이 없으면 유니크 위반이라고 단정할 수 없다.
        mockMvc.perform(get("/test/data-integrity-violation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("C999"));
    }

    @Test
    void unknownPathReturnsCommonErrorBody() throws Exception {
        // 본문 없이 상태만 내려가면 code 로 분기하는 클라이언트 공통 처리가 이 응답만 파싱하지 못한다.
        mockMvc.perform(get("/test/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C004"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void wrongHttpMethodReturnsCommonErrorBodyAndKeepsAllowHeader() throws Exception {
        // 본문을 채우면서 Spring 이 실어 둔 헤더를 버리면 안 된다 — 405 의 Allow 는 표준상 필수다.
        mockMvc.perform(post("/test/business-exception"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C005"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unsupportedContentTypeReturnsCommonErrorBody() throws Exception {
        mockMvc.perform(post("/test/json-only")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C006"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void pathVariableTypeMismatchReturnsInvalidInput() throws Exception {
        mockMvc.perform(get("/test/path/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("projectId"))
                .andExpect(jsonPath("$.errors[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void queryParameterTypeMismatchReturnsInvalidInput() throws Exception {
        mockMvc.perform(get("/test/query").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void missingRequiredParameterReturnsInvalidInput() throws Exception {
        mockMvc.perform(get("/test/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("keyword"))
                .andExpect(jsonPath("$.errors[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void methodParameterValidationFailureReturnsInvalidInput() throws Exception {
        mockMvc.perform(get("/test/validated").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedExceptionReturnsSafeInternalServerError() throws Exception {
        mockMvc.perform(get("/test/unexpected-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C999"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
                .andExpect(jsonPath("$.message").value(not(containsString("sensitive-internal-detail"))))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business-exception")
        void throwBusinessException() {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        @GetMapping("/test/quota-exceeded")
        void throwQuotaExceeded() {
            throw new AiGuardrailExceededException(ErrorCode.AI_REQUEST_QUOTA_EXCEEDED, 30);
        }

        @GetMapping("/test/business-exception-with-field-errors")
        void throwBusinessExceptionWithFieldErrors() {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    List.of(new FieldError("name", "프로젝트 이름은 필수입니다."))
            );
        }

        @GetMapping("/test/data-integrity-violation")
        void throwDataIntegrityViolation() {
            throw new DataIntegrityViolationException("duplicate key");
        }

        @GetMapping("/test/unique-violation")
        void throwUniqueViolation() {
            throw new DataIntegrityViolationException(
                    "could not execute statement",
                    new SQLException("duplicate key value violates unique constraint", "23505"));
        }

        @GetMapping("/test/foreign-key-violation")
        void throwForeignKeyViolation() {
            throw new DataIntegrityViolationException(
                    "could not execute statement",
                    new SQLException("insert or update violates foreign key constraint", "23503"));
        }

        @GetMapping("/test/path/{projectId}")
        void pathVariable(@PathVariable Long projectId) {
        }

        @GetMapping("/test/query")
        void queryParameter(@RequestParam Integer page) {
        }

        @GetMapping("/test/required")
        void requiredParameter(@RequestParam String keyword) {
        }

        @GetMapping("/test/validated")
        void validatedParameter(@RequestParam @Min(1) int page) {
        }

        @PostMapping(value = "/test/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        void jsonOnly() {
        }

        @GetMapping("/test/unexpected-exception")
        void throwUnexpectedException() {
            throw new IllegalStateException("sensitive-internal-detail");
        }
    }
}
