package com.forgeflow.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("requirement")
public class Requirement extends BaseDomain {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

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

    private String originalContent;

    private String structuredSummary;

    private String missingInfo;

    private String clarificationQuestions;

    private Integer materialCount;

    private String status;

    private String versionNo;

    private Boolean sensitiveMasked;
}
