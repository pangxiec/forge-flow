package com.forgeflow.pojo.vo.req;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;

@Data
public class ReqGeneratePrdVo implements Serializable {

    @NotNull(message = "projectId cannot be null")
    private Long projectId;

    private Long requirementId;

    private Long operatorId;
}
