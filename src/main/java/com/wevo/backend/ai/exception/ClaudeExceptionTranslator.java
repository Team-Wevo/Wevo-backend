package com.wevo.backend.ai.exception;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NoCredentialsException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

@Component
public class ClaudeExceptionTranslator {

    public ClaudeProviderException translate(Throwable throwable) {
        if (throwable instanceof ClaudeProviderException providerException) {
            return providerException;
        }

        Throwable providerCause = findProviderCause(throwable);
        if (providerCause instanceof NoCredentialsException) {
            return new ClaudeProviderException(ErrorCode.AI_PROVIDER_AUTHENTICATION_FAILED, throwable);
        }
        if (providerCause instanceof AnthropicServiceException serviceException) {
            return new ClaudeProviderException(mapStatus(serviceException.statusCode()), throwable);
        }
        if (providerCause instanceof AnthropicIoException) {
            ErrorCode errorCode = hasTimeoutCause(providerCause)
                    ? ErrorCode.AI_PROVIDER_TIMEOUT
                    : ErrorCode.AI_PROVIDER_UNAVAILABLE;
            return new ClaudeProviderException(errorCode, throwable);
        }
        if (hasTimeoutCause(throwable)) {
            return new ClaudeProviderException(ErrorCode.AI_PROVIDER_TIMEOUT, throwable);
        }
        return new ClaudeProviderException(ErrorCode.AI_PROVIDER_ERROR, throwable);
    }

    private ErrorCode mapStatus(int statusCode) {
        return switch (statusCode) {
            case 400, 413, 422 -> ErrorCode.AI_INVALID_REQUEST;
            case 401 -> ErrorCode.AI_PROVIDER_AUTHENTICATION_FAILED;
            case 403 -> ErrorCode.AI_PROVIDER_PERMISSION_DENIED;
            case 404 -> ErrorCode.AI_MODEL_NOT_AVAILABLE;
            case 429 -> ErrorCode.AI_RATE_LIMITED;
            case 504 -> ErrorCode.AI_PROVIDER_TIMEOUT;
            case 529 -> ErrorCode.AI_PROVIDER_OVERLOADED;
            default -> statusCode >= 500
                    ? ErrorCode.AI_PROVIDER_UNAVAILABLE
                    : ErrorCode.AI_PROVIDER_ERROR;
        };
    }

    private Throwable findProviderCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoCredentialsException
                    || current instanceof AnthropicServiceException
                    || current instanceof AnthropicIoException) {
                return current;
            }
            current = current.getCause();
        }
        return throwable;
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
