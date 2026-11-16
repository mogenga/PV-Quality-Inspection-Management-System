package com.pvqims.common.exception;

/**
 * 业务错误码。code 的百位即对应的 HTTP 状态码。
 */
public enum ErrorCode {

    PARAM_INVALID(40001, "参数校验失败"),
    USERNAME_EXISTS(40002, "用户名已存在"),
    BAD_CREDENTIALS(40100, "用户名或密码错误"),
    UNAUTHORIZED(40101, "未登录或登录已过期"),
    FORBIDDEN(40300, "无权限执行该操作"),
    NOT_FOUND(40400, "资源不存在"),
    INTERNAL_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
