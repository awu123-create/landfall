package com.landfall.common.api;

import java.util.Objects;

public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").resolveMessage(message));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
