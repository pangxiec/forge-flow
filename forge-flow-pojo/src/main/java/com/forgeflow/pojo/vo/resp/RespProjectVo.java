package com.forgeflow.pojo.vo.resp;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RespProjectVo implements Serializable {

    private Long id;
    private String projectName;
    private String projectCode;
    private String description;
    private String currentStage;
    private String status;
    private Long managerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
