package com.forgeflow.admin.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeflow.dao.domain.Requirement;
import com.forgeflow.third.client.BailianLlmClient;
import jakarta.annotation.Resource;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PrdAgent {

    private static final String ANALYSIS_SYSTEM_PROMPT = """
            你是一位资深的产品需求分析师。请根据用户提供的需求信息进行结构化分析，输出以下三部分内容：

            1. structuredSummary（结构化需求摘要）：对需求标题、来源、优先级、需求方、产品负责人、业务背景、目标与成功标准、范围与边界进行归纳整理，形成条理清晰的结构化摘要，使用Markdown列表格式。
            2. missingInfo（缺失信息）：识别需求中缺失或不足的关键信息，如期望完成日期、业务背景深度、目标可验收性、范围边界清晰度、补充材料等。如无明显缺失，输出"暂无明显缺失信息"，使用Markdown列表格式。
            3. clarificationQuestions（待澄清问题）：列出需要与需求方进一步澄清的关键问题，覆盖核心用户场景、交付范围优先级、审批权限规则、异常处理等维度，使用Markdown列表格式。

            请严格以JSON格式返回，不要包含markdown代码块标记或任何其他多余文本，格式如下：
            {"structuredSummary":"结构化需求摘要内容","missingInfo":"缺失信息内容","clarificationQuestions":"待澄清问题内容"}
            """;

    @Resource
    private BailianLlmClient bailianLlmClient;

    @Resource
    private ObjectMapper objectMapper;

    public RequirementAnalysis analyze(Requirement requirement) {
        String userPrompt = buildAnalysisUserPrompt(requirement);
        String llmResponse = bailianLlmClient.chat(ANALYSIS_SYSTEM_PROMPT, userPrompt);
        return parseAnalysisResponse(llmResponse);
    }

    private String buildAnalysisUserPrompt(Requirement requirement) {
        String expectedDate = requirement.getExpectedDate() == null
                ? "未明确"
                : requirement.getExpectedDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        int materialCount = requirement.getMaterialCount() == null ? 0 : requirement.getMaterialCount();

        return String.join("\n",
                "请分析以下需求信息：",
                "",
                "需求标题：" + normalize(requirement.getTitle()),
                "需求来源：" + normalize(requirement.getSourceType()),
                "优先级：" + normalize(requirement.getPriority()),
                "需求方：" + normalize(requirement.getRequester()),
                "产品负责人：" + normalize(requirement.getProductOwner()),
                "期望完成日期：" + expectedDate,
                "业务背景：" + normalize(requirement.getBackground()),
                "目标与成功标准：" + normalize(requirement.getObjective()),
                "范围与边界：" + normalize(requirement.getScope()),
                "补充材料数量：" + materialCount);
    }

    private RequirementAnalysis parseAnalysisResponse(String llmResponse) {
        try {
            String json = stripCodeBlock(llmResponse);
            JsonNode root = objectMapper.readTree(json);
            String structuredSummary = root.path("structuredSummary").asText("");
            String missingInfo = root.path("missingInfo").asText("");
            String clarificationQuestions = root.path("clarificationQuestions").asText("");

            if (structuredSummary.isBlank()) {
                structuredSummary = llmResponse;
            }
            if (missingInfo.isBlank()) {
                missingInfo = "- 暂无明显缺失信息";
            }
            if (clarificationQuestions.isBlank()) {
                clarificationQuestions = "- 暂无需澄清问题";
            }

            return new RequirementAnalysis(structuredSummary, missingInfo, clarificationQuestions);
        } catch (Exception e) {
            return new RequirementAnalysis(llmResponse, "- 暂无明显缺失信息", "- 暂无需澄清问题");
        }
    }

    private String stripCodeBlock(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "待补充";
    }

    public record RequirementAnalysis(String structuredSummary, String missingInfo, String clarificationQuestions) {
    }
}
