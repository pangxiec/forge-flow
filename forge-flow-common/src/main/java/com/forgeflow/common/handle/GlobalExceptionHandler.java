package com.forgeflow.common.handle;

import com.forgeflow.common.enums.ResponseCodeEnum;
import com.forgeflow.common.exception.BizException;
import com.forgeflow.common.result.ResponseResult;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseResult<Void> handleBizException(BizException e) {
        return ResponseResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseResult<Void> handleValidationException(Exception e) {
        return ResponseResult.fail(ResponseCodeEnum.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseResult<Void> handleException(Exception e) {
        return ResponseResult.fail(ResponseCodeEnum.SYSTEM_ERROR.getCode(), e.getMessage());
    }
}
