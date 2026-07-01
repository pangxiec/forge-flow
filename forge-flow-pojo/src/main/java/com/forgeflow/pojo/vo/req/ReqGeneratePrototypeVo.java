package com.forgeflow.pojo.vo.req;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;

@Data
public class ReqGeneratePrototypeVo implements Serializable {

    @NotNull(message = "projectId cannot be null")
    private Long projectId;

    private Long prdId;

    private Long operatorId;
}
