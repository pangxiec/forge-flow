package com.forgeflow.admin.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeflow.dao.domain.Requirement;
import com.forgeflow.third.llm.LlmChatRequest;
import com.forgeflow.third.llm.LlmGateway;
import jakarta.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PrdAgent {

    private static final Logger log = LoggerFactory.getLogger(PrdAgent.class);

    private static final String ANALYSIS_SYSTEM_PROMPT = """
            你是一位资深的产品需求分析师。请根据用户提供的需求信息进行结构化分析，输出以下三部分内容：

            1. structuredSummary（结构化需求摘要）：对需求标题、来源、优先级、需求方、产品负责人、业务背景、目标与成功标准、范围与边界进行归纳整理，形成条理清晰的结构化摘要，使用Markdown列表格式。
            2. missingInfo（缺失信息）：识别需求中缺失或不足的关键信息，如期望完成日期、业务背景深度、目标可验收性、范围边界清晰度、补充材料等。如无明显缺失，输出"暂无明显缺失信息"，使用Markdown列表格式。
            3. clarificationQuestions（待澄清问题）：列出需要与需求方进一步澄清的关键问题，覆盖核心用户场景、交付范围优先级、审批权限规则、异常处理等维度，使用Markdown列表格式。

            请严格以JSON格式返回，不要包含markdown代码块标记或任何其他多余文本，格式如下：
            {"structuredSummary":"结构化需求摘要内容","missingInfo":"缺失信息内容","clarificationQuestions":"待澄清问题内容"}
            """;

    private static final String PRD_SYSTEM_PROMPT = """
            你是一位资深产品经理，正在为企业内部研发团队编写可进入审批流的正式PRD。

            请基于用户提供的需求信息、结构化摘要、缺失信息和澄清问题，生成完整PRD Markdown。

            要求：
            1. 只输出Markdown正文，不要输出JSON，不要输出markdown代码块标记。
            2. 内容必须具体贴合业务，不要泛泛而谈。
            3. 必须覆盖以下章节：
               # {需求标题} PRD
               ## 1. 文档信息
               ## 2. 业务背景
               ## 3. 业务目标
               ## 4. 范围与边界
               ## 5. 用户角色
               ## 6. 核心业务流程
               ## 7. 功能清单
               ## 8. 页面与交互说明
               ## 9. 业务规则
               ## 10. 状态流转
               ## 11. 异常场景
               ## 12. 权限与审批要求
               ## 13. 数据与字段要求
               ## 14. 非功能要求
               ## 15. 验收标准
               ## 16. 待澄清问题
            4. 对未明确的信息，要标注“待确认”，不要编造具体公司内部制度、金额阈值、真实接口或真实密钥。
            5. 验收标准使用可验证条目，适合后续测试负责人生成测试用例。
            """;

    @Resource
    private LlmGateway llmGateway;

    @Resource
    private ObjectMapper objectMapper;

    public RequirementAnalysis analyze(Requirement requirement) {
        try {
            String userPrompt = buildAnalysisUserPrompt(requirement);
            String llmResponse = llmGateway.chat(LlmChatRequest.builder()
                    .scene("requirement-analysis")
                    .projectId(requirement.getProjectId())
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .systemPrompt(ANALYSIS_SYSTEM_PROMPT)
                    .userPrompt(userPrompt)
                    .timeoutSeconds(180)
                    .build()).getContent();
            return parseAnalysisResponse(llmResponse);
        } catch (Exception e) {
            log.warn("PRD Agent requirement analysis fell back to local rules: {}", e.getMessage());
            return localAnalyze(requirement);
        }
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
        try {
            String llmResponse = llmGateway.chat(LlmChatRequest.builder()
                    .scene("prd-generation")
                    .projectId(requirement.getProjectId())
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .systemPrompt(PRD_SYSTEM_PROMPT)
                    .userPrompt(buildPrdUserPrompt(requirement, analysis))
                    .timeoutSeconds(180)
                    .build()).getContent();
            String prdMarkdown = stripMarkdownFence(llmResponse);
            if (StringUtils.hasText(prdMarkdown)) {
                return prdMarkdown;
            }
        } catch (Exception e) {
            log.warn("PRD Agent document generation fell back to local rules: {}", e.getMessage());
        }
        return localGeneratePrd(requirement, analysis);
    }

    private String buildPrdUserPrompt(Requirement requirement, RequirementAnalysis analysis) {
        String expectedDate = requirement.getExpectedDate() == null
                ? "待确认"
                : requirement.getExpectedDate().format(DateTimeFormatter.ISO_LOCAL_DATE);

        return String.join("\n",
                "请为以下需求生成正式PRD：",
                "",
                "项目ID：" + requirement.getProjectId(),
                "需求ID：" + requirement.getId(),
                "需求标题：" + normalize(requirement.getTitle()),
                "需求版本：" + normalize(requirement.getVersionNo()),
                "需求来源：" + normalize(requirement.getSourceType()),
                "优先级：" + normalize(requirement.getPriority()),
                "需求方：" + normalize(requirement.getRequester()),
                "产品负责人：" + normalize(requirement.getProductOwner()),
                "期望完成日期：" + expectedDate,
                "补充材料数量：" + (requirement.getMaterialCount() == null ? 0 : requirement.getMaterialCount()),
                "",
                "业务背景：",
                normalize(requirement.getBackground()),
                "",
                "目标与成功标准：",
                normalize(requirement.getObjective()),
                "",
                "范围与边界：",
                normalize(requirement.getScope()),
                "",
                "结构化摘要：",
                analysis.structuredSummary(),
                "",
                "缺失信息：",
                analysis.missingInfo(),
                "",
                "待澄清问题：",
                analysis.clarificationQuestions());
    }

    private RequirementAnalysis localAnalyze(Requirement requirement) {
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
                "- 需求标题：" + normalize(requirement.getTitle()),
                "- 需求来源：" + normalize(requirement.getSourceType()) + "，优先级：" + normalize(requirement.getPriority()),
                "- 需求方：" + normalize(requirement.getRequester()) + "，产品负责人：" + normalize(requirement.getProductOwner()),
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

    private String localGeneratePrd(Requirement requirement, RequirementAnalysis analysis) {
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

    private String stripMarkdownFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private String toMarkdownList(List<String> items) {
        if (items.isEmpty()) {
            return "- 暂无明显缺失信息";
        }
        return String.join("\n", items.stream().map(item -> "- " + item).toList());
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
