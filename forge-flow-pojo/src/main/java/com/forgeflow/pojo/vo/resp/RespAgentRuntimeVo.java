package com.forgeflow.pojo.vo.resp;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RespAgentRuntimeVo implements Serializable {
    private Long taskId;
    private String agentType;
    private String status;
    private String currentNode;
    private String nextNode;
    private Integer stepSequence;
    private Integer checkpointVersion;
    private String lastError;
    private LocalDateTime updatedAt;
}
