package com.forgeflow.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_checkpoint")
public class AgentCheckpoint extends BaseDomain {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Long projectId;
    private String agentType;
    private String status;
    private String currentNode;
    private String nextNode;
    private String stateJson;
    private Integer stepSequence;
    private Integer checkpointVersion;
    private String lastError;
}
