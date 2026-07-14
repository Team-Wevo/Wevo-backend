package com.wevo.backend.ai.prompt;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;

public class PromptException extends BusinessException {

    public PromptException(ErrorCode errorCode) {
        super(errorCode);
    }
}
