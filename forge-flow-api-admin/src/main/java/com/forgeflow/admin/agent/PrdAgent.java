package com.forgeflow.admin.agent;

import com.forgeflow.dao.domain.Requirement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PrdAgent {

    public RequirementAnalysis analyze(Requirement requirement) {
        List<String> missingItems = new ArrayList<>();
        if (requirement.getExpectedDate() == null) {
            missingItems.add("期望完成日期未明确");
        }
        if (textLength(requirement.getBackground()) < 30) {
            missingItems.add("业务背景偏短，需要补充现状、痛点和触发原因");
        }
        if (textLength(requirement.getObjective()) < 30) {
            missingItems.add("目标与成功标准偏短，需要补充可验收指标");
        }
        if (textLength(requirement.getScope()) < 20) {
            missingItems.add("范围与边界偏短，需要补充不包含内容、依赖系统和异常场景");
        }
        if (requirement.getMaterialCount() == null || requirement.getMaterialCount() == 0) {
            missingItems.add("未上传补充材料，可按需补充会议纪要、流程图或截图");
        }

        String summary = String.join("\n",
                "- 需求标题：" + requirement.getTitle(),
                "- 需求来源：" + requirement.getSourceType() + "，优先级：" + requirement.getPriority(),
                "- 需求方：" + requirement.getRequester() + "，产品负责人：" + requirement.getProductOwner(),
                "- 业务背景：" + normalize(requirement.getBackground()),
                "- 目标与成功标准：" + normalize(requirement.getObjective()),
                "- 范围与边界：" + normalize(requirement.getScope()));

        List<String> questions = new ArrayList<>();
        questions.add("核心用户在什么场景下最高频使用该能力？");
        questions.add("本次需求的必须交付范围和可后置范围分别是什么？");
        questions.add("有哪些审批、权限、数据留痕或异常处理规则必须在第一版覆盖？");
        if (requirement.getExpectedDate() == null) {
            questions.add("业务期望的上线或验收日期是什么？");
        }
        if (requirement.getMaterialCount() == null || requirement.getMaterialCount() == 0) {
            questions.add("是否存在流程图、字段清单、原型草图或历史系统截图可作为补充输入？");
        }

        return new RequirementAnalysis(summary, toMarkdownList(missingItems), toMarkdownList(questions));
    }

    public String generatePrd(Requirement requirement) {
        RequirementAnalysis analysis = analyze(requirement);
        String expectedDate = requirement.getExpectedDate() == null
                ? "待确认"
                : requirement.getExpectedDate().format(DateTimeFormatter.ISO_LOCAL_DATE);

        return String.join("\n\n",
                "# " + requirement.getTitle() + " PRD",
                "## 1. 文档信息\n"
                        + "- 项目ID：" + requirement.getProjectId() + "\n"
                        + "- 需求ID：" + requirement.getId() + "\n"
                        + "- 版本号：" + requirement.getVersionNo() + "\n"
                        + "- 需求方：" + requirement.getRequester() + "\n"
                        + "- 产品负责人：" + requirement.getProductOwner() + "\n"
                        + "- 优先级：" + requirement.getPriority() + "\n"
                        + "- 期望完成日期：" + expectedDate,
                "## 2. 业务背景\n" + normalize(requirement.getBackground()),
                "## 3. 目标与成功标准\n" + normalize(requirement.getObjective()),
                "## 4. 范围与边界\n" + normalize(requirement.getScope()),
                "## 5. 结构化需求摘要\n" + analysis.structuredSummary(),
                "## 6. 用户角色\n"
                        + "- 需求方：确认业务目标、业务规则和验收结果。\n"
                        + "- 产品经理：维护需求内容、处理澄清问题并发起 PRD 审批。\n"
                        + "- 研发负责人：确认接口、数据、页面和工程实现可行性。\n"
                        + "- 测试负责人：确认验收标准和测试覆盖范围。",
                "## 7. 功能清单\n"
                        + "- 需求录入与版本留痕。\n"
                        + "- AI 需求分析与澄清问题生成。\n"
                        + "- PRD 初稿生成、编辑、审批和冻结。\n"
                        + "- 审计记录覆盖需求上传、分析和 PRD 生成动作。",
                "## 8. 业务规则\n"
                        + "- 上传给模型前必须确认敏感信息已脱敏。\n"
                        + "- PRD 未审批通过前不得进入原型生成。\n"
                        + "- 冻结后的 PRD 不允许直接覆盖，变更应形成新版本或补充说明。",
                "## 9. 异常场景\n"
                        + "- 需求输入不完整时，系统生成缺失信息和澄清问题，由产品经理补充后重新分析。\n"
                        + "- AI 生成失败时，生成任务记录失败原因，项目流程进入人工处理。\n"
                        + "- 审批驳回时，必须记录驳回原因和建议修改方向。",
                "## 10. 验收标准\n"
                        + "- 可以查看需求结构化摘要、缺失信息和澄清问题。\n"
                        + "- 可以基于需求生成完整 PRD 初稿。\n"
                        + "- 生成任务、PRD 文档和审计日志均可追溯。\n"
                        + "- PRD 内容覆盖背景、目标、范围、角色、功能、规则、异常和验收标准。",
                "## 11. 待澄清问题\n" + analysis.clarificationQuestions());
    }

    private String toMarkdownList(List<String> items) {
        if (items.isEmpty()) {
            return "- 暂无明显缺失信息";
        }
        return items.stream().map(item -> "- " + item).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private int textLength(String value) {
        return StringUtils.hasText(value) ? value.trim().length() : 0;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "待补充";
    }

    public record RequirementAnalysis(String structuredSummary, String missingInfo, String clarificationQuestions) {
    }
}
