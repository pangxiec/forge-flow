package com.forgeflow.pojo.vo.resp;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RespGenerationTaskStepVo implements Serializable {

    private Long id;

    private Long taskId;

    private Long projectId;

    private Integer stepOrder;

    private String stepName;

    private String toolName;

    private String status;

    private String summary;

    private Long elapsedMillis;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
