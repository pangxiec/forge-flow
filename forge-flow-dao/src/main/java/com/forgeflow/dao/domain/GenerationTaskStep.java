package com.forgeflow.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("generation_task_step")
public class GenerationTaskStep extends BaseDomain {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
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
