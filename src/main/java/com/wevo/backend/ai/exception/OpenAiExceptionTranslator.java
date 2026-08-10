package com.wevo.backend.ai.exception;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class OpenAiExceptionTranslator implements AiExceptionTranslator {

    @Override
    public AiProviderException translate(Throwable throwable) {
        if (throwable instanceof AiProviderException providerException) {
            return providerException;
        }

        Throwable providerCause = findProviderCause(throwable);
        if (providerCause instanceof OpenAIServiceException serviceException) {
            return new AiProviderException(
                    mapStatus(serviceException.statusCode()),
                    throwable,
                    null,
                    0,
                    retryAfter(serviceException)
            );
        }
        if (providerCause instanceof OpenAIIoException) {
            ErrorCode errorCode = hasTimeoutCause(providerCause)
                    ? ErrorCode.AI_PROVIDER_TIMEOUT
                    : ErrorCode.AI_PROVIDER_UNAVAILABLE;
            return new AiProviderException(errorCode, throwable);
        }
        if (providerCause instanceof OpenAIInvalidDataException) {
            return new AiProviderException(ErrorCode.AI_PROVIDER_INVALID_RESPONSE, throwable);
        }
        if (hasTimeoutCause(throwable)) {
            return new AiProviderException(ErrorCode.AI_PROVIDER_TIMEOUT, throwable);
        }
        if (hasConnectionCause(throwable)) {
            return new AiProviderException(ErrorCode.AI_PROVIDER_UNAVAILABLE, throwable);
        }
        return new AiProviderException(ErrorCode.AI_PROVIDER_ERROR, throwable);
    }

    private ErrorCode mapStatus(int statusCode) {
        return switch (statusCode) {
            case 400, 413, 422 -> ErrorCode.AI_INVALID_REQUEST;
            case 401 -> ErrorCode.AI_PROVIDER_AUTHENTICATION_FAILED;
            case 403 -> ErrorCode.AI_PROVIDER_PERMISSION_DENIED;
            case 404, 410 -> ErrorCode.AI_MODEL_NOT_AVAILABLE;
            case 408, 504 -> ErrorCode.AI_PROVIDER_TIMEOUT;
            case 429 -> ErrorCode.AI_RATE_LIMITED;
            default -> statusCode >= 500
                    ? ErrorCode.AI_PROVIDER_UNAVAILABLE
                    : ErrorCode.AI_PROVIDER_ERROR;
        };
    }

    private Throwable findProviderCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OpenAIServiceException
                    || current instanceof OpenAIIoException
                    || current instanceof OpenAIInvalidDataException) {
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

    private boolean hasConnectionCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof NoRouteToHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration retryAfter(OpenAIServiceException exception) {
        return exception.headers().values("retry-after").stream()
                .findFirst()
                .flatMap(value -> {
                    try {
                        long seconds = Long.parseLong(value.strip());
                        return seconds < 0
                                ? java.util.Optional.empty()
                                : java.util.Optional.of(Duration.ofSeconds(seconds));
                    } catch (NumberFormatException ignored) {
                        return java.util.Optional.empty();
                    }
                })
                .orElse(null);
    }
}
