package com.forgeflow.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("llm_call_log")
public class LlmCallLog extends BaseDomain {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String scene;

    private String provider;

    private String modelName;

    private String status;

    private Long projectId;

    private String bizType;

    private Long bizId;

    private Integer promptCharCount;

    private Integer responseCharCount;

    private Integer attemptCount;

    private Long elapsedMillis;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
