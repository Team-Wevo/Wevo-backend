package com.wevo.backend.ai.exception;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;

public class AiAuditPersistenceException extends BusinessException {

    public AiAuditPersistenceException(Throwable cause) {
        super(ErrorCode.AI_AUDIT_LOG_PERSISTENCE_FAILED);
        initCause(cause);
    }
}
