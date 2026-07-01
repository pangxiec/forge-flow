package com.forgeflow.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prototype_artifact")
public class PrototypeArtifact extends BaseDomain {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;

    private Long requirementId;

    private Long prdId;

    private String title;

    private String prototypeType;

    private String content;

    private String status;

    private String versionNo;
}
