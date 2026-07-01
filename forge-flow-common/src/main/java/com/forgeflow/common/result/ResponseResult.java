package com.forgeflow.common.result;

import com.forgeflow.common.enums.ResponseCodeEnum;
import java.io.Serializable;
import lombok.Data;

@Data
public class ResponseResult<T> implements Serializable {

    private String code;
    private Boolean success;
    private String msg;
    private T data;

    public static <T> ResponseResult<T> success(T data) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(ResponseCodeEnum.SUCCESS.getCode());
        result.setSuccess(true);
        result.setMsg(ResponseCodeEnum.SUCCESS.getMsg());
        result.setData(data);
        return result;
    }

    public static <T> ResponseResult<T> fail(String code, String msg) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(code);
        result.setSuccess(false);
        result.setMsg(msg);
        return result;
    }
}
