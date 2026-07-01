package com.forgeflow.pojo.vo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;

@Data
public class ReqCreateProjectVo implements Serializable {

    @NotBlank(message = "projectName cannot be blank")
    private String projectName;

    @NotBlank(message = "projectCode cannot be blank")
    private String projectCode;

    private String description;

    @NotNull(message = "managerId cannot be null")
    private Long managerId;
}
