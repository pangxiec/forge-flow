package com.forgeflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum {

    SUCCESS("000000", "success"),
    PARAM_ERROR("400000", "invalid request parameter"),
    BUSINESS_ERROR("500001", "business error"),
    SYSTEM_ERROR("500000", "system error");

    private final String code;
    private final String msg;
}
