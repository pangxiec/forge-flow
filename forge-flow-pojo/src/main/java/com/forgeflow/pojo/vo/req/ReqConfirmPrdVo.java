package com.forgeflow.pojo.vo.req;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;

@Data
public class ReqConfirmPrdVo implements Serializable {

    @NotNull(message = "projectId cannot be null")
    private Long projectId;

    @NotNull(message = "prdId cannot be null")
    private Long prdId;

    private Long operatorId;
}
