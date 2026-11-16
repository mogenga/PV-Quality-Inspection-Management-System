package com.pvqims.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应信封。code 为 0 表示成功；data 为空时不序列化该字段。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(int code, String message, T data) {

    private static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MESSAGE = "ok";

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static <T> ApiResult<T> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
