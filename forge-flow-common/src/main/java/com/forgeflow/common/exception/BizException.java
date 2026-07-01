package com.forgeflow.common.exception;

import com.forgeflow.common.enums.ResponseCodeEnum;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String message) {
        super(message);
        this.code = ResponseCodeEnum.BUSINESS_ERROR.getCode();
    }

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }
}
