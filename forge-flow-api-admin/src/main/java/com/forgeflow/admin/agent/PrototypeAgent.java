package com.forgeflow.admin.agent;

import com.forgeflow.dao.domain.PrdDocument;
import com.forgeflow.dao.domain.Requirement;
import com.forgeflow.third.llm.LlmChatRequest;
import com.forgeflow.third.llm.LlmGateway;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PrototypeAgent {

    private static final Logger log = LoggerFactory.getLogger(PrototypeAgent.class);

    private static final String PROTOTYPE_SYSTEM_PROMPT = """
            你是一位资深 B 端产品设计师和前端原型工程师。请基于 PRD 生成可直接在浏览器 iframe 中预览的单文件 HTML 前端页面原型。
            输出要求：
            1. 只输出完整 HTML 文档，从 <!doctype html> 开始，不要输出 Markdown、JSON 或代码块标记。
            2. 必须使用内联 CSS 和少量原生 JavaScript，不要引用任何外部 CDN、图片、字体或接口。
            3. 原型要像真实 B 端系统页面，不是文字说明文档；需要包含导航、工作台、列表、表单、详情/审批等关键页面。
            4. 各个页面之间必须可点击切换：左侧/顶部导航能切换页面，列表行或查看按钮能进入详情页，详情页能返回列表，表单提交后能切到审批/详情状态。
            5. 所有交互必须在本地完成，例如页面切换、Tab 切换、列表行选中、审批弹层/详情区域切换；不得请求后端。
            6. 使用真实业务文案、字段、状态、按钮、筛选条件、空状态、错误提示等静态示例数据。
            7. 视觉风格要克制、专业、信息密度适中，适合企业内部管理系统；不要做营销落地页。
            8. CSS 必须响应式，宽屏为左右导航/主内容，小屏自动堆叠；按钮和输入控件不能文字溢出。
            9. 不要在页面中出现“这是原型说明”“如何使用”等解释性文案，直接呈现可评审的产品界面。
            """;

    @Resource
    private LlmGateway llmGateway;

    public String generatePrototype(Requirement requirement, PrdDocument prdDocument) {
        try {
            String response = llmGateway.chat(LlmChatRequest.builder()
                    .scene("prototype-generation")
                    .projectId(prdDocument.getProjectId())
                    .bizType("prd_document")
                    .bizId(prdDocument.getId())
                    .systemPrompt(PROTOTYPE_SYSTEM_PROMPT)
                    .userPrompt(buildUserPrompt(requirement, prdDocument))
                    .timeoutSeconds(180)
                    .build()).getContent();
            String html = stripHtmlFence(response);
            if (StringUtils.hasText(html) && html.toLowerCase().contains("<html")) {
                return html;
            }
        } catch (Exception e) {
            log.warn("Prototype Agent generation fell back to local HTML: {}", e.getMessage());
        }
        return localGeneratePrototype(requirement, prdDocument);
    }

    private String buildUserPrompt(Requirement requirement, PrdDocument prdDocument) {
        return String.join("\n",
                "请基于以下 PRD 生成 HTML 前端页面原型：",
                "",
                "项目ID：" + prdDocument.getProjectId(),
                "需求ID：" + prdDocument.getRequirementId(),
                "PRD ID：" + prdDocument.getId(),
                "需求标题：" + safe(requirement.getTitle()),
                "",
                "业务背景：",
                safe(requirement.getBackground()),
                "",
                "业务目标：",
                safe(requirement.getObjective()),
                "",
                "范围边界：",
                safe(requirement.getScope()),
                "",
                "强制交互要求：",
                "- 所有主要页面必须能通过导航按钮点击切换。",
                "- 工作台的待办、列表页的查看/编辑按钮必须能进入详情页。",
                "- 新建表单的提交按钮必须能切换到详情或审批页面，并展示状态变化。",
                "- 详情页必须提供返回列表、新建申请、审批通过/驳回等可点击动作。",
                "",
                "PRD正文：",
                safe(prdDocument.getContent()));
    }

    private String localGeneratePrototype(Requirement requirement, PrdDocument prdDocument) {
        String title = escapeHtml(safe(requirement.getTitle()));
        String background = escapeHtml(safe(requirement.getBackground()));
        String objective = escapeHtml(safe(requirement.getObjective()));
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>__TITLE__ 原型</title>
                  <style>
                    :root { color-scheme: light; --bg:#eef3f8; --panel:#fff; --line:#d8e1ec; --text:#172033; --muted:#66758a; --primary:#2563eb; --ok:#16a34a; --warn:#d97706; --danger:#dc2626; }
                    * { box-sizing: border-box; }
                    body { margin:0; min-height:100vh; font-family: Inter, "PingFang SC", "Microsoft YaHei", Arial, sans-serif; color:var(--text); background:var(--bg); }
                    button, input, select, textarea { font: inherit; }
                    button { cursor:pointer; }
                    .app { display:grid; grid-template-columns:240px minmax(0,1fr); min-height:100vh; }
                    .side { padding:18px; color:#dbeafe; background:#0f172a; }
                    .brand { display:flex; align-items:center; gap:10px; margin-bottom:22px; font-weight:800; }
                    .mark { display:grid; width:34px; height:34px; place-items:center; color:#fff; background:#2563eb; border-radius:8px; }
                    .nav { display:grid; gap:6px; }
                    .nav button { min-height:42px; padding:0 12px; color:#cbd5e1; text-align:left; background:transparent; border:0; border-radius:8px; }
                    .nav button.active, .nav button:hover { color:#fff; background:rgba(59,130,246,.22); }
                    main { display:grid; gap:14px; align-content:start; padding:18px; }
                    .page { display:none; gap:14px; align-content:start; }
                    .page.active { display:grid; }
                    .topbar, .panel { background:var(--panel); border:1px solid var(--line); border-radius:8px; box-shadow:0 10px 28px rgba(15,23,42,.07); }
                    .topbar { display:flex; gap:14px; align-items:center; justify-content:space-between; padding:16px; }
                    h1, h2, h3, p { margin:0; }
                    h1 { font-size:22px; line-height:1.25; }
                    h2 { font-size:16px; }
                    .muted { color:var(--muted); font-size:13px; line-height:1.55; }
                    .actions { display:flex; flex-wrap:wrap; gap:8px; }
                    .btn { min-height:38px; padding:0 13px; border:1px solid var(--line); border-radius:7px; background:#fff; }
                    .btn.primary { color:#fff; background:var(--primary); border-color:var(--primary); }
                    .btn.ok { color:#fff; background:var(--ok); border-color:var(--ok); }
                    .btn.danger { color:#fff; background:var(--danger); border-color:var(--danger); }
                    .grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; }
                    .metric { padding:14px; background:#fff; border:1px solid var(--line); border-radius:8px; }
                    .metric strong { display:block; margin-top:8px; font-size:22px; }
                    .layout { display:grid; grid-template-columns:minmax(0,1.1fr) 360px; gap:14px; align-items:start; }
                    .panel { padding:16px; }
                    .panel-head { display:flex; justify-content:space-between; gap:12px; align-items:center; margin-bottom:14px; }
                    .filters { display:grid; grid-template-columns:1fr 1fr 140px; gap:10px; margin-bottom:12px; }
                    input, select, textarea { width:100%; min-height:38px; padding:8px 10px; color:var(--text); background:#fff; border:1px solid var(--line); border-radius:7px; }
                    table { width:100%; border-collapse:collapse; font-size:13px; }
                    th, td { padding:11px 10px; text-align:left; border-bottom:1px solid var(--line); vertical-align:top; }
                    th { color:#475569; background:#f8fafc; }
                    tr.clickable:hover { background:#f8fafc; }
                    .tag { display:inline-flex; align-items:center; min-height:24px; padding:0 8px; border-radius:999px; font-size:12px; background:#e0f2fe; color:#075985; }
                    .tag.warn { background:#fef3c7; color:#92400e; }
                    .tag.ok { background:#dcfce7; color:#166534; }
                    .form { display:grid; gap:12px; }
                    .form-row { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
                    label span { display:block; margin-bottom:5px; color:#475569; font-size:12px; font-weight:700; }
                    .timeline { display:grid; gap:10px; margin-top:12px; }
                    .step { display:grid; grid-template-columns:22px minmax(0,1fr); gap:8px; align-items:start; }
                    .dot { width:10px; height:10px; margin-top:5px; background:var(--primary); border-radius:50%; box-shadow:0 0 0 5px #dbeafe; }
                    .detail-grid { display:grid; grid-template-columns:1fr 1fr 1fr; gap:10px; }
                    .field { padding:12px; background:#f8fafc; border:1px solid var(--line); border-radius:8px; }
                    .field span { display:block; margin-bottom:5px; color:var(--muted); font-size:12px; }
                    @media (max-width: 920px) { .app, .layout, .grid, .filters, .form-row, .detail-grid { grid-template-columns:1fr; } .side { position:static; } .topbar { align-items:flex-start; flex-direction:column; } }
                  </style>
                </head>
                <body>
                  <div class="app">
                    <aside class="side">
                      <div class="brand"><div class="mark">F</div><div>__TITLE__<br><span class="muted">业务审批工作台</span></div></div>
                      <nav class="nav">
                        <button class="active" data-page="dashboard">工作台</button>
                        <button data-page="create">新建申请</button>
                        <button data-page="approval">审批中心</button>
                        <button data-page="finance">财务复核</button>
                        <button data-page="report">报表追踪</button>
                      </nav>
                    </aside>
                    <main>
                      <section id="dashboard" class="page active">
                        <div class="topbar">
                          <div><h1>__TITLE__</h1><p class="muted">__BACKGROUND__</p></div>
                          <div class="actions"><button class="btn" data-page="approval">查看待办</button><button class="btn primary" data-page="create">发起申请</button></div>
                        </div>
                        <div class="grid">
                          <button class="metric" data-page="approval"><span class="muted">待我处理</span><strong>12</strong></button>
                          <button class="metric" data-page="approval"><span class="muted">审批中</span><strong>28</strong></button>
                          <button class="metric" data-page="finance"><span class="muted">财务复核</span><strong>9</strong></button>
                          <button class="metric" data-page="report"><span class="muted">本月通过</span><strong>96</strong></button>
                        </div>
                        <div class="layout">
                          <div class="panel">
                            <div class="panel-head"><div><h2>最近申请</h2><p class="muted">点击单据进入详情页</p></div><span class="tag">实时状态</span></div>
                            <table>
                              <thead><tr><th>单据</th><th>申请人</th><th>金额</th><th>状态</th><th>操作</th></tr></thead>
                              <tbody>
                                <tr class="clickable" data-page="detail"><td>差旅报销-上海客户拜访</td><td>张三</td><td>¥ 3,280.00</td><td><span class="tag warn">主管审批</span></td><td><button class="btn" data-page="detail">查看</button></td></tr>
                                <tr class="clickable" data-page="detail"><td>办公用品采购报销</td><td>王五</td><td>¥ 860.00</td><td><span class="tag ok">财务复核</span></td><td><button class="btn" data-page="detail">查看</button></td></tr>
                                <tr class="clickable" data-page="create"><td>团队活动费用报销</td><td>李四</td><td>¥ 1,460.00</td><td><span class="tag">待提交</span></td><td><button class="btn" data-page="create">编辑</button></td></tr>
                              </tbody>
                            </table>
                          </div>
                          <aside class="panel">
                            <div class="panel-head"><h2>流程进度</h2><span class="tag warn">审批中</span></div>
                            <div class="timeline">
                              <div class="step"><div class="dot"></div><div><strong>员工提交</strong><p class="muted">保存基础信息、明细和附件</p></div></div>
                              <div class="step"><div class="dot"></div><div><strong>部门主管审批</strong><p class="muted">通过、驳回或补充说明</p></div></div>
                              <div class="step"><div class="dot"></div><div><strong>财务复核</strong><p class="muted">核对票据和付款状态</p></div></div>
                            </div>
                          </aside>
                        </div>
                      </section>

                      <section id="create" class="page">
                        <div class="topbar"><div><h1>新建报销申请</h1><p class="muted">录入基础信息、费用明细和附件后提交审批。</p></div><button class="btn" data-page="dashboard">返回工作台</button></div>
                        <div class="panel form">
                          <div class="form-row"><label><span>报销标题</span><input value="差旅报销-上海客户拜访"></label><label><span>费用类型</span><select><option>差旅费</option><option>办公费</option><option>培训费</option></select></label></div>
                          <div class="form-row"><label><span>申请金额</span><input value="3280.00"></label><label><span>期望付款日期</span><input value="2026-08-08"></label></div>
                          <label><span>事由说明</span><textarea rows="4">客户现场需求澄清与方案评审产生的交通和住宿费用。</textarea></label>
                          <div class="actions"><button class="btn">上传附件</button><button class="btn">保存草稿</button><button class="btn primary" data-page="detail">提交审批</button></div>
                        </div>
                      </section>

                      <section id="approval" class="page">
                        <div class="topbar"><div><h1>审批中心</h1><p class="muted">集中处理待审批、已驳回和补充材料任务。</p></div><button class="btn primary" data-page="create">新建申请</button></div>
                        <div class="panel">
                          <div class="filters"><input placeholder="搜索申请人/单据"><select><option>待我审批</option><option>已审批</option><option>已驳回</option></select><button class="btn primary">筛选</button></div>
                          <table><thead><tr><th>待办</th><th>节点</th><th>提交时间</th><th>操作</th></tr></thead><tbody>
                            <tr class="clickable" data-page="detail"><td>差旅报销-上海客户拜访</td><td>部门主管审批</td><td>2026-07-01 09:30</td><td><button class="btn" data-page="detail">处理</button></td></tr>
                            <tr class="clickable" data-page="detail"><td>办公用品采购报销</td><td>财务复核</td><td>2026-07-01 11:20</td><td><button class="btn" data-page="detail">查看</button></td></tr>
                          </tbody></table>
                        </div>
                      </section>

                      <section id="detail" class="page">
                        <div class="topbar"><div><h1>报销详情与审批</h1><p class="muted">__OBJECTIVE__</p></div><div class="actions"><button class="btn" data-page="approval">返回列表</button><button class="btn primary" data-page="finance">通过并流转</button></div></div>
                        <div class="panel">
                          <div class="detail-grid">
                            <div class="field"><span>单据编号</span><strong>BX-20260701-0032</strong></div>
                            <div class="field"><span>申请人</span><strong>张三 / 产品部</strong></div>
                            <div class="field"><span>当前状态</span><strong>主管审批中</strong></div>
                            <div class="field"><span>申请金额</span><strong>¥ 3,280.00</strong></div>
                            <div class="field"><span>费用类型</span><strong>差旅费</strong></div>
                            <div class="field"><span>附件</span><strong>3 份票据</strong></div>
                          </div>
                          <div class="form" style="margin-top:14px">
                            <label><span>审批意见</span><textarea rows="4">票据齐全，费用发生原因清晰。</textarea></label>
                            <div class="actions"><button class="btn danger" data-page="approval">驳回修改</button><button class="btn primary" data-page="finance">审批通过</button></div>
                          </div>
                        </div>
                      </section>

                      <section id="finance" class="page">
                        <div class="topbar"><div><h1>财务复核</h1><p class="muted">复核票据、金额和付款状态。</p></div><button class="btn" data-page="approval">返回审批中心</button></div>
                        <div class="panel"><table><thead><tr><th>单据</th><th>金额</th><th>票据状态</th><th>付款状态</th><th>操作</th></tr></thead><tbody>
                          <tr><td>差旅报销-上海客户拜访</td><td>¥ 3,280.00</td><td><span class="tag ok">齐全</span></td><td><span class="tag warn">待付款</span></td><td><button class="btn primary" data-page="report">确认付款</button></td></tr>
                          <tr><td>办公用品采购报销</td><td>¥ 860.00</td><td><span class="tag ok">齐全</span></td><td><span class="tag ok">已付款</span></td><td><button class="btn" data-page="detail">查看</button></td></tr>
                        </tbody></table></div>
                      </section>

                      <section id="report" class="page">
                        <div class="topbar"><div><h1>报表追踪</h1><p class="muted">按部门、状态和时间追踪报销处理效率。</p></div><button class="btn" data-page="dashboard">返回工作台</button></div>
                        <div class="grid"><div class="metric"><span class="muted">本月金额</span><strong>¥ 184,260</strong></div><div class="metric"><span class="muted">平均审批</span><strong>1.8 天</strong></div><div class="metric"><span class="muted">驳回率</span><strong>4.7%</strong></div><div class="metric"><span class="muted">待付款</span><strong>9 单</strong></div></div>
                        <div class="panel"><table><thead><tr><th>部门</th><th>提交数</th><th>通过数</th><th>平均耗时</th></tr></thead><tbody><tr><td>产品部</td><td>24</td><td>22</td><td>1.6 天</td></tr><tr><td>销售部</td><td>38</td><td>35</td><td>2.1 天</td></tr><tr><td>财务部</td><td>12</td><td>12</td><td>0.8 天</td></tr></tbody></table></div>
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
                    buttons.forEach(btn => btn.addEventListener('click', event => {
                      event.stopPropagation();
                      showPage(btn.dataset.page);
                    }));
                    document.querySelectorAll('tr.clickable').forEach(row => row.addEventListener('click', () => showPage(row.dataset.page)));
                  </script>
                </body>
                </html>
                """
                .replace("__TITLE__", title)
                .replace("__BACKGROUND__", background)
                .replace("__OBJECTIVE__", objective);
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
        int start = trimmed.toLowerCase().indexOf("<!doctype html");
        if (start < 0) {
            start = trimmed.toLowerCase().indexOf("<html");
        }
        return start >= 0 ? trimmed.substring(start).trim() : trimmed;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "待确认";
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
