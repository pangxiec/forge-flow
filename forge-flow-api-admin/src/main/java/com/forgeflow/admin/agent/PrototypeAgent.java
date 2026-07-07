package com.forgeflow.admin.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.dao.domain.PrdDocument;
import com.forgeflow.dao.domain.PrototypeArtifact;
import com.forgeflow.dao.domain.Requirement;
import com.forgeflow.dao.mapper.PrototypeArtifactMapper;
import com.forgeflow.third.llm.LlmChatRequest;
import com.forgeflow.third.llm.LlmGateway;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PrototypeAgent {

    private static final Logger log = LoggerFactory.getLogger(PrototypeAgent.class);
    private static final int MAX_REVISIONS = 2;

    private static final String PROTOTYPE_SYSTEM_PROMPT = """
            You are a senior B-side product designer and frontend prototyping engineer.
            Generate a complete single-file HTML prototype that can be previewed directly in an iframe.
            Requirements:
            1. Output only HTML, starting with <!doctype html> or <html>.
            2. Use inline CSS and local JavaScript only. Do not use external CDN, images, fonts, APIs, or backend requests.
            3. The prototype must look like a real enterprise operation system, not a marketing page or documentation.
            4. Include clickable navigation, list-to-detail flow, form submission state changes, and key workflow actions.
            5. Use business-specific fields, statuses, actions, filters, empty states, and sample data from the PRD.
            6. Make it responsive. Controls and text must not overflow their containers.
            7. Do not write explanatory copy such as "this is a prototype" inside the UI.
            """;

    private static final String PROTOTYPE_REVISION_PROMPT = """
            You are revising a single-file HTML prototype.
            Output only the revised full HTML document.
            Fix all review issues while keeping the original PRD business meaning.
            Do not use external CDN, images, fonts, APIs, or backend requests.
            """;

    @Resource
    private LlmGateway llmGateway;

    @Resource
    private PrototypeArtifactMapper prototypeArtifactMapper;

    public String generatePrototype(Requirement requirement, PrdDocument prdDocument) {
        return run(requirement, prdDocument).html();
    }

    public PrototypeAgentExecution run(Requirement requirement, PrdDocument prdDocument) {
        List<PrdAgentStep> steps = new ArrayList<>();

        PrototypeAutonomyAssessment assessment = executeStep(steps, "assess-autonomy", "PrototypePlanner",
                () -> assessAutonomy(requirement, prdDocument));
        if (assessment.clarificationRequired()) {
            String clarificationHtml = executeStep(steps, "request-clarification", "PrototypeClarificationTool",
                    () -> buildClarificationHtml(requirement, assessment));
            PrototypeAgentReview review = PrototypeAgentReview.builder()
                    .passed(false)
                    .summary("Prototype Agent is waiting for PRD clarification.")
                    .issues(assessment.questions())
                    .suggestions(List.of("补充页面、字段、流程或交互信息后重新生成原型。"))
                    .build();
            steps.add(PrdAgentStep.builder()
                    .name("await-user-input")
                    .tool("PrototypeClarificationTool")
                    .status("WAITING_USER")
                    .summary(toMarkdownList(assessment.questions()))
                    .elapsedMillis(0)
                    .build());
            String memorySummary = executeStep(steps, "memory-save", "PrototypeMemoryTool",
                    () -> buildMemorySummary(requirement, assessment, null, review, true, 0));
            return PrototypeAgentExecution.builder()
                    .html(clarificationHtml)
                    .review(review)
                    .steps(Collections.unmodifiableList(steps))
                    .clarificationRequired(true)
                    .clarificationQuestions(assessment.questions())
                    .memorySummary(memorySummary)
                    .build();
        }

        PrototypeToolPlan toolPlan = executeStep(steps, "select-tools", "PrototypeToolPlanner",
                () -> planTools(requirement, prdDocument, assessment));
        String toolContext = executeStep(steps, "call-tools", "PrototypeToolRegistry",
                () -> executeSelectedTools(requirement, prdDocument, assessment, toolPlan));
        String html = executeStep(steps, "generate-html", "PrototypeHtmlGeneratorTool",
                () -> generateHtml(requirement, prdDocument, assessment, toolContext));
        PrototypeAgentReview review = executeStep(steps, "review-prototype", "PrototypeReviewTool",
                () -> reviewPrototype(html, assessment));

        String finalHtml = html;
        PrototypeAgentReview currentReview = review;
        int completedRevisions = 0;
        while ((!currentReview.passed() || !currentReview.issues().isEmpty()) && completedRevisions < MAX_REVISIONS) {
            int revisionNo = completedRevisions + 1;
            String sourceHtml = finalHtml;
            PrototypeAgentReview sourceReview = currentReview;
            finalHtml = executeStep(steps, "revise-html-" + revisionNo, "PrototypeRevisionTool",
                    () -> reviseHtml(requirement, prdDocument, sourceHtml, sourceReview, toolContext));
            completedRevisions++;
            String reviewTarget = finalHtml;
            int reviewNo = completedRevisions + 1;
            currentReview = executeStep(steps, "review-prototype-" + reviewNo, "PrototypeReviewTool",
                    () -> reviewPrototype(reviewTarget, assessment));
        }

        if (completedRevisions == 0) {
            steps.add(PrdAgentStep.builder()
                    .name("revise-html")
                    .tool("PrototypeRevisionTool")
                    .status("SKIPPED")
                    .summary("Prototype review passed; no revision required.")
                    .elapsedMillis(0)
                    .build());
        }

        PrototypeAgentReview memoryReview = currentReview;
        int memoryRevisionCount = completedRevisions;
        String memorySummary = executeStep(steps, "memory-save", "PrototypeMemoryTool",
                () -> buildMemorySummary(requirement, assessment, toolPlan, memoryReview, false, memoryRevisionCount));

        log.info("Prototype Agent L4 flow completed prdId={}, tools={}, revisions={}, steps={}",
                prdDocument.getId(), toolPlan.toolNames(), completedRevisions, steps.stream().map(PrdAgentStep::name).toList());
        return PrototypeAgentExecution.builder()
                .html(finalHtml)
                .review(currentReview)
                .steps(Collections.unmodifiableList(steps))
                .clarificationRequired(false)
                .clarificationQuestions(assessment.questions())
                .memorySummary(memorySummary)
                .build();
    }

    private PrototypeAutonomyAssessment assessAutonomy(Requirement requirement, PrdDocument prdDocument) {
        String prd = safe(prdDocument.getContent());
        List<String> questions = new ArrayList<>();
        if (prd.length() < 500) {
            questions.add("请补充 PRD 正文，至少包含页面、流程、字段、状态和验收标准。");
        }
        if (!containsAny(prd, "页面", "列表", "详情", "表单", "工作台", "dashboard", "page")) {
            questions.add("请明确需要生成哪些页面，例如工作台、列表页、详情页、新建/编辑页、审批页或报表页。");
        }
        if (!containsAny(prd, "字段", "数据", "表", "金额", "状态", "时间", "用户", "角色")) {
            questions.add("请补充核心字段、状态和列表展示列。");
        }
        if (!containsAny(prd, "点击", "提交", "审批", "通过", "驳回", "流转", "查询", "筛选", "新增", "编辑")) {
            questions.add("请补充关键交互动作，例如查询、进入详情、提交、审批通过、驳回或状态流转。");
        }

        List<String> pagePatterns = inferPagePatterns(prd);
        if (pagePatterns.isEmpty()) {
            pagePatterns = List.of("workspace", "searchable-list", "detail-view", "create-edit-form");
        }
        boolean criticalMissing = prd.length() < 300 || questions.size() >= 3;
        String decision = criticalMissing
                ? "WAITING_USER: PRD lacks enough page, field, or interaction information."
                : "CONTINUE: PRD contains enough information for prototype generation.";
        return new PrototypeAutonomyAssessment(criticalMissing, questions.stream().distinct().limit(6).toList(),
                pagePatterns, decision);
    }

    private PrototypeToolPlan planTools(
            Requirement requirement,
            PrdDocument prdDocument,
            PrototypeAutonomyAssessment assessment) {
        Set<String> toolNames = new LinkedHashSet<>();
        List<String> reasons = new ArrayList<>();

        toolNames.add("PRD_PAGE_EXTRACTOR_TOOL");
        reasons.add("Extract page patterns, roles, fields, statuses, and interaction actions from PRD.");

        toolNames.add("UI_TEMPLATE_TOOL");
        reasons.add("Select reusable B-side page templates based on inferred page patterns.");

        toolNames.add("DESIGN_RULE_TOOL");
        reasons.add("Load local B-side UI, responsive, interaction, and HTML output rules.");

        toolNames.add("INTERACTION_RULE_TOOL");
        reasons.add("Ensure navigation, list-detail, form submission, and workflow actions are clickable.");

        toolNames.add("HTML_VALIDATOR_TOOL");
        reasons.add("Validate single-file HTML, local assets, responsive CSS, and JavaScript interaction.");

        toolNames.add("PROTOTYPE_REVIEW_TOOL");
        reasons.add("Review prototype coverage and trigger autonomous revision when needed.");

        if (hasHistoryPrototype(prdDocument.getProjectId(), prdDocument.getId())) {
            toolNames.add("HISTORY_PROTOTYPE_TOOL");
            reasons.add("Reuse visual and interaction patterns from previous project prototypes.");
        }

        return new PrototypeToolPlan(List.copyOf(toolNames), reasons);
    }

    private String executeSelectedTools(
            Requirement requirement,
            PrdDocument prdDocument,
            PrototypeAutonomyAssessment assessment,
            PrototypeToolPlan toolPlan) {
        List<String> contexts = new ArrayList<>();
        for (String toolName : toolPlan.toolNames()) {
            switch (toolName) {
                case "PRD_PAGE_EXTRACTOR_TOOL" -> contexts.add(extractPrdPageContext(requirement, prdDocument, assessment));
                case "UI_TEMPLATE_TOOL" -> contexts.add(loadTemplateContext(assessment.pagePatterns()));
                case "DESIGN_RULE_TOOL" -> contexts.add(loadRuleContext());
                case "INTERACTION_RULE_TOOL" -> contexts.add(loadNamedDocs(Path.of("prototype-rules"), List.of("interaction-rules.md")));
                case "HTML_VALIDATOR_TOOL" -> contexts.add(loadNamedDocs(Path.of("prototype-rules"), List.of("html-output-rules.md", "responsive-rules.md")));
                case "PROTOTYPE_REVIEW_TOOL" -> contexts.add(loadPrototypeReviewContext());
                case "HISTORY_PROTOTYPE_TOOL" -> contexts.add(loadHistoryPrototypeContext(prdDocument));
                default -> log.warn("Prototype Agent ignored unknown tool: {}", toolName);
            }
        }
        contexts.add("## TOOL_SELECTION_REASONS\n" + toMarkdownList(toolPlan.reasons()));
        return truncate(String.join("\n\n", contexts), 14000);
    }

    private String generateHtml(
            Requirement requirement,
            PrdDocument prdDocument,
            PrototypeAutonomyAssessment assessment,
            String toolContext) {
        try {
            String response = llmGateway.chat(LlmChatRequest.builder()
                    .scene("prototype-generation")
                    .projectId(prdDocument.getProjectId())
                    .bizType("prd_document")
                    .bizId(prdDocument.getId())
                    .systemPrompt(PROTOTYPE_SYSTEM_PROMPT)
                    .userPrompt(buildGenerationPrompt(requirement, prdDocument, assessment, toolContext))
                    .timeoutSeconds(180)
                    .build()).getContent();
            String html = stripHtmlFence(response);
            if (StringUtils.hasText(html) && html.toLowerCase(Locale.ROOT).contains("<html")) {
                return html;
            }
        } catch (Exception e) {
            log.warn("Prototype Agent generation fell back to local HTML: {}", e.getMessage());
        }
        return localGeneratePrototype(requirement, prdDocument, assessment);
    }

    private PrototypeAgentReview reviewPrototype(String html, PrototypeAutonomyAssessment assessment) {
        List<String> issues = new ArrayList<>();
        String lower = safe(html).toLowerCase(Locale.ROOT);
        if (!lower.contains("<html")) {
            issues.add("HTML document is incomplete: missing <html>.");
        }
        if (!lower.contains("<style")) {
            issues.add("Missing inline CSS style block.");
        }
        if (!lower.contains("<script")) {
            issues.add("Missing local JavaScript interactions.");
        }
        if (containsAny(lower, "cdn.", "http://", "https://", "fetch(", "axios", "XMLHttpRequest")) {
            issues.add("Prototype must not use external assets or backend requests.");
        }
        if (!lower.contains("@media")) {
            issues.add("Missing responsive CSS media query.");
        }
        if (!containsAny(lower, "data-page", "addEventListener", "onclick")) {
            issues.add("Missing clickable page switching or interaction handlers.");
        }
        if (!containsAny(lower, "<table", "role=\"table\"", "class=\"list", "grid")) {
            issues.add("Missing list/table style information area.");
        }
        if (!containsAny(lower, "<form", "<input", "<select", "<textarea")) {
            issues.add("Missing form or editable controls.");
        }
        if (assessment.pagePatterns().contains("approval-flow")
                && !containsAny(lower, "approve", "reject", "通过", "驳回", "审批")) {
            issues.add("Approval flow is required but approve/reject actions are missing.");
        }
        if (html.length() < 2500) {
            issues.add("Prototype is too short to cover key B-side pages and interactions.");
        }

        boolean passed = issues.isEmpty();
        return PrototypeAgentReview.builder()
                .passed(passed)
                .summary(passed ? "Prototype local validation passed." : "Prototype local validation found issues.")
                .issues(issues)
                .suggestions(passed ? List.of() : List.of(
                        "Add missing B-side page patterns and controls.",
                        "Ensure all main navigation and workflow actions are clickable.",
                        "Keep output as self-contained responsive HTML."))
                .build();
    }

    private String reviseHtml(
            Requirement requirement,
            PrdDocument prdDocument,
            String html,
            PrototypeAgentReview review,
            String toolContext) {
        try {
            String response = llmGateway.chat(LlmChatRequest.builder()
                    .scene("prototype-revision")
                    .projectId(prdDocument.getProjectId())
                    .bizType("prd_document")
                    .bizId(prdDocument.getId())
                    .systemPrompt(PROTOTYPE_REVISION_PROMPT)
                    .userPrompt(buildRevisionPrompt(requirement, prdDocument, html, review, toolContext))
                    .timeoutSeconds(180)
                    .build()).getContent();
            String revised = stripHtmlFence(response);
            if (StringUtils.hasText(revised) && revised.toLowerCase(Locale.ROOT).contains("<html")) {
                return revised;
            }
        } catch (Exception e) {
            log.warn("Prototype Agent revision fell back to previous HTML: {}", e.getMessage());
        }
        return html;
    }

    private String buildGenerationPrompt(
            Requirement requirement,
            PrdDocument prdDocument,
            PrototypeAutonomyAssessment assessment,
            String toolContext) {
        return String.join("\n",
                "Generate a complete B-side HTML prototype for this PRD.",
                "",
                "Project ID: " + prdDocument.getProjectId(),
                "Requirement ID: " + prdDocument.getRequirementId(),
                "PRD ID: " + prdDocument.getId(),
                "Requirement title: " + safe(requirement.getTitle()),
                "Inferred page patterns: " + String.join(", ", assessment.pagePatterns()),
                "",
                "Business background:",
                safe(requirement.getBackground()),
                "",
                "Business objective:",
                safe(requirement.getObjective()),
                "",
                "Scope:",
                safe(requirement.getScope()),
                "",
                "Tool context:",
                toolContext,
                "",
                "PRD content:",
                safe(prdDocument.getContent()));
    }

    private String buildRevisionPrompt(
            Requirement requirement,
            PrdDocument prdDocument,
            String html,
            PrototypeAgentReview review,
            String toolContext) {
        return String.join("\n",
                "Revise the prototype for requirement: " + safe(requirement.getTitle()),
                "",
                "Review summary: " + review.summary(),
                "",
                "Issues:",
                toMarkdownList(review.issues()),
                "",
                "Suggestions:",
                toMarkdownList(review.suggestions()),
                "",
                "Tool context:",
                toolContext,
                "",
                "PRD content:",
                safe(prdDocument.getContent()),
                "",
                "Current HTML:",
                html);
    }

    private String extractPrdPageContext(
            Requirement requirement,
            PrdDocument prdDocument,
            PrototypeAutonomyAssessment assessment) {
        return String.join("\n",
                "## PRD_PAGE_EXTRACTOR_TOOL",
                "- Requirement title: " + safe(requirement.getTitle()),
                "- Page patterns: " + String.join(", ", assessment.pagePatterns()),
                "- Must derive field names, status labels, filters, actions, and sample rows from the PRD.",
                "- If PRD lacks exact values, mark them as pending confirmation in realistic business UI copy.");
    }

    private String loadTemplateContext(List<String> pagePatterns) {
        List<String> fileNames = new ArrayList<>();
        for (String pattern : pagePatterns) {
            fileNames.add(pattern + ".md");
        }
        return "## UI_TEMPLATE_TOOL\n" + loadNamedDocs(Path.of("prototype-templates"), fileNames);
    }

    private String loadRuleContext() {
        return "## DESIGN_RULE_TOOL\n" + loadAllDocs(Path.of("prototype-rules"));
    }

    private String loadPrototypeReviewContext() {
        return String.join("\n",
                "## PROTOTYPE_REVIEW_TOOL",
                "- Check PRD coverage: pages, roles, flows, fields, statuses, and actions.",
                "- Check interaction coverage: navigation, list detail, form submit, workflow actions.",
                "- Check technical constraints: single-file HTML, inline CSS/JS, no external network dependencies.",
                "- Check visual constraints: professional B-side UI, dense but readable, responsive, no marketing hero.");
    }

    private String loadHistoryPrototypeContext(PrdDocument prdDocument) {
        try {
            List<PrototypeArtifact> artifacts = prototypeArtifactMapper.selectList(Wrappers.<PrototypeArtifact>lambdaQuery()
                    .eq(PrototypeArtifact::getProjectId, prdDocument.getProjectId())
                    .ne(PrototypeArtifact::getPrdId, prdDocument.getId())
                    .orderByDesc(PrototypeArtifact::getCreatedAt)
                    .last("LIMIT 2"));
            if (artifacts.isEmpty()) {
                return "## HISTORY_PROTOTYPE_TOOL\nNo historical prototypes.";
            }
            List<String> snippets = new ArrayList<>();
            snippets.add("## HISTORY_PROTOTYPE_TOOL");
            for (PrototypeArtifact artifact : artifacts) {
                snippets.add("### " + safe(artifact.getTitle()) + " / " + safe(artifact.getVersionNo()) + "\n"
                        + truncate(artifact.getContent(), 2200));
            }
            return String.join("\n\n", snippets);
        } catch (Exception e) {
            log.warn("Prototype Agent failed to load history prototype: {}", e.getMessage());
            return "## HISTORY_PROTOTYPE_TOOL\nHistory prototype loading failed.";
        }
    }

    private String loadAllDocs(Path directory) {
        if (!Files.isDirectory(directory)) {
            return "";
        }
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .map(this::readDoc)
                    .filter(StringUtils::hasText)
                    .reduce("", (left, right) -> left + "\n\n" + right);
        } catch (IOException e) {
            log.warn("Prototype Agent failed to load docs directory={}: {}", directory, e.getMessage());
            return "";
        }
    }

    private String loadNamedDocs(Path directory, List<String> fileNames) {
        List<String> docs = new ArrayList<>();
        for (String fileName : fileNames) {
            Path path = directory.resolve(fileName);
            if (Files.isRegularFile(path)) {
                docs.add(readDoc(path));
            }
        }
        return String.join("\n\n", docs);
    }

    private String readDoc(Path path) {
        try {
            return "# " + path.getFileName() + "\n" + Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Prototype Agent failed to read doc path={}: {}", path, e.getMessage());
            return "";
        }
    }

    private boolean hasHistoryPrototype(Long projectId, Long prdId) {
        try {
            return prototypeArtifactMapper.selectCount(Wrappers.<PrototypeArtifact>lambdaQuery()
                    .eq(PrototypeArtifact::getProjectId, projectId)
                    .ne(prdId != null, PrototypeArtifact::getPrdId, prdId)) > 0;
        } catch (Exception e) {
            log.warn("Prototype Agent history probe failed: {}", e.getMessage());
            return false;
        }
    }

    private List<String> inferPagePatterns(String prd) {
        String text = safe(prd).toLowerCase(Locale.ROOT);
        Set<String> patterns = new LinkedHashSet<>();
        if (containsAny(text, "工作台", "首页", "dashboard", "待办", "指标")) {
            patterns.add("workspace");
        }
        if (containsAny(text, "列表", "查询", "筛选", "分页", "检索", "table")) {
            patterns.add("searchable-list");
        }
        if (containsAny(text, "详情", "查看", "明细", "记录")) {
            patterns.add("detail-view");
        }
        if (containsAny(text, "新增", "创建", "编辑", "提交", "表单", "录入")) {
            patterns.add("create-edit-form");
        }
        if (containsAny(text, "审批", "通过", "驳回", "流转", "审核")) {
            patterns.add("approval-flow");
        }
        if (containsAny(text, "报表", "统计", "趋势", "分析", "看板")) {
            patterns.add("report-dashboard");
        }
        if (containsAny(text, "权限", "角色", "菜单", "授权")) {
            patterns.add("permission-admin");
        }
        return List.copyOf(patterns);
    }

    private String buildClarificationHtml(Requirement requirement, PrototypeAutonomyAssessment assessment) {
        String title = escapeHtml(safe(requirement.getTitle()));
        String items = assessment.questions().stream()
                .map(question -> "<li>" + escapeHtml(question) + "</li>")
                .reduce("", String::concat);
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Prototype clarification</title>
                  <style>
                    body { margin:0; font-family: Arial, "Microsoft YaHei", sans-serif; background:#f6f8fb; color:#172033; }
                    main { max-width: 920px; margin: 0 auto; padding: 32px 18px; }
                    section { background:#fff; border:1px solid #d8e1ec; border-radius:8px; padding:20px; }
                    h1 { margin:0 0 10px; font-size:22px; }
                    p, li { line-height:1.7; }
                    .tag { display:inline-block; padding:4px 9px; border-radius:999px; background:#fef3c7; color:#92400e; font-size:12px; }
                  </style>
                </head>
                <body>
                  <main>
                    <section>
                      <span class="tag">WAITING_USER</span>
                      <h1>__TITLE__ 原型生成需要补充信息</h1>
                      <p>Prototype Agent 已暂停正式生成，请先补充以下信息。</p>
                      <ul>__QUESTIONS__</ul>
                    </section>
                  </main>
                </body>
                </html>
                """.replace("__TITLE__", title).replace("__QUESTIONS__", items);
    }

    private String localGeneratePrototype(
            Requirement requirement,
            PrdDocument prdDocument,
            PrototypeAutonomyAssessment assessment) {
        String title = escapeHtml(safe(requirement.getTitle()));
        String background = escapeHtml(safe(requirement.getBackground()));
        String objective = escapeHtml(safe(requirement.getObjective()));
        boolean approval = assessment.pagePatterns().contains("approval-flow");
        boolean report = assessment.pagePatterns().contains("report-dashboard");
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>__TITLE__ Prototype</title>
                  <style>
                    :root { --bg:#f4f7fb; --panel:#fff; --line:#d8e1ec; --text:#172033; --muted:#66758a; --primary:#2563eb; --ok:#16a34a; --warn:#d97706; --danger:#dc2626; }
                    * { box-sizing:border-box; }
                    body { margin:0; min-height:100vh; font-family:Inter, "Microsoft YaHei", Arial, sans-serif; color:var(--text); background:var(--bg); }
                    button, input, select, textarea { font:inherit; }
                    button { cursor:pointer; }
                    .app { display:grid; grid-template-columns:238px minmax(0,1fr); min-height:100vh; }
                    .side { padding:18px; color:#dbeafe; background:#111827; }
                    .brand { display:flex; gap:10px; align-items:center; margin-bottom:20px; font-weight:800; }
                    .mark { display:grid; width:34px; height:34px; place-items:center; color:#fff; background:var(--primary); border-radius:8px; }
                    .nav { display:grid; gap:6px; }
                    .nav button { min-height:42px; padding:0 12px; text-align:left; color:#cbd5e1; background:transparent; border:0; border-radius:7px; }
                    .nav button.active, .nav button:hover { color:#fff; background:rgba(37,99,235,.28); }
                    main { display:grid; gap:14px; align-content:start; padding:18px; }
                    .page { display:none; gap:14px; align-content:start; }
                    .page.active { display:grid; }
                    .topbar, .panel, .metric { background:var(--panel); border:1px solid var(--line); border-radius:8px; box-shadow:0 10px 24px rgba(15,23,42,.06); }
                    .topbar { display:flex; justify-content:space-between; gap:14px; align-items:center; padding:16px; }
                    h1, h2, h3, p { margin:0; }
                    h1 { font-size:22px; }
                    h2 { font-size:16px; }
                    .muted { color:var(--muted); font-size:13px; line-height:1.55; }
                    .actions { display:flex; flex-wrap:wrap; gap:8px; }
                    .btn { min-height:38px; padding:0 13px; border:1px solid var(--line); border-radius:7px; background:#fff; }
                    .btn.primary { color:#fff; background:var(--primary); border-color:var(--primary); }
                    .btn.ok { color:#fff; background:var(--ok); border-color:var(--ok); }
                    .btn.danger { color:#fff; background:var(--danger); border-color:var(--danger); }
                    .grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; }
                    .metric { padding:14px; text-align:left; }
                    .metric strong { display:block; margin-top:8px; font-size:22px; }
                    .layout { display:grid; grid-template-columns:minmax(0,1fr) 360px; gap:14px; align-items:start; }
                    .panel { padding:16px; }
                    .panel-head { display:flex; justify-content:space-between; gap:12px; align-items:center; margin-bottom:14px; }
                    .filters { display:grid; grid-template-columns:1fr 1fr 130px; gap:10px; margin-bottom:12px; }
                    input, select, textarea { width:100%; min-height:38px; padding:8px 10px; color:var(--text); border:1px solid var(--line); border-radius:7px; background:#fff; }
                    table { width:100%; border-collapse:collapse; font-size:13px; }
                    th, td { padding:11px 10px; text-align:left; border-bottom:1px solid var(--line); vertical-align:top; }
                    th { color:#475569; background:#f8fafc; }
                    tr.clickable:hover { background:#f8fafc; }
                    .tag { display:inline-flex; align-items:center; min-height:24px; padding:0 8px; border-radius:999px; font-size:12px; background:#dbeafe; color:#1d4ed8; }
                    .tag.warn { background:#fef3c7; color:#92400e; }
                    .tag.ok { background:#dcfce7; color:#166534; }
                    .form { display:grid; gap:12px; }
                    .form-row, .detail-grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
                    label span, .field span { display:block; margin-bottom:5px; color:#475569; font-size:12px; font-weight:700; }
                    .field { padding:12px; background:#f8fafc; border:1px solid var(--line); border-radius:8px; }
                    .timeline { display:grid; gap:10px; }
                    .step { padding:10px; border:1px solid var(--line); border-radius:8px; background:#f8fafc; }
                    @media (max-width: 920px) { .app, .layout, .grid, .filters, .form-row, .detail-grid { grid-template-columns:1fr; } .topbar { align-items:flex-start; flex-direction:column; } }
                  </style>
                </head>
                <body>
                  <div class="app">
                    <aside class="side">
                      <div class="brand"><div class="mark">F</div><div>__TITLE__<br><span class="muted">业务工作台</span></div></div>
                      <nav class="nav">
                        <button class="active" data-page="dashboard">工作台</button>
                        <button data-page="list">业务列表</button>
                        <button data-page="create">新建表单</button>
                        <button data-page="detail">详情处理</button>
                        <button data-page="approval">审批流转</button>
                        <button data-page="report">报表看板</button>
                      </nav>
                    </aside>
                    <main>
                      <section id="dashboard" class="page active">
                        <div class="topbar">
                          <div><h1>__TITLE__</h1><p class="muted">__BACKGROUND__</p></div>
                          <div class="actions"><button class="btn" data-page="list">查看列表</button><button class="btn primary" data-page="create">新建记录</button></div>
                        </div>
                        <div class="grid">
                          <button class="metric" data-page="list"><span class="muted">待处理</span><strong>12</strong></button>
                          <button class="metric" data-page="approval"><span class="muted">流转中</span><strong>28</strong></button>
                          <button class="metric" data-page="detail"><span class="muted">异常待确认</span><strong>4</strong></button>
                          <button class="metric" data-page="report"><span class="muted">本月完成</span><strong>96</strong></button>
                        </div>
                        <div class="layout">
                          <div class="panel">
                            <div class="panel-head"><div><h2>最近业务记录</h2><p class="muted">点击行进入详情处理。</p></div><span class="tag">实时状态</span></div>
                            <table>
                              <thead><tr><th>业务名称</th><th>负责人</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>
                              <tbody>
                                <tr class="clickable" data-page="detail"><td>核心流程待确认事项</td><td>产品经理</td><td><span class="tag warn">待审批</span></td><td>2026-07-07 10:30</td><td><button class="btn" data-page="detail">查看</button></td></tr>
                                <tr class="clickable" data-page="detail"><td>字段规则补充记录</td><td>业务负责人</td><td><span class="tag ok">已通过</span></td><td>2026-07-07 11:20</td><td><button class="btn" data-page="detail">查看</button></td></tr>
                              </tbody>
                            </table>
                          </div>
                          <aside class="panel">
                            <div class="panel-head"><h2>流程进度</h2><span class="tag warn">处理中</span></div>
                            <div class="timeline">
                              <div class="step"><strong>提交</strong><p class="muted">录入业务信息和附件。</p></div>
                              <div class="step"><strong>审核</strong><p class="muted">主管确认范围、字段和规则。</p></div>
                              <div class="step"><strong>归档</strong><p class="muted">完成后进入报表统计。</p></div>
                            </div>
                          </aside>
                        </div>
                      </section>
                      <section id="list" class="page">
                        <div class="topbar"><div><h1>业务列表</h1><p class="muted">支持查询、筛选和进入详情。</p></div><button class="btn primary" data-page="create">新建</button></div>
                        <div class="panel"><div class="filters"><input placeholder="搜索标题/负责人"><select><option>全部状态</option><option>待审批</option><option>已通过</option></select><button class="btn primary">筛选</button></div><table><thead><tr><th>名称</th><th>状态</th><th>负责人</th><th>操作</th></tr></thead><tbody><tr class="clickable" data-page="detail"><td>核心流程待确认事项</td><td><span class="tag warn">待审批</span></td><td>产品经理</td><td><button class="btn" data-page="detail">查看</button></td></tr></tbody></table></div>
                      </section>
                      <section id="create" class="page">
                        <div class="topbar"><div><h1>新建表单</h1><p class="muted">__OBJECTIVE__</p></div><button class="btn" data-page="list">返回列表</button></div>
                        <div class="panel form"><div class="form-row"><label><span>业务标题</span><input value="核心流程待确认事项"></label><label><span>优先级</span><select><option>高</option><option>中</option><option>低</option></select></label></div><label><span>业务说明</span><textarea rows="4">根据 PRD 补充真实业务说明。</textarea></label><div class="actions"><button class="btn">保存草稿</button><button class="btn primary" data-page="detail">提交并查看</button></div></div>
                      </section>
                      <section id="detail" class="page">
                        <div class="topbar"><div><h1>详情处理</h1><p class="muted">展示字段、状态、附件和处理记录。</p></div><div class="actions"><button class="btn" data-page="list">返回列表</button><button class="btn primary" data-page="approval">进入审批</button></div></div>
                        <div class="panel"><div class="detail-grid"><div class="field"><span>单据编号</span><strong>FF-20260707-001</strong></div><div class="field"><span>当前状态</span><strong>待审批</strong></div><div class="field"><span>负责人</span><strong>产品经理</strong></div><div class="field"><span>更新时间</span><strong>2026-07-07 11:30</strong></div></div></div>
                      </section>
                      <section id="approval" class="page">
                        <div class="topbar"><div><h1>审批流转</h1><p class="muted">处理通过、驳回和补充说明。</p></div><button class="btn" data-page="detail">返回详情</button></div>
                        <div class="panel form"><label><span>审批意见</span><textarea rows="4">信息完整，同意进入下一节点。</textarea></label><div class="actions"><button class="btn danger" data-page="list">驳回</button><button class="btn ok" data-page="report">审批通过</button></div></div>
                      </section>
                      <section id="report" class="page">
                        <div class="topbar"><div><h1>报表看板</h1><p class="muted">按状态、负责人和时间统计处理效率。</p></div><button class="btn" data-page="dashboard">返回工作台</button></div>
                        <div class="grid"><div class="metric"><span class="muted">总量</span><strong>184</strong></div><div class="metric"><span class="muted">通过率</span><strong>92%</strong></div><div class="metric"><span class="muted">平均耗时</span><strong>1.8天</strong></div><div class="metric"><span class="muted">待补充</span><strong>7</strong></div></div>
                      </section>
                    </main>
                  </div>
                  <script>
                    const buttons = Array.from(document.querySelectorAll('[data-page]'));
                    function showPage(pageId) {
                      document.querySelectorAll('.page').forEach(page => page.classList.toggle('active', page.id === pageId));
                      document.querySelectorAll('.nav button').forEach(btn => btn.classList.toggle('active', btn.dataset.page === pageId));
                      window.scrollTo({ top: 0, behavior: 'smooth' });
                    }
                    buttons.forEach(btn => btn.addEventListener('click', event => { event.stopPropagation(); showPage(btn.dataset.page); }));
                    document.querySelectorAll('tr.clickable').forEach(row => row.addEventListener('click', () => showPage(row.dataset.page)));
                  </script>
                </body>
                </html>
                """
                .replace("__TITLE__", title)
                .replace("__BACKGROUND__", background)
                .replace("__OBJECTIVE__", objective);
    }

    private String buildMemorySummary(
            Requirement requirement,
            PrototypeAutonomyAssessment assessment,
            PrototypeToolPlan toolPlan,
            PrototypeAgentReview review,
            boolean waitingUser,
            int revisionCount) {
        List<String> lines = new ArrayList<>();
        lines.add("requirementId=" + requirement.getId());
        lines.add("decision=" + assessment.decision());
        lines.add("state=" + (waitingUser ? "WAITING_USER" : "COMPLETED"));
        lines.add("pagePatterns=" + String.join(",", assessment.pagePatterns()));
        lines.add("revisionCount=" + revisionCount);
        if (toolPlan != null) {
            lines.add("tools=" + String.join(",", toolPlan.toolNames()));
        }
        lines.add("reviewPassed=" + review.passed());
        if (!review.issues().isEmpty()) {
            lines.add("openIssues=" + String.join(" | ", review.issues()));
        }
        return truncate(String.join("; ", lines), 1200);
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
        if (result instanceof PrototypeAutonomyAssessment assessment) {
            return assessment.decision() + "; pagePatterns=" + String.join(",", assessment.pagePatterns());
        }
        if (result instanceof PrototypeToolPlan plan) {
            return "tools=" + String.join(",", plan.toolNames()) + "; reasons=" + String.join(" | ", plan.reasons());
        }
        if (result instanceof PrototypeAgentReview review) {
            return review.summary();
        }
        return result.getClass().getSimpleName();
    }

    private String stripHtmlFence(String text) {
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
        int start = trimmed.toLowerCase(Locale.ROOT).indexOf("<!doctype html");
        if (start < 0) {
            start = trimmed.toLowerCase(Locale.ROOT).indexOf("<html");
        }
        return start >= 0 ? trimmed.substring(start).trim() : trimmed;
    }

    private boolean containsAny(String text, String... keywords) {
        String source = safe(text).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (source.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String toMarkdownList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- none";
        }
        return String.join("\n", items.stream().map(item -> "- " + item).toList());
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String escapeHtml(String value) {
        return safe(value).replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record PrototypeAutonomyAssessment(
            boolean clarificationRequired,
            List<String> questions,
            List<String> pagePatterns,
            String decision) {
    }

    private record PrototypeToolPlan(List<String> toolNames, List<String> reasons) {
    }

    @FunctionalInterface
    private interface StepAction<T> {
        T execute();
    }
}
