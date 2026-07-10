package com.forgeflow.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.admin.agent.PrototypeAgent;
import com.forgeflow.admin.agent.PrototypeAgentExecution;
import com.forgeflow.admin.service.AuditLogService;
import com.forgeflow.admin.service.PrototypeService;
import com.forgeflow.common.enums.ProjectStatusEnum;
import com.forgeflow.common.enums.UserRoleEnum;
import com.forgeflow.common.exception.BizException;
import com.forgeflow.dao.domain.GenerationTask;
import com.forgeflow.dao.domain.PrdDocument;
import com.forgeflow.dao.domain.Project;
import com.forgeflow.dao.domain.PrototypeArtifact;
import com.forgeflow.dao.domain.Requirement;
import com.forgeflow.dao.mapper.GenerationTaskMapper;
import com.forgeflow.dao.mapper.PrdDocumentMapper;
import com.forgeflow.dao.mapper.ProjectMapper;
import com.forgeflow.dao.mapper.PrototypeArtifactMapper;
import com.forgeflow.dao.mapper.RequirementMapper;
import com.forgeflow.pojo.vo.req.ReqGeneratePrototypeVo;
import com.forgeflow.pojo.vo.resp.RespPrototypeArtifactVo;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrototypeServiceImpl implements PrototypeService {

    private static final String PRD_STATUS_CONFIRMED = "PRD_CONFIRMED";
    private static final String PROTOTYPE_STATUS_REVIEWING = "PROTOTYPE_REVIEWING";
    private static final String PROTOTYPE_STATUS_WAITING_USER = "WAITING_USER";
    private static final String PROTOTYPE_TYPE_HTML = "HTML_PROTOTYPE";
    private static final String TASK_TYPE_PROTOTYPE_GENERATE = "PROTOTYPE";
    private static final String TASK_STATUS_RUNNING = "RUNNING";
    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String TASK_STATUS_WAITING_USER = "WAITING_USER";
    private static final String TASK_STATUS_FAILED = "FAILED";
    private static final String PROTOTYPE_AGENT = "LLM_PROTOTYPE_AGENT";

    @Resource
    private PrototypeArtifactMapper prototypeArtifactMapper;

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
    private PrototypeAgent prototypeAgent;

    @Override
    public RespPrototypeArtifactVo generate(ReqGeneratePrototypeVo reqVo) {
        Project project = getProject(reqVo.getProjectId());
        PrdDocument prdDocument = getPrdDocument(project.getId(), reqVo.getPrdId());
        if (!PRD_STATUS_CONFIRMED.equals(prdDocument.getStatus())) {
            throw new BizException("prd document must be confirmed before generating prototype");
        }
        Requirement requirement = requirementMapper.selectById(prdDocument.getRequirementId());
        if (requirement == null) {
            throw new BizException("requirement not found");
        }

        GenerationTask task = new GenerationTask();
        task.setProjectId(project.getId());
        task.setTaskType(TASK_TYPE_PROTOTYPE_GENERATE);
        task.setStatus(TASK_STATUS_RUNNING);
        task.setInputArtifactId(prdDocument.getId());
        task.setModelName(PROTOTYPE_AGENT);
        task.setStartedAt(LocalDateTime.now());
        task.setCreatedBy(operatorId(reqVo.getOperatorId(), project));
        task.setUpdatedBy(operatorId(reqVo.getOperatorId(), project));
        generationTaskMapper.insert(task);

        PrototypeAgentExecution execution;
        try {
            execution = prototypeAgent.run(task.getId(), project.getId(),
                    operatorId(reqVo.getOperatorId(), project), requirement, prdDocument);
        } catch (RuntimeException e) {
            markTaskFailed(task, e);
            throw e;
        }

        PrototypeArtifact prototypeArtifact = new PrototypeArtifact();
        prototypeArtifact.setProjectId(project.getId());
        prototypeArtifact.setRequirementId(requirement.getId());
        prototypeArtifact.setPrdId(prdDocument.getId());
        prototypeArtifact.setTitle(requirement.getTitle() + " 页面原型");
        prototypeArtifact.setPrototypeType(PROTOTYPE_TYPE_HTML);
        prototypeArtifact.setContent(execution.html());
        prototypeArtifact.setStatus(execution.clarificationRequired()
                ? PROTOTYPE_STATUS_WAITING_USER : PROTOTYPE_STATUS_REVIEWING);
        prototypeArtifact.setVersionNo(nextPrototypeVersion(project.getId()));
        prototypeArtifact.setCreatedBy(operatorId(reqVo.getOperatorId(), project));
        prototypeArtifact.setUpdatedBy(operatorId(reqVo.getOperatorId(), project));
        prototypeArtifactMapper.insert(prototypeArtifact);

        task.setStatus(execution.clarificationRequired() ? TASK_STATUS_WAITING_USER : TASK_STATUS_SUCCESS);
        task.setOutputArtifactId(prototypeArtifact.getId());
        task.setFinishedAt(execution.clarificationRequired() ? null : LocalDateTime.now());
        generationTaskMapper.updateById(task);

        if (!execution.clarificationRequired()) {
            project.setCurrentStage(ProjectStatusEnum.PROTOTYPE_REVIEWING.getCode());
            project.setStatus(ProjectStatusEnum.PROTOTYPE_REVIEWING.getCode());
            project.setUpdatedBy(operatorId(reqVo.getOperatorId(), project));
            projectMapper.updateById(project);
        }

        auditLogService.record(operatorId(reqVo.getOperatorId(), project), UserRoleEnum.PRODUCT_MANAGER.getCode(),
                "GENERATE_PROTOTYPE", "PROTOTYPE_ARTIFACT", prototypeArtifact.getId(), null, prototypeArtifact.getTitle());

        RespPrototypeArtifactVo respVo = convert(prototypeArtifact);
        respVo.setTaskId(task.getId());
        return respVo;
    }

    @Override
    public RespPrototypeArtifactVo resume(Long taskId) {
        GenerationTask task = getResumableTask(taskId, TASK_TYPE_PROTOTYPE_GENERATE);
        Project project = getProject(task.getProjectId());
        PrdDocument prdDocument = getPrdDocument(project.getId(), task.getInputArtifactId());
        Requirement requirement = requirementMapper.selectById(prdDocument.getRequirementId());
        if (requirement == null) {
            throw new BizException("requirement not found");
        }
        Long operatorId = task.getCreatedBy() == null ? project.getManagerId() : task.getCreatedBy();
        task.setStatus(TASK_STATUS_RUNNING);
        task.setErrorMessage(null);
        generationTaskMapper.updateById(task);

        PrototypeAgentExecution execution;
        try {
            execution = prototypeAgent.resume(
                    task.getId(), project.getId(), operatorId, requirement, prdDocument);
        } catch (RuntimeException e) {
            markTaskFailed(task, e);
            throw e;
        }

        PrototypeArtifact artifact = task.getOutputArtifactId() == null
                ? null : prototypeArtifactMapper.selectById(task.getOutputArtifactId());
        if (artifact == null) {
            artifact = new PrototypeArtifact();
            artifact.setProjectId(project.getId());
            artifact.setRequirementId(requirement.getId());
            artifact.setPrdId(prdDocument.getId());
            artifact.setTitle(requirement.getTitle() + " Prototype");
            artifact.setPrototypeType(PROTOTYPE_TYPE_HTML);
            artifact.setVersionNo(nextPrototypeVersion(project.getId()));
            artifact.setCreatedBy(operatorId);
        }
        artifact.setContent(execution.html());
        artifact.setStatus(execution.clarificationRequired()
                ? PROTOTYPE_STATUS_WAITING_USER : PROTOTYPE_STATUS_REVIEWING);
        artifact.setUpdatedBy(operatorId);
        if (artifact.getId() == null) {
            prototypeArtifactMapper.insert(artifact);
        } else {
            prototypeArtifactMapper.updateById(artifact);
        }

        task.setOutputArtifactId(artifact.getId());
        task.setStatus(execution.clarificationRequired() ? TASK_STATUS_WAITING_USER : TASK_STATUS_SUCCESS);
        task.setFinishedAt(execution.clarificationRequired() ? null : LocalDateTime.now());
        generationTaskMapper.updateById(task);
        if (!execution.clarificationRequired()) {
            project.setCurrentStage(ProjectStatusEnum.PROTOTYPE_REVIEWING.getCode());
            project.setStatus(ProjectStatusEnum.PROTOTYPE_REVIEWING.getCode());
            project.setUpdatedBy(operatorId);
            projectMapper.updateById(project);
        }
        RespPrototypeArtifactVo response = convert(artifact);
        response.setTaskId(task.getId());
        return response;
    }

    @Override
    public RespPrototypeArtifactVo getLatest(Long projectId) {
        PrototypeArtifact prototypeArtifact = prototypeArtifactMapper.selectOne(Wrappers.<PrototypeArtifact>lambdaQuery()
                .eq(PrototypeArtifact::getProjectId, projectId)
                .eq(PrototypeArtifact::getPrototypeType, PROTOTYPE_TYPE_HTML)
                .orderByDesc(PrototypeArtifact::getCreatedAt)
                .last("LIMIT 1"));
        if (prototypeArtifact == null) {
            throw new BizException("prototype artifact not found");
        }
        return convert(prototypeArtifact);
    }

    private Project getProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException("project not found");
        }
        return project;
    }

    private GenerationTask getResumableTask(Long taskId, String taskType) {
        GenerationTask task = generationTaskMapper.selectById(taskId);
        if (task == null || !taskType.equals(task.getTaskType())) {
            throw new BizException("generation task not found");
        }
        if (TASK_STATUS_SUCCESS.equals(task.getStatus())) {
            throw new BizException("generation task is already completed");
        }
        return task;
    }

    private void markTaskFailed(GenerationTask task, RuntimeException error) {
        task.setStatus(TASK_STATUS_FAILED);
        task.setErrorMessage(error.getMessage());
        task.setFinishedAt(null);
        generationTaskMapper.updateById(task);
    }

    private PrdDocument getPrdDocument(Long projectId, Long prdId) {
        PrdDocument prdDocument;
        if (prdId == null) {
            prdDocument = prdDocumentMapper.selectOne(Wrappers.<PrdDocument>lambdaQuery()
                    .eq(PrdDocument::getProjectId, projectId)
                    .orderByDesc(PrdDocument::getCreatedAt)
                    .last("LIMIT 1"));
        } else {
            prdDocument = prdDocumentMapper.selectById(prdId);
        }
        if (prdDocument == null || !projectId.equals(prdDocument.getProjectId())) {
            throw new BizException("prd document not found");
        }
        return prdDocument;
    }

    private String nextPrototypeVersion(Long projectId) {
        Long count = prototypeArtifactMapper.selectCount(Wrappers.<PrototypeArtifact>lambdaQuery()
                .eq(PrototypeArtifact::getProjectId, projectId));
        return "v" + (count + 1);
    }

    private RespPrototypeArtifactVo convert(PrototypeArtifact prototypeArtifact) {
        RespPrototypeArtifactVo respVo = new RespPrototypeArtifactVo();
        BeanUtils.copyProperties(prototypeArtifact, respVo);
        return respVo;
    }

    private Long operatorId(Long operatorId, Project project) {
        return operatorId == null ? project.getManagerId() : operatorId;
    }
}
