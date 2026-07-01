package com.forgeflow.pojo.vo.resp;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RespRequirementUploadVo implements Serializable {

    private Long requirementId;

    private String versionNo;

    private String status;

    private Integer materialCount;

    private LocalDateTime createdAt;
}
