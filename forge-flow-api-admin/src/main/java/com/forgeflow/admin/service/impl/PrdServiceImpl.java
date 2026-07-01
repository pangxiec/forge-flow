package com.forgeflow.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.admin.agent.PrdAgent;
import com.forgeflow.admin.service.AuditLogService;
import com.forgeflow.admin.service.PrdService;
import com.forgeflow.common.enums.ProjectStatusEnum;
import com.forgeflow.common.enums.UserRoleEnum;
import com.forgeflow.common.exception.BizException;
import com.forgeflow.dao.domain.GenerationTask;
import com.forgeflow.dao.domain.PrdDocument;
import com.forgeflow.dao.domain.Project;
import com.forgeflow.dao.domain.Requirement;
import com.forgeflow.dao.mapper.GenerationTaskMapper;
import com.forgeflow.dao.mapper.PrdDocumentMapper;
import com.forgeflow.dao.mapper.ProjectMapper;
import com.forgeflow.dao.mapper.RequirementMapper;
import com.forgeflow.pojo.vo.req.ReqGeneratePrdVo;
import com.forgeflow.pojo.vo.resp.RespPrdDocumentVo;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrdServiceImpl implements PrdService {

    private static final String PRD_STATUS_REVIEWING = "PRD_REVIEWING";
    private static final String TASK_TYPE_PRD_GENERATE = "PRD";
    private static final String TASK_STATUS_RUNNING = "RUNNING";
    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String BAILIAN_AGENT = "BAILIAN_PRD_AGENT";

    @Resource
    private PrdDocumentMapper prdDocumentMapper;

    @Resource
    private RequirementMapper requirementMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private GenerationTaskMapper generationTaskMapper;

    @Resource
    private AuditLogService auditLogService;

    @Resource
    private PrdAgent prdAgent;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RespPrdDocumentVo generate(ReqGeneratePrdVo reqVo) {
        Project project = getProject(reqVo.getProjectId());
        Requirement requirement = getRequirement(reqVo.getProjectId(), reqVo.getRequirementId());

        GenerationTask task = new GenerationTask();
        task.setProjectId(project.getId());
        task.setTaskType(TASK_TYPE_PRD_GENERATE);
        task.setStatus(TASK_STATUS_RUNNING);
        task.setInputArtifactId(requirement.getId());
        task.setModelName(BAILIAN_AGENT);
        task.setStartedAt(LocalDateTime.now());
        task.setCreatedBy(operatorId(reqVo.getOperatorId(), project));
        task.setUpdatedBy(operatorId(reqVo.getOperatorId(), project));
        generationTaskMapper.insert(task);

        PrdDocument prdDocument = new PrdDocument();
        prdDocument.setProjectId(project.getId());
        prdDocument.setRequirementId(requirement.getId());
        prdDocument.setTitle(requirement.getTitle() + " PRD");
        prdDocument.setContent(prdAgent.generatePrd(requirement));
        prdDocument.setStatus(PRD_STATUS_REVIEWING);
        prdDocument.setVersionNo(nextPrdVersion(project.getId()));
        prdDocument.setCreatedBy(operatorId(reqVo.getOperatorId(), project));
        prdDocument.setUpdatedBy(operatorId(reqVo.getOperatorId(), project));
        prdDocumentMapper.insert(prdDocument);

        task.setStatus(TASK_STATUS_SUCCESS);
        task.setOutputArtifactId(prdDocument.getId());
        task.setFinishedAt(LocalDateTime.now());
        generationTaskMapper.updateById(task);

        project.setCurrentStage(ProjectStatusEnum.PRD_REVIEWING.getCode());
        project.setStatus(ProjectStatusEnum.PRD_REVIEWING.getCode());
        project.setUpdatedBy(operatorId(reqVo.getOperatorId(), project));
        projectMapper.updateById(project);

        auditLogService.record(operatorId(reqVo.getOperatorId(), project), UserRoleEnum.PRODUCT_MANAGER.getCode(),
                "GENERATE_PRD", "PRD_DOCUMENT", prdDocument.getId(), null, prdDocument.getTitle());

        RespPrdDocumentVo respVo = convert(prdDocument);
        respVo.setTaskId(task.getId());
        return respVo;
    }

    @Override
    public RespPrdDocumentVo getLatest(Long projectId) {
        PrdDocument prdDocument = prdDocumentMapper.selectOne(Wrappers.<PrdDocument>lambdaQuery()
                .eq(PrdDocument::getProjectId, projectId)
                .orderByDesc(PrdDocument::getCreatedAt)
                .last("LIMIT 1"));
        if (prdDocument == null) {
            throw new BizException("prd document not found");
        }
        return convert(prdDocument);
    }

    private Project getProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException("project not found");
        }
        return project;
    }

    private Requirement getRequirement(Long projectId, Long requirementId) {
        Requirement requirement;
        if (requirementId == null) {
            requirement = requirementMapper.selectOne(Wrappers.<Requirement>lambdaQuery()
                    .eq(Requirement::getProjectId, projectId)
                    .orderByDesc(Requirement::getCreatedAt)
                    .last("LIMIT 1"));
        } else {
            requirement = requirementMapper.selectById(requirementId);
        }
        if (requirement == null || !projectId.equals(requirement.getProjectId())) {
            throw new BizException("requirement not found");
        }
        return requirement;
    }

    private String nextPrdVersion(Long projectId) {
        Long count = prdDocumentMapper.selectCount(Wrappers.<PrdDocument>lambdaQuery()
                .eq(PrdDocument::getProjectId, projectId));
        return "v" + (count + 1);
    }

    private RespPrdDocumentVo convert(PrdDocument prdDocument) {
        RespPrdDocumentVo respVo = new RespPrdDocumentVo();
        BeanUtils.copyProperties(prdDocument, respVo);
        return respVo;
    }

    private Long operatorId(Long operatorId, Project project) {
        return operatorId == null ? project.getManagerId() : operatorId;
    }
}
