package com.forgeflow.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.admin.agent.PrdAgent;
import com.forgeflow.admin.service.AuditLogService;
import com.forgeflow.admin.service.RequirementService;
import com.forgeflow.common.enums.ProjectStatusEnum;
import com.forgeflow.common.enums.UserRoleEnum;
import com.forgeflow.common.exception.BizException;
import com.forgeflow.dao.domain.GenerationTask;
import com.forgeflow.dao.domain.Project;
import com.forgeflow.dao.domain.Requirement;
import com.forgeflow.dao.mapper.GenerationTaskMapper;
import com.forgeflow.dao.mapper.ProjectMapper;
import com.forgeflow.dao.mapper.RequirementMapper;
import com.forgeflow.pojo.vo.req.ReqAnalyzeRequirementVo;
import com.forgeflow.pojo.vo.resp.RespRequirementAnalysisVo;
import com.forgeflow.pojo.vo.resp.RespRequirementUploadVo;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RequirementServiceImpl implements RequirementService {

    private static final String REQUIREMENT_STATUS_DRAFT = "DRAFT";
    private static final String REQUIREMENT_STATUS_REVIEWING = "REQUIREMENT_REVIEWING";
    private static final String TASK_TYPE_PRD_ANALYSIS = "PRD_ANALYSIS";
    private static final String TASK_STATUS_RUNNING = "RUNNING";
    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String LOCAL_RULE_AGENT = "LOCAL_RULE_PRD_AGENT";

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
    public RespRequirementUploadVo upload(Long projectId, String title, String sourceType, String priority,
            String requester, String productOwner, LocalDate expectedDate, String background, String objective,
            String scope, Boolean sensitiveMasked, List<MultipartFile> files) {
        Project project = getProject(projectId);
        if (!Boolean.TRUE.equals(sensitiveMasked)) {
            throw new BizException("requirement must be masked before AI analysis");
        }

        Requirement requirement = new Requirement();
        requirement.setProjectId(projectId);
        requirement.setTitle(title);
        requirement.setSourceType(sourceType);
        requirement.setPriority(priority);
        requirement.setRequester(requester);
        requirement.setProductOwner(productOwner);
        requirement.setExpectedDate(expectedDate);
        requirement.setBackground(background);
        requirement.setObjective(objective);
        requirement.setScope(scope);
        requirement.setOriginalContent(buildOriginalContent(background, objective, scope));
        requirement.setMaterialCount(files == null ? 0 : files.size());
        requirement.setStatus(REQUIREMENT_STATUS_DRAFT);
        requirement.setVersionNo(nextRequirementVersion(projectId));
        requirement.setSensitiveMasked(Boolean.TRUE);
        requirement.setCreatedBy(project.getManagerId());
        requirement.setUpdatedBy(project.getManagerId());
        requirementMapper.insert(requirement);

        auditLogService.record(project.getManagerId(), UserRoleEnum.PROJECT_MANAGER.getCode(), "UPLOAD_REQUIREMENT",
                "REQUIREMENT", requirement.getId(), null, requirement.getTitle());

        RespRequirementAnalysisVo analysisVo = analyzeRequirement(project, requirement);

        RespRequirementUploadVo respVo = new RespRequirementUploadVo();
        respVo.setRequirementId(requirement.getId());
        respVo.setVersionNo(requirement.getVersionNo());
        respVo.setStatus(analysisVo.getStatus());
        respVo.setMaterialCount(requirement.getMaterialCount());
        respVo.setCreatedAt(requirement.getCreatedAt());
        return respVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RespRequirementAnalysisVo analyze(ReqAnalyzeRequirementVo reqVo) {
        Project project = getProject(reqVo.getProjectId());
        Requirement requirement = getRequirement(reqVo.getProjectId(), reqVo.getRequirementId());
        return analyzeRequirement(project, requirement);
    }

    private RespRequirementAnalysisVo analyzeRequirement(Project project, Requirement requirement) {
        GenerationTask task = new GenerationTask();
        task.setProjectId(requirement.getProjectId());
        task.setTaskType(TASK_TYPE_PRD_ANALYSIS);
        task.setStatus(TASK_STATUS_RUNNING);
        task.setInputArtifactId(requirement.getId());
        task.setModelName(LOCAL_RULE_AGENT);
        task.setStartedAt(LocalDateTime.now());
        task.setCreatedBy(project.getManagerId());
        task.setUpdatedBy(project.getManagerId());
        generationTaskMapper.insert(task);

        PrdAgent.RequirementAnalysis analysis = prdAgent.analyze(requirement);
        requirement.setStructuredSummary(analysis.structuredSummary());
        requirement.setMissingInfo(analysis.missingInfo());
        requirement.setClarificationQuestions(analysis.clarificationQuestions());
        requirement.setStatus(REQUIREMENT_STATUS_REVIEWING);
        requirement.setUpdatedBy(project.getManagerId());
        requirementMapper.updateById(requirement);

        task.setStatus(TASK_STATUS_SUCCESS);
        task.setOutputArtifactId(requirement.getId());
        task.setFinishedAt(LocalDateTime.now());
        generationTaskMapper.updateById(task);

        project.setCurrentStage(ProjectStatusEnum.REQUIREMENT_REVIEWING.getCode());
        project.setStatus(ProjectStatusEnum.REQUIREMENT_REVIEWING.getCode());
        project.setUpdatedBy(project.getManagerId());
        projectMapper.updateById(project);

        auditLogService.record(project.getManagerId(), UserRoleEnum.PROJECT_MANAGER.getCode(),
                "ANALYZE_REQUIREMENT", "GENERATION_TASK", task.getId(), null, requirement.getTitle());

        RespRequirementAnalysisVo respVo = new RespRequirementAnalysisVo();
        respVo.setRequirementId(requirement.getId());
        respVo.setTaskId(task.getId());
        respVo.setTitle(requirement.getTitle());
        respVo.setStatus(requirement.getStatus());
        respVo.setStructuredSummary(requirement.getStructuredSummary());
        respVo.setMissingInfo(requirement.getMissingInfo());
        respVo.setClarificationQuestions(requirement.getClarificationQuestions());
        respVo.setAnalyzedAt(task.getFinishedAt());
        return respVo;
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

    private String nextRequirementVersion(Long projectId) {
        Long count = requirementMapper.selectCount(Wrappers.<Requirement>lambdaQuery()
                .eq(Requirement::getProjectId, projectId));
        return "v" + (count + 1);
    }

    private String buildOriginalContent(String background, String objective, String scope) {
        return "业务背景：\n" + normalize(background) + "\n\n目标与成功标准：\n" + normalize(objective)
                + "\n\n范围与边界：\n" + normalize(scope);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
