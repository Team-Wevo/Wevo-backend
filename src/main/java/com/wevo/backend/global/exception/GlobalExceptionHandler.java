package com.wevo.backend.global.exception;

import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.ai.exception.AiGuardrailExceededException;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.response.FieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.List;
import java.util.Objects;

/**
 * 모든 REST Controller에서 발생하는 예외를 공통 형식으로 처리.
 *
 * Controller마다 중복된 try-catch 문을 작성하지 않도록 예외 처리를 한곳에 모으고,
 * 실패 응답을 ApiResponse 형식으로 통일.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_FORMAT_REASON = "올바른 형식의 값이어야 합니다.";
    private static final String REQUIRED_VALUE_REASON = "필수 값입니다.";

    /**
     * 서비스 계층에서 발생한 BusinessException을 처리.
     *
     * 예외가 가진 ErrorCode에서 HTTP 상태와 오류 정보를 꺼내
     * 공통 실패 응답으로 변환
     *
     * @param exception 처리할 비즈니스 예외
     * @return HTTP 상태와 공통 실패 응답
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception
    ) {
        // 비즈니스 예외에 담긴 HTTP 상태, 오류 코드, 메시지를 가져옴.
        ErrorCode errorCode = exception.getErrorCode();

        // ErrorCode의 HTTP 상태와 ApiResponse의 공통 실패 본문을 함께 반환.
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, exception.getErrors()));
    }

    /**
     * {@code @Valid} 검증 실패(MethodArgumentNotValidException)를 처리.
     *
     * 필드별 검증 실패 사유를 공통 실패 응답의 {@code errors}로 변환한다.
     *
     * @param exception 검증 실패 예외
     * @return {@code C001 INVALID_INPUT} 과 필드 단위 오류 목록
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, errors));
    }

    /**
     * Path variable 또는 Query parameter의 타입 변환 실패를 처리한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return invalidInput(new FieldError(exception.getName(), INVALID_FORMAT_REASON));
    }

    /**
     * 필수 Query parameter 누락을 처리한다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        return invalidInput(new FieldError(exception.getParameterName(), REQUIRED_VALUE_REASON));
    }

    /**
     * Controller 메서드 파라미터의 Bean Validation 실패를 처리한다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception
    ) {
        List<FieldError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(
                                Objects.requireNonNullElse(
                                        result.getMethodParameter().getParameterName(),
                                        "arg" + result.getMethodParameter().getParameterIndex()
                                ),
                                Objects.requireNonNullElse(error.getDefaultMessage(), INVALID_FORMAT_REASON)
                        )))
                .toList();

        return invalidInput(errors);
    }

    /**
     * JSON 본문 파싱 실패를 공통 실패 응답으로 변환한다.
     *
     * <p>{@link AuthProvider} enum 역직렬화 실패는 문서화된 A006으로 내려주고,
     * 그 외 잘못된 JSON/타입 오류는 C001로 반환한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        ErrorCode errorCode = isUnsupportedAuthProvider(exception)
                ? ErrorCode.UNSUPPORTED_AUTH_PROVIDER
                : ErrorCode.INVALID_INPUT;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    /**
     * 낙관적 잠금({@code @Version}) 충돌을 409 {@code C003 CONFLICT} 로 변환한다.
     *
     * <p>같은 리소스를 두 요청이 동시에 갱신하면 늦게 커밋하는 쪽이 실패한다.
     * (예: 팀 검토 재제출과 팀장의 resolve 처리가 동시에 실행되는 경우)
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(
            OptimisticLockingFailureException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.CONFLICT.getStatus())
                .body(ApiResponse.error(ErrorCode.CONFLICT));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.CONFLICT.getStatus())
                .body(ApiResponse.error(ErrorCode.CONFLICT));
    }

    /**
     * 처리되지 않은 서버 예외를 안전한 공통 응답으로 변환한다.
     *
     * <p>Spring MVC가 상태 코드를 이미 정의한 예외는 해당 상태를 그대로 유지하고,
     * 그 밖의 예상하지 못한 예외만 내부 서버 오류로 변환한다.
     *
     * <p>예외 메시지와 요청 데이터는 민감정보를 포함할 수 있으므로 로그에 남기지 않고,
     * 진단에 필요한 예외 타입만 기록한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpectedException(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode()).build();
        }

        log.error("Unhandled server exception. exceptionType={}", exception.getClass().getName());

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<ApiResponse<Void>> invalidInput(FieldError error) {
        return invalidInput(List.of(error));
    }

    private ResponseEntity<ApiResponse<Void>> invalidInput(List<FieldError> errors) {
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, errors));
    }

    private boolean isUnsupportedAuthProvider(HttpMessageNotReadableException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof InvalidFormatException invalidFormatException
                    && invalidFormatException.getTargetType() == AuthProvider.class) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @ExceptionHandler(AiGuardrailExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiGuardrailExceeded(
            AiGuardrailExceededException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(ApiResponse.error(errorCode));
    }
}
