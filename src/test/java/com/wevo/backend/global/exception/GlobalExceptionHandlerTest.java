package com.wevo.backend.global.exception;

import com.wevo.backend.global.response.FieldError;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void dataIntegrityViolationReturnsConflictResponse() throws Exception {
        mockMvc.perform(get("/test/data-integrity-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.message").value("요청이 현재 상태와 충돌합니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
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

        @GetMapping("/test/unexpected-exception")
        void throwUnexpectedException() {
            throw new IllegalStateException("sensitive-internal-detail");
        }
    }
}
