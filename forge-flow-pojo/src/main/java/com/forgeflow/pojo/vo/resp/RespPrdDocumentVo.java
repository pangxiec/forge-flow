package com.forgeflow.pojo.vo.resp;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RespPrdDocumentVo implements Serializable {

    private Long id;

    private Long projectId;

    private Long requirementId;

    private Long taskId;

    private String title;

    private String content;

    private String status;

    private String versionNo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
