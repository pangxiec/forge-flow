package com.forgeflow.pojo.vo.resp;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RespRequirementAnalysisVo implements Serializable {

    private Long requirementId;

    private Long taskId;

    private String title;

    private String status;

    private String structuredSummary;

    private String missingInfo;

    private String clarificationQuestions;

    private LocalDateTime analyzedAt;
}
