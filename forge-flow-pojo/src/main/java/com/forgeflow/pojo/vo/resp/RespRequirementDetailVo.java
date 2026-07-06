package com.forgeflow.pojo.vo.resp;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RespRequirementDetailVo implements Serializable {

    private Long requirementId;

    private Long projectId;

    private String title;

    private String sourceType;

    private String priority;

    private String requester;

    private String productOwner;

    private LocalDate expectedDate;

    private String background;

    private String objective;

    private String scope;

    private Integer materialCount;

    private String status;

    private String versionNo;

    private Boolean sensitiveMasked;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
