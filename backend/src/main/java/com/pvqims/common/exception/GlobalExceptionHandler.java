package com.pvqims.common.exception;

import com.pvqims.common.api.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：所有错误以统一响应信封返回，内部细节不外泄。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BizException e) {
        HttpStatus status = HttpStatus.resolve(e.code() / 100);
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(ApiResult.fail(e.code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
            ? fieldError.getDefaultMessage()
            : ErrorCode.PARAM_INVALID.defaultMessage();
        return badRequest(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return badRequest("请求体格式错误");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult<Void>> handleBadParameter(Exception e) {
        return badRequest(ErrorCode.PARAM_INVALID.defaultMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception e) {
        log.error("未处理的服务器异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResult.fail(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    private ResponseEntity<ApiResult<Void>> badRequest(String message) {
        return ResponseEntity.badRequest().body(ApiResult.fail(ErrorCode.PARAM_INVALID.code(), message));
    }
}
