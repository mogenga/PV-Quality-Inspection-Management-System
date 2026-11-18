package com.pvqims.common.exception;

/**
 * 业务错误码。code 的百位即对应的 HTTP 状态码。
 */
public enum ErrorCode {

    PARAM_INVALID(40001, "参数校验失败"),
    USERNAME_EXISTS(40002, "用户名已存在"),
    PROJECT_CODE_EXISTS(40003, "项目编号已存在"),
    BATCH_NO_EXISTS(40004, "该项目下批号已存在"),
    PROJECT_CLOSED(40005, "项目已关闭，批次不可修改"),
    PROJECT_HAS_DEPENDENTS(40006, "项目下存在批次或标准，不能删除"),
    BATCH_HAS_RECORDS(40007, "批次下已有检验记录，不能删除"),
    STANDARD_ITEM_EXISTS(40008, "该项目下检验项编码已存在"),
    BAD_CREDENTIALS(40100, "用户名或密码错误"),
    UNAUTHORIZED(40101, "未登录或登录已过期"),
    FORBIDDEN(40300, "无权限执行该操作"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE(41500, "不支持的请求类型"),
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
