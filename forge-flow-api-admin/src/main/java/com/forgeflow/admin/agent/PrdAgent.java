package com.forgeflow.admin.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeflow.dao.domain.PrdDocument;
import com.forgeflow.dao.domain.Requirement;
import com.forgeflow.dao.mapper.PrdDocumentMapper;
import com.forgeflow.third.llm.LlmChatRequest;
import com.forgeflow.third.llm.LlmGateway;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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

    private static final String PRD_REVIEW_SYSTEM_PROMPT = """
            你是一位严谨的 PRD 评审专家。请检查 PRD 是否可以进入审批流。
            重点检查：章节完整性、业务目标清晰度、范围边界、用户角色、功能清单、业务规则、异常场景、权限审批、数据字段、非功能需求、验收标准可测试性、待澄清问题是否合理。
            请严格返回 JSON，不要输出 markdown 代码块或其他文本，格式如下：
            {"passed":false,"summary":"评审摘要","issues":["问题1"],"suggestions":["建议1"]}
            """;

    private static final String PRD_REVISION_SYSTEM_PROMPT = """
            你是一位资深产品经理。请根据 PRD 评审意见修订 PRD。
            要求：
            1. 只输出修订后的 Markdown 正文，不要输出 JSON，不要输出 markdown 代码块标记。
            2. 保留原始需求已经明确的信息，不能编造真实公司制度、金额阈值、真实接口或密钥。
            3. 对不明确的信息标注“待确认”。
            4. 强化验收标准，使其可被测试人员直接拆解为测试用例。
            """;

    @Resource
    private LlmGateway llmGateway;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PrdDocumentMapper prdDocumentMapper;

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

    public PrdAgentExecution run(Requirement requirement) {
        RequirementAnalysis analysis = analyze(requirement);
        return run(requirement, analysis);
    }

    public PrdAgentExecution run(Requirement requirement, RequirementAnalysis analysis) {
        List<PrdAgentStep> steps = new ArrayList<>();

        AutonomyAssessment assessment = executeStep(steps, "assess-autonomy", "AutonomousPrdPlanner",
                () -> assessAutonomy(requirement, analysis));
        if (assessment.clarificationRequired()) {
            String clarificationDoc = executeStep(steps, "request-clarification", "ClarificationTool",
                    () -> buildClarificationDocument(requirement, analysis, assessment));
            PrdAgentReview blockedReview = PrdAgentReview.builder()
                    .passed(false)
                    .summary("Agent stopped before generation and requested user clarification.")
                    .issues(assessment.questions())
                    .suggestions(List.of("补充澄清问题后重新触发 PRD Agent。"))
                    .build();
            steps.add(PrdAgentStep.builder()
                    .name("await-user-input")
                    .tool("ClarificationTool")
                    .status("WAITING_USER")
                    .summary(toMarkdownList(assessment.questions()))
                    .elapsedMillis(0)
                    .build());
            steps.add(PrdAgentStep.builder()
                    .name("generate-draft")
                    .tool("PrdDraftGeneratorTool")
                    .status("SKIPPED")
                    .summary("Skipped because critical requirement information is missing.")
                    .elapsedMillis(0)
                    .build());
            String memorySummary = executeStep(steps, "memory-save", "PrdMemoryTool",
                    () -> buildMemorySummary(requirement, assessment, null, blockedReview, true, 0));
            log.info("PRD Agent autonomous flow requested clarification requirementId={}, questions={}",
                    requirement.getId(), assessment.questions().size());
            return PrdAgentExecution.builder()
                    .analysis(analysis)
                    .prdMarkdown(clarificationDoc)
                    .review(blockedReview)
                    .steps(Collections.unmodifiableList(steps))
                    .clarificationRequired(true)
                    .clarificationQuestions(assessment.questions())
                    .memorySummary(memorySummary)
                    .build();
        }

        PrdToolPlan toolPlan = executeStep(steps, "select-tools", "PrdToolPlanner",
                () -> planTools(requirement, analysis));

        String toolContext = executeStep(steps, "call-tools", "PrdToolRegistry",
                () -> executeSelectedTools(requirement, analysis, toolPlan));

        String draft = executeStep(steps, "generate-draft", "PrdDraftGeneratorTool",
                () -> generatePrdDraftDynamic(requirement, analysis, toolContext));

        PrdAgentReview review = executeStep(steps, "review-draft", "PrdReviewTool",
                () -> reviewPrdDynamic(requirement, analysis, draft));

        String finalPrd = draft;
        PrdAgentReview currentReview = review;
        int completedRevisions = 0;
        int maxRevisions = 2;
        while ((!currentReview.passed() || !currentReview.issues().isEmpty()) && completedRevisions < maxRevisions) {
            int revisionNo = completedRevisions + 1;
            String revisionSource = finalPrd;
            PrdAgentReview revisionReview = currentReview;
            finalPrd = executeStep(steps, "revise-draft-" + revisionNo, "PrdRevisionTool",
                    () -> revisePrdDynamic(requirement, analysis, revisionSource, revisionReview, toolContext));
            completedRevisions++;
            String reviewTarget = finalPrd;
            int reviewNo = completedRevisions + 1;
            currentReview = executeStep(steps, "review-draft-" + reviewNo, "PrdReviewTool",
                    () -> reviewPrdDynamic(requirement, analysis, reviewTarget));
        }

        if (completedRevisions == 0) {
            steps.add(PrdAgentStep.builder()
                    .name("revise-draft")
                    .tool("PrdRevisionTool")
                    .status("SKIPPED")
                    .summary("Autonomous review passed; no revision required.")
                    .elapsedMillis(0)
                    .build());
        }

        PrdAgentReview memoryReview = currentReview;
        int memoryRevisionCount = completedRevisions;
        String memorySummary = executeStep(steps, "memory-save", "PrdMemoryTool",
                () -> buildMemorySummary(requirement, assessment, toolPlan, memoryReview, false, memoryRevisionCount));

        log.info("PRD Agent autonomous flow completed requirementId={}, tools={}, revisions={}, steps={}",
                requirement.getId(), toolPlan.toolNames(), completedRevisions, steps.stream().map(PrdAgentStep::name).toList());
        return PrdAgentExecution.builder()
                .analysis(analysis)
                .prdMarkdown(finalPrd)
                .review(currentReview)
                .steps(Collections.unmodifiableList(steps))
                .clarificationRequired(false)
                .clarificationQuestions(assessment.questions())
                .memorySummary(memorySummary)
                .build();
    }

    private AutonomyAssessment assessAutonomy(Requirement requirement, RequirementAnalysis analysis) {
        List<String> questions = new ArrayList<>(extractMarkdownItems(analysis.clarificationQuestions()));
        if (textLength(requirement.getBackground()) < 20) {
            questions.add("请补充业务背景：当前问题、触发原因、涉及用户或组织范围是什么？");
        }
        if (textLength(requirement.getObjective()) < 20) {
            questions.add("请补充业务目标和成功标准：上线后用什么指标判断完成？");
        }
        if (textLength(requirement.getScope()) < 15) {
            questions.add("请补充范围边界：本次必须包含什么，明确不包含什么？");
        }
        if (requirement.getExpectedDate() == null) {
            questions.add("请确认期望交付或验收日期。");
        }
        if (requirement.getMaterialCount() == null || requirement.getMaterialCount() == 0) {
            questions.add("如有流程图、字段清单、原型截图或历史材料，请补充上传。");
        }

        List<String> distinctQuestions = questions.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
        long weakCoreFields = Stream.of(requirement.getBackground(), requirement.getObjective(), requirement.getScope())
                .filter(value -> textLength(value) < 15)
                .count();
        boolean criticalMissing = weakCoreFields >= 2;
        String decision = criticalMissing
                ? "WAITING_USER: critical background/objective/scope information is missing."
                : "CONTINUE: enough information for autonomous PRD generation.";
        return new AutonomyAssessment(criticalMissing, distinctQuestions, decision);
    }

    private String buildClarificationDocument(
            Requirement requirement,
            RequirementAnalysis analysis,
            AutonomyAssessment assessment) {
        return String.join("\n",
                "# " + normalize(requirement.getTitle()) + " PRD",
                "",
                "## Agent 状态",
                "当前需求信息不足，PRD Agent 已暂停正式生成并进入主动澄清状态。",
                "",
                "## 已识别信息",
                analysis.structuredSummary(),
                "",
                "## 缺失信息",
                analysis.missingInfo(),
                "",
                "## 需要需求方补充的问题",
                toMarkdownList(assessment.questions()),
                "",
                "## 下一步",
                "补充以上信息后重新触发 PRD Agent，Agent 将继续执行工具选择、PRD 生成、评审、修订和记忆沉淀。");
    }

    private String buildMemorySummary(
            Requirement requirement,
            AutonomyAssessment assessment,
            PrdToolPlan toolPlan,
            PrdAgentReview review,
            boolean waitingUser,
            int revisionCount) {
        List<String> lines = new ArrayList<>();
        lines.add("requirementId=" + requirement.getId());
        lines.add("decision=" + assessment.decision());
        lines.add("state=" + (waitingUser ? "WAITING_USER" : "COMPLETED"));
        lines.add("revisionCount=" + revisionCount);
        if (toolPlan != null) {
            lines.add("tools=" + String.join(",", toolPlan.toolNames()));
        }
        lines.add("reviewPassed=" + review.passed());
        if (!review.issues().isEmpty()) {
            lines.add("openIssues=" + String.join(" | ", review.issues()));
        }
        if (!assessment.questions().isEmpty()) {
            lines.add("clarificationQuestions=" + String.join(" | ", assessment.questions()));
        }
        return truncate(String.join("; ", lines), 1200);
    }

    private List<String> extractMarkdownItems(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String item = line.replaceFirst("^\\s*[-*]\\s*", "").trim();
            if (StringUtils.hasText(item)
                    && !item.contains("暂无")
                    && !item.contains("无明显")
                    && !item.toLowerCase().contains("none")) {
                items.add(item);
            }
        }
        return items;
    }

    private record AutonomyAssessment(boolean clarificationRequired, List<String> questions, String decision) {
    }

    private PrdToolPlan planTools(Requirement requirement, RequirementAnalysis analysis) {
        Set<String> toolNames = new LinkedHashSet<>();
        List<String> reasons = new ArrayList<>();

        toolNames.add("PRD_TEMPLATE_TOOL");
        reasons.add("需要统一 PRD 章节结构和审批口径");

        toolNames.add("PRD_VALIDATOR_TOOL");
        reasons.add("需要在生成前准备章节完整性和验收标准校验规则");

        if (shouldUseKnowledgeBase(requirement, analysis)) {
            toolNames.add("KNOWLEDGE_BASE_TOOL");
            reasons.add("需求涉及后端、数据库、权限、接口或规范，需要检索团队知识库");
        }

        if (shouldUseHistoryPrd(requirement, analysis)) {
            toolNames.add("HISTORY_PRD_TOOL");
            reasons.add("当前项目已有 PRD 或需求输入偏薄，需要参考历史 PRD 的表达方式和范围拆解");
        }

        return new PrdToolPlan(List.copyOf(toolNames), reasons);
    }

    private boolean shouldUseKnowledgeBase(Requirement requirement, RequirementAnalysis analysis) {
        String text = String.join(" ",
                normalize(requirement.getTitle()),
                normalize(requirement.getBackground()),
                normalize(requirement.getObjective()),
                normalize(requirement.getScope()),
                analysis.structuredSummary(),
                analysis.missingInfo(),
                analysis.clarificationQuestions()).toLowerCase();
        return containsAny(text, "数据库", "表", "接口", "api", "权限", "审批", "后端", "规范", "字段", "流程");
    }

    private boolean shouldUseHistoryPrd(Requirement requirement, RequirementAnalysis analysis) {
        return textLength(requirement.getBackground()) < 80
                || textLength(requirement.getObjective()) < 80
                || textLength(requirement.getScope()) < 60
                || analysis.missingInfo().contains("缺")
                || analysis.missingInfo().contains("补充")
                || hasHistoryPrd(requirement.getProjectId(), requirement.getId());
    }

    private boolean hasHistoryPrd(Long projectId, Long requirementId) {
        try {
            return prdDocumentMapper.selectCount(Wrappers.<PrdDocument>lambdaQuery()
                    .eq(PrdDocument::getProjectId, projectId)
                    .ne(requirementId != null, PrdDocument::getRequirementId, requirementId)) > 0;
        } catch (Exception e) {
            log.warn("PRD Agent history PRD probe failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String executeSelectedTools(Requirement requirement, RequirementAnalysis analysis, PrdToolPlan toolPlan) {
        List<String> contexts = new ArrayList<>();
        for (String toolName : toolPlan.toolNames()) {
            switch (toolName) {
                case "PRD_TEMPLATE_TOOL" -> contexts.add(loadPrdTemplateContext());
                case "PRD_VALIDATOR_TOOL" -> contexts.add(loadValidatorContext());
                case "KNOWLEDGE_BASE_TOOL" -> contexts.add(loadKnowledgeContextDynamic(requirement, analysis));
                case "HISTORY_PRD_TOOL" -> contexts.add(loadHistoryPrdContext(requirement));
                default -> log.warn("PRD Agent ignored unknown tool: {}", toolName);
            }
        }
        contexts.add("## 工具选择原因\n" + String.join("\n", toolPlan.reasons().stream().map(reason -> "- " + reason).toList()));
        return truncate(String.join("\n\n", contexts), 10000);
    }

    private String loadPrdTemplateContext() {
        return String.join("\n",
                "## PRD_TEMPLATE_TOOL",
                "必须输出以下章节：文档信息、业务背景、业务目标、范围与边界、用户角色、核心业务流程、功能清单、页面与交互说明、业务规则、状态流转、异常场景、权限与审批要求、数据与字段要求、非功能需求、验收标准、待澄清问题。",
                "章节内容应服务后续 Prototype Agent、Architecture Agent、Test Agent 使用。");
    }

    private String loadValidatorContext() {
        return String.join("\n",
                "## PRD_VALIDATOR_TOOL",
                "- 每个功能必须能映射到页面、接口或业务规则。",
                "- 验收标准必须可测试，避免只写“体验良好”“性能优秀”等不可验证描述。",
                "- 不明确的公司制度、金额阈值、接口、字段枚举必须标注待确认。",
                "- 涉及审批、权限、数据留痕、异常处理时必须独立说明。");
    }

    private String loadHistoryPrdContext(Requirement requirement) {
        try {
            List<PrdDocument> documents = prdDocumentMapper.selectList(Wrappers.<PrdDocument>lambdaQuery()
                    .eq(PrdDocument::getProjectId, requirement.getProjectId())
                    .ne(PrdDocument::getRequirementId, requirement.getId())
                    .orderByDesc(PrdDocument::getCreatedAt)
                    .last("LIMIT 2"));
            if (documents.isEmpty()) {
                return "## HISTORY_PRD_TOOL\n暂无可参考历史 PRD。";
            }
            List<String> snippets = new ArrayList<>();
            snippets.add("## HISTORY_PRD_TOOL");
            for (PrdDocument document : documents) {
                snippets.add("### " + document.getTitle() + " / " + document.getVersionNo() + "\n"
                        + truncate(document.getContent(), 2200));
            }
            return String.join("\n\n", snippets);
        } catch (Exception e) {
            log.warn("PRD Agent failed to load history PRD: {}", e.getMessage());
            return "## HISTORY_PRD_TOOL\n历史 PRD 读取失败，已跳过。";
        }
    }

    private String selectedToolsSummary(PrdToolPlan plan) {
        return "tools=" + String.join(",", plan.toolNames()) + "; reasons=" + String.join(" | ", plan.reasons());
    }

    private record PrdToolPlan(List<String> toolNames, List<String> reasons) {
    }

    private String generatePrdDraftDynamic(Requirement requirement, RequirementAnalysis analysis, String knowledgeContext) {
        try {
            String llmResponse = llmGateway.chat(LlmChatRequest.builder()
                    .scene("prd-generation")
                    .projectId(requirement.getProjectId())
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .systemPrompt(PRD_SYSTEM_PROMPT)
                    .userPrompt(buildPrdUserPromptDynamic(requirement, analysis, knowledgeContext))
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

    private String buildPrdUserPromptDynamic(Requirement requirement, RequirementAnalysis analysis, String knowledgeContext) {
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
                analysis.clarificationQuestions(),
                "",
                "Agent 已动态选择并调用以下工具，生成 PRD 时必须吸收这些上下文：",
                StringUtils.hasText(knowledgeContext) ? knowledgeContext : "暂无可用工具上下文");
    }

    private String loadKnowledgeContextDynamic(Requirement requirement, RequirementAnalysis analysis) {
        Path knowledgeBase = Path.of("knowledge-base");
        if (!Files.isDirectory(knowledgeBase)) {
            return "";
        }
        String searchText = buildKnowledgeSearchText(requirement, analysis);
        List<String> keywords = extractKnowledgeKeywords(searchText);
        List<String> modules = inferKnowledgeModules(searchText);
        List<KnowledgeSegment> segments = new ArrayList<>();

        try (var files = Files.list(knowledgeBase)) {
            files.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(path -> segments.addAll(readKnowledgeSegments(path)));
        } catch (IOException e) {
            log.warn("PRD Agent failed to load knowledge base: {}", e.getMessage());
        }

        List<KnowledgeSegment> matchedSegments = segments.stream()
                .map(segment -> segment.withScore(scoreKnowledgeSegment(segment, keywords, modules)))
                .filter(segment -> segment.score() > 0)
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .limit(5)
                .toList();

        if (matchedSegments.isEmpty()) {
            matchedSegments = segments.stream().limit(2).toList();
        }

        StringBuilder builder = new StringBuilder("## KNOWLEDGE_BASE_TOOL\n");
        builder.append("检索模块：").append(modules.isEmpty() ? "通用" : String.join("、", modules)).append("\n");
        builder.append("检索关键词：").append(keywords.isEmpty() ? "无显式关键词" : String.join("、", keywords)).append("\n");
        for (KnowledgeSegment segment : matchedSegments) {
            builder.append("\n### ")
                    .append(segment.fileName())
                    .append(" / ")
                    .append(segment.heading())
                    .append(" / score=")
                    .append(segment.score())
                    .append("\n")
                    .append(truncate(segment.content(), 1400));
        }
        return truncate(builder.toString(), 6000);
    }

    private String buildKnowledgeSearchText(Requirement requirement, RequirementAnalysis analysis) {
        return String.join(" ",
                normalize(requirement.getTitle()),
                normalize(requirement.getSourceType()),
                normalize(requirement.getPriority()),
                normalize(requirement.getBackground()),
                normalize(requirement.getObjective()),
                normalize(requirement.getScope()),
                analysis.structuredSummary(),
                analysis.missingInfo(),
                analysis.clarificationQuestions()).toLowerCase();
    }

    private List<String> extractKnowledgeKeywords(String searchText) {
        List<String> candidates = List.of(
                "数据库", "表", "字段", "索引", "mysql",
                "接口", "api", "controller", "service", "mapper",
                "权限", "审批", "角色", "状态", "流程",
                "后端", "spring", "mybatis", "redis", "minio",
                "异常", "审计", "日志", "版本", "冻结",
                "测试", "验收", "性能", "安全");
        List<String> keywords = new ArrayList<>();
        for (String candidate : candidates) {
            if (searchText.contains(candidate.toLowerCase())) {
                keywords.add(candidate);
            }
        }
        return keywords;
    }

    private List<String> inferKnowledgeModules(String searchText) {
        List<String> modules = new ArrayList<>();
        if (containsAny(searchText, "数据库", "表", "字段", "索引", "mysql")) {
            modules.add("数据库设计");
        }
        if (containsAny(searchText, "后端", "接口", "api", "controller", "service", "mapper", "spring")) {
            modules.add("后端工程");
        }
        if (containsAny(searchText, "权限", "审批", "角色", "状态", "流程")) {
            modules.add("业务流程与权限");
        }
        if (containsAny(searchText, "测试", "验收", "异常", "性能", "安全")) {
            modules.add("质量与验收");
        }
        return modules;
    }

    private List<KnowledgeSegment> readKnowledgeSegments(Path path) {
        List<KnowledgeSegment> segments = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String heading = path.getFileName().toString();
            StringBuilder content = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("## ")) {
                    addKnowledgeSegment(segments, path, heading, content);
                    heading = line.replaceFirst("^#+\\s*", "").trim();
                    content = new StringBuilder(line).append("\n");
                } else {
                    content.append(line).append("\n");
                }
            }
            addKnowledgeSegment(segments, path, heading, content);
        } catch (IOException e) {
            log.warn("PRD Agent failed to read knowledge file path={}, message={}", path, e.getMessage());
        }
        return segments;
    }

    private void addKnowledgeSegment(List<KnowledgeSegment> segments, Path path, String heading, StringBuilder content) {
        String text = content.toString().trim();
        if (!StringUtils.hasText(text)) {
            return;
        }
        segments.add(new KnowledgeSegment(path.getFileName().toString(), heading, text, 0));
    }

    private int scoreKnowledgeSegment(KnowledgeSegment segment, List<String> keywords, List<String> modules) {
        String haystack = (segment.fileName() + " " + segment.heading() + " " + segment.content()).toLowerCase();
        int score = 0;
        for (String keyword : keywords) {
            String normalized = keyword.toLowerCase();
            if (segment.fileName().toLowerCase().contains(normalized)) {
                score += 8;
            }
            if (segment.heading().toLowerCase().contains(normalized)) {
                score += 6;
            }
            if (haystack.contains(normalized)) {
                score += 2;
            }
        }
        for (String module : modules) {
            if (haystack.contains(module.toLowerCase())) {
                score += 5;
            }
        }
        return score;
    }

    private record KnowledgeSegment(String fileName, String heading, String content, int score) {
        private KnowledgeSegment withScore(int newScore) {
            return new KnowledgeSegment(fileName, heading, content, newScore);
        }
    }

    private PrdAgentReview reviewPrdDynamic(Requirement requirement, RequirementAnalysis analysis, String prdMarkdown) {
        try {
            String llmResponse = llmGateway.chat(LlmChatRequest.builder()
                    .scene("prd-review")
                    .projectId(requirement.getProjectId())
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .systemPrompt(PRD_REVIEW_SYSTEM_PROMPT)
                    .userPrompt(buildReviewUserPrompt(requirement, analysis, prdMarkdown))
                    .timeoutSeconds(120)
                    .build()).getContent();
            return parseReviewResponse(llmResponse);
        } catch (Exception e) {
            log.warn("PRD Agent review fell back to local rules: {}", e.getMessage());
            return localReviewPrd(prdMarkdown);
        }
    }

    private String revisePrdDynamic(
            Requirement requirement,
            RequirementAnalysis analysis,
            String draft,
            PrdAgentReview review,
            String knowledgeContext) {
        try {
            String llmResponse = llmGateway.chat(LlmChatRequest.builder()
                    .scene("prd-revision")
                    .projectId(requirement.getProjectId())
                    .bizType("requirement")
                    .bizId(requirement.getId())
                    .systemPrompt(PRD_REVISION_SYSTEM_PROMPT)
                    .userPrompt(buildRevisionUserPrompt(requirement, analysis, draft, review, knowledgeContext))
                    .timeoutSeconds(180)
                    .build()).getContent();
            String revised = stripMarkdownFence(llmResponse);
            if (StringUtils.hasText(revised)) {
                return revised;
            }
        } catch (Exception e) {
            log.warn("PRD Agent revision fell back to draft: {}", e.getMessage());
        }
        return draft;
    }

    private String buildReviewUserPrompt(Requirement requirement, RequirementAnalysis analysis, String prdMarkdown) {
        return String.join("\n",
                "请评审以下 PRD：",
                "",
                "需求ID：" + requirement.getId(),
                "需求标题：" + normalize(requirement.getTitle()),
                "",
                "结构化摘要：",
                analysis.structuredSummary(),
                "",
                "缺失信息：",
                analysis.missingInfo(),
                "",
                "待澄清问题：",
                analysis.clarificationQuestions(),
                "",
                "PRD 正文：",
                prdMarkdown);
    }

    private String buildRevisionUserPrompt(
            Requirement requirement,
            RequirementAnalysis analysis,
            String draft,
            PrdAgentReview review,
            String knowledgeContext) {
        return String.join("\n",
                "请修订以下 PRD：",
                "",
                "需求ID：" + requirement.getId(),
                "需求标题：" + normalize(requirement.getTitle()),
                "",
                "结构化摘要：",
                analysis.structuredSummary(),
                "",
                "评审摘要：",
                review.summary(),
                "",
                "评审问题：",
                toMarkdownList(review.issues()),
                "",
                "修订建议：",
                toMarkdownList(review.suggestions()),
                "",
                "知识库与团队规范摘要：",
                StringUtils.hasText(knowledgeContext) ? knowledgeContext : "暂无可用知识库摘要",
                "",
                "PRD 草稿：",
                draft);
    }

    private PrdAgentReview parseReviewResponse(String llmResponse) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeBlock(llmResponse));
            return PrdAgentReview.builder()
                    .passed(root.path("passed").asBoolean(false))
                    .summary(root.path("summary").asText(""))
                    .issues(jsonArrayToList(root.path("issues")))
                    .suggestions(jsonArrayToList(root.path("suggestions")))
                    .build();
        } catch (Exception e) {
            return localReviewPrd(llmResponse);
        }
    }

    private List<String> jsonArrayToList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("");
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        });
        return values;
    }

    private PrdAgentReview localReviewPrd(String prdMarkdown) {
        List<String> issues = new ArrayList<>();
        for (String section : requiredPrdSections()) {
            if (!prdMarkdown.contains(section)) {
                issues.add("缺少章节：" + section);
            }
        }
        if (!prdMarkdown.contains("待确认") && !prdMarkdown.contains("待澄清")) {
            issues.add("未明确标注不确定信息或待澄清问题");
        }
        if (prdMarkdown.length() < 1200) {
            issues.add("PRD 内容偏短，可能不足以支撑后续原型、架构和测试生成");
        }

        boolean passed = issues.isEmpty();
        return PrdAgentReview.builder()
                .passed(passed)
                .summary(passed ? "本地规则检查通过" : "本地规则发现 PRD 仍需补充")
                .issues(issues)
                .suggestions(passed ? List.of() : List.of("补齐缺失章节", "强化业务规则、异常场景和可测试验收标准"))
                .build();
    }

    private List<String> requiredPrdSections() {
        return List.of(
                "## 1. 文档信息",
                "## 2. 业务背景",
                "## 3. 业务目标",
                "## 4. 范围与边界",
                "## 5. 用户角色",
                "## 6. 核心业务流程",
                "## 7. 功能清单",
                "## 8. 页面与交互说明",
                "## 9. 业务规则",
                "## 10. 状态流转",
                "## 11. 异常场景",
                "## 12. 权限与审批要求",
                "## 13. 数据与字段要求",
                "## 14. 非功能需求",
                "## 15. 验收标准",
                "## 16. 待澄清问题");
    }

    private <T> T executeStep(List<PrdAgentStep> steps, String name, String tool, StepAction<T> action) {
        long start = System.currentTimeMillis();
        try {
            T result = action.execute();
            steps.add(PrdAgentStep.builder()
                    .name(name)
                    .tool(tool)
                    .status("SUCCESS")
                    .summary(stepSummary(result))
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .build());
            return result;
        } catch (RuntimeException e) {
            steps.add(PrdAgentStep.builder()
                    .name(name)
                    .tool(tool)
                    .status("FAILED")
                    .summary(e.getMessage())
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .build());
            throw e;
        }
    }

    private String stepSummary(Object result) {
        if (result == null) {
            return "";
        }
        if (result instanceof String text) {
            return "chars=" + text.length();
        }
        if (result instanceof PrdAgentReview review) {
            return review.summary();
        }
        if (result instanceof PrdToolPlan plan) {
            return selectedToolsSummary(plan);
        }
        if (result instanceof AutonomyAssessment assessment) {
            return assessment.decision() + "; clarificationQuestions=" + assessment.questions().size();
        }
        return result.getClass().getSimpleName();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
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

    @FunctionalInterface
    private interface StepAction<T> {
        T execute();
    }

    public record RequirementAnalysis(String structuredSummary, String missingInfo, String clarificationQuestions) {
    }
}
