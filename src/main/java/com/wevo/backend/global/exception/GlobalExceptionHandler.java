package com.wevo.backend.global.exception;

import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.ai.exception.AiGuardrailExceededException;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.response.FieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

import java.sql.SQLException;
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
    /** SQL 표준 unique_violation. PostgreSQL·H2 공통. */
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

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

    /**
     * DB 무결성 제약 위반을 원인별로 나눠 변환한다.
     *
     * <p><b>유니크 위반만 409({@code C003})</b> 다 — 같은 값이 이미 있다는 뜻이라 §5.6 의 409 정의
     * ("저장된 리소스의 현재 상태와 충돌")에 맞고, 동시 요청에서 정상적으로 발생할 수 있다.
     *
     * <p>그 밖의 위반(FK·NOT NULL·CHECK)은 <b>서버가 보낼 수 없는 데이터를 보낸 것</b>이므로 500 으로
     * 돌린다. 전부 409 로 뭉치면 "요청이 현재 상태와 충돌합니다"라는 안내가 나가 사용자는 재시도하고,
     * 정작 고쳐야 할 서버 버그는 드러나지 않는다.
     *
     * <p>제약 이름·값은 로그에 남기지 않는다 — 저장하려던 데이터가 메시지에 섞여 나올 수 있다(§7).
     * 진단에는 SQLState 로 충분하다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        if (isUniqueViolation(exception)) {
            return ResponseEntity
                    .status(ErrorCode.CONFLICT.getStatus())
                    .body(ApiResponse.error(ErrorCode.CONFLICT));
        }

        log.error("Data integrity violation that is not a unique constraint. sqlState={}",
                sqlState(exception));

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /** SQLState {@code 23505}(unique_violation)는 PostgreSQL·H2 가 같은 값을 쓴다. */
    private boolean isUniqueViolation(DataIntegrityViolationException exception) {
        return UNIQUE_VIOLATION_SQL_STATE.equals(sqlState(exception));
    }

    private String sqlState(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }

    /**
     * 처리되지 않은 서버 예외를 안전한 공통 응답으로 변환한다.
     *
     * <p>Spring MVC가 상태 코드를 이미 정의한 예외({@link ErrorResponse})는 그 상태를 유지하되
     * <b>본문은 반드시 공통 형식으로 채운다</b>. 상태만 남기고 본문을 비우면 오타 경로(404)·잘못된
     * 메서드(405)·Content-Type 불일치(415) 같은 흔한 오류에서 {@code code} 가 없는 응답이 나가,
     * {@code code} 로 분기하는 클라이언트 공통 처리가 그 응답만 파싱하지 못한다. (§5.4)
     *
     * <p>예외 메시지와 요청 데이터는 민감정보를 포함할 수 있으므로 로그에 남기지 않고,
     * 진단에 필요한 예외 타입만 기록한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            // Spring 이 실은 응답 헤더를 그대로 넘긴다 — 405 의 Allow 는 표준상 필수고(RFC 9110),
            // 415 의 Accept 계열도 클라이언트가 요청을 고치는 데 필요한 정보다.
            return ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .body(ApiResponse.error(protocolErrorCode(status)));
        }

        log.error("Unhandled server exception. exceptionType={}", exception.getClass().getName());

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /**
     * Spring MVC 가 정한 상태에 대응하는 공통 오류 코드를 고른다.
     *
     * <p>이름이 붙은 상태만 전용 코드를 두고, 그 밖의 상태는 계열로 묶는다 — 상태 하나마다 코드를
     * 늘리면 클라이언트가 분기하지도 않을 코드만 쌓인다. (§5.8) 어느 경우든 <b>본문은 항상 채운다</b>.
     */
    private ErrorCode protocolErrorCode(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return ErrorCode.ENDPOINT_NOT_FOUND;
        }
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        return status.is5xxServerError() ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.INVALID_INPUT;
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
