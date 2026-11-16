package com.pvqims.common.exception;

/**
 * 业务异常，携带 {@link ErrorCode}；HTTP 状态由错误码推导。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int code() {
        return errorCode.code();
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
