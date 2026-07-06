<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-grid" aria-hidden="true" />
      <div class="brand">
        <div class="brand-mark">
          <span>F</span>
        </div>
        <div>
          <strong>ForgeFlow</strong>
          <span>AI Engineering OS</span>
        </div>
      </div>

      <nav class="nav-list">
        <button
          v-for="item in navItems"
          :key="item.label"
          :aria-current="item.active ? 'page' : undefined"
          :class="['nav-item', { active: item.active }]"
          type="button"
          @click="goTo(item.path)"
        >
          <component :is="item.icon" />
          <span>{{ item.label }}</span>
          <small>{{ item.meta }}</small>
        </button>
      </nav>

      <div class="sidebar-status" aria-label="PRD Agent 状态">
        <span>PRD Agent</span>
        <strong>{{ prdDocument ? '03' : analysisResult ? '02' : '01' }}</strong>
        <el-progress :percentage="agentProgress" :stroke-width="7" :show-text="false" />
        <small>Analyze -> Generate -> Review</small>
      </div>
    </aside>

    <main class="workspace prd-agent-workspace">
      <header class="topbar">
        <div>
          <h1>PRD Agent 工作台</h1>
          <p>查看需求分析结果，生成或再次生成正式 PRD 初稿。</p>
        </div>
        <div class="topbar-actions">
          <el-button :icon="UploadFilled" @click="activeFlowStep = 'upload'">上传需求</el-button>
          <el-button :icon="Refresh" :loading="loadingRequirement || loadingLatest || loadingPrototype" :disabled="!selectedProjectId" @click="refreshLatestResults">
            刷新结果
          </el-button>
          <el-button type="primary" :icon="DocumentChecked" :loading="generating" :disabled="!selectedProjectId" @click="regeneratePrd">
            {{ prdDocument ? '再次生成' : '生成 PRD' }}
          </el-button>
        </div>
      </header>

      <section class="prd-command-panel">
        <div class="prd-command-copy">
          <el-tag effect="dark" type="success">AI Requirement Analysis</el-tag>
          <h2>{{ currentProject?.projectName || '选择项目后查看 PRD Agent 结果' }}</h2>
          <p>{{ currentProject?.description || '系统会使用该项目下最新需求进行分析和 PRD 生成。' }}</p>
        </div>
        <div class="prd-command-form">
          <el-form label-position="top">
            <el-form-item label="所属项目">
              <el-select
                v-model="selectedProjectId"
                placeholder="选择项目"
                filterable
                :loading="projectLoading"
                @change="handleProjectChange"
              >
                <el-option
                  v-for="project in projectOptions"
                  :key="project.id"
                  :label="project.label"
                  :value="project.id"
                />
              </el-select>
            </el-form-item>
            <div class="prd-command-actions">
              <el-button :icon="Search" :loading="analyzing" :disabled="!selectedProjectId" @click="runAnalysis">
                重新分析
              </el-button>
              <el-button type="primary" :icon="DocumentChecked" :loading="generating" :disabled="!selectedProjectId" @click="regeneratePrd">
                生成 PRD
              </el-button>
            </div>
          </el-form>
        </div>
      </section>

      <section class="prd-status-grid">
        <div v-for="item in statusCards" :key="item.label" class="metric-panel prd-status-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </div>
      </section>

      <section class="panel requirement-record-panel">
        <div class="panel-header">
          <div>
            <h2>需求记录</h2>
            <p>{{ requirementRecord ? `${requirementRecord.title} / ${requirementRecord.versionNo}` : '选择项目后展示最新上传的需求记录。' }}</p>
          </div>
          <div class="requirement-record-tools">
            <el-tag :type="requirementRecord ? 'success' : 'info'" effect="light">
              {{ requirementRecord?.status || '暂无需求' }}
            </el-tag>
            <el-button :icon="Refresh" :loading="loadingRequirement" :disabled="!selectedProjectId" @click="loadLatestRequirement">
              刷新
            </el-button>
          </div>
        </div>

        <el-skeleton v-if="loadingRequirement" :rows="4" animated />
        <el-empty v-else-if="!requirementRecord" description="暂无需求记录" :image-size="82">
          <el-button type="primary" @click="activeFlowStep = 'upload'">上传需求</el-button>
        </el-empty>
        <div v-else class="requirement-record-content">
          <div class="requirement-record-meta">
            <div v-for="item in requirementMeta" :key="item.label" class="requirement-record-chip">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
          <div class="requirement-record-sections">
            <article>
              <strong>业务背景</strong>
              <p>{{ requirementRecord.background }}</p>
            </article>
            <article>
              <strong>目标与成功标准</strong>
              <p>{{ requirementRecord.objective }}</p>
            </article>
            <article>
              <strong>范围与边界</strong>
              <p>{{ requirementRecord.scope }}</p>
            </article>
          </div>
        </div>
      </section>

      <section v-if="agentBusy" class="agent-waiting-panel" aria-live="polite">
        <div class="agent-waiting-icon">
          <el-icon><Loading /></el-icon>
        </div>
        <div>
          <strong>{{ waitTitle }}</strong>
          <span>{{ waitDescription }}</span>
        </div>
        <div class="agent-waiting-time">
          <span>已等待</span>
          <strong>{{ waitingSeconds }}s</strong>
        </div>
      </section>

      <section class="panel agent-trace-panel">
        <div class="panel-header">
          <div>
            <h2>Agent 执行轨迹</h2>
            <p>{{ prdDocument?.taskId ? `Task ${prdDocument.taskId}` : '生成 PRD 后展示 Agent 多步执行过程。' }}</p>
          </div>
          <div class="agent-trace-tools">
            <el-tag :type="agentTraceSteps.length ? 'success' : 'info'" effect="light">
              {{ agentTraceSteps.length ? `${agentTraceSteps.length} steps` : '暂无轨迹' }}
            </el-tag>
            <el-button :icon="Refresh" :loading="loadingAgentTrace" :disabled="!prdDocument?.taskId" @click="loadAgentTrace">
              刷新
            </el-button>
          </div>
        </div>

        <el-skeleton v-if="loadingAgentTrace" :rows="4" animated />
        <el-empty v-else-if="!agentTraceSteps.length" description="暂无 Agent 执行轨迹" :image-size="82" />
        <div v-else class="agent-trace-list">
          <article v-for="step in agentTraceSteps" :key="step.id" class="agent-trace-step">
            <div class="agent-trace-index">{{ step.stepOrder }}</div>
            <div class="agent-trace-body">
              <div class="agent-trace-title">
                <strong>{{ step.stepName }}</strong>
                <span>{{ step.toolName }}</span>
              </div>
              <p>{{ step.summary || '-' }}</p>
            </div>
            <div class="agent-trace-state">
              <el-tag :type="step.status === 'SUCCESS' ? 'success' : step.status === 'SKIPPED' ? 'info' : 'danger'" effect="light">
                {{ step.status }}
              </el-tag>
              <small>{{ formatElapsed(step.elapsedMillis) }}</small>
            </div>
          </article>
        </div>
      </section>

      <section class="prd-flow-stepper" aria-label="PRD Agent 流程步骤">
        <button
          v-for="(step, index) in flowSteps"
          :key="step.key"
          type="button"
          :class="[
            'prd-flow-step',
            {
              active: step.panel === activeFlowStep,
              done: step.done,
              locked: !step.unlocked,
            },
          ]"
          :aria-current="step.panel === activeFlowStep ? 'step' : undefined"
          :disabled="!step.unlocked"
          @click="handleFlowStepClick(step)"
        >
          <span class="prd-flow-index">{{ step.done ? 'OK' : index + 1 }}</span>
          <span class="prd-flow-copy">
            <strong>{{ step.title }}</strong>
            <small>{{ step.hint }}</small>
          </span>
        </button>
      </section>

      <section v-show="activeFlowStep === 'upload'" class="prd-upload-step">
        <div class="panel upload-form-panel">
          <div class="panel-header">
            <div>
              <h2>产品需求上传</h2>
              <p>在当前工作台内提交需求，提交成功后继续进入需求分析步骤。</p>
            </div>
            <el-tag :type="uploadForm.sensitiveMasked ? 'success' : 'warning'" effect="light">
              {{ uploadForm.sensitiveMasked ? '已确认脱敏' : '待脱敏确认' }}
            </el-tag>
          </div>

          <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-position="top" class="requirement-form">
            <div class="form-grid two-columns">
              <el-form-item label="所属项目" prop="projectId">
                <el-select
                  v-model="uploadForm.projectId"
                  placeholder="选择项目"
                  filterable
                  :loading="projectLoading"
                  :disabled="projectLoading"
                  @change="handleUploadProjectChange"
                >
                  <el-option
                    v-for="project in projectOptions"
                    :key="project.id"
                    :label="project.label"
                    :value="project.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="需求标题" prop="title">
                <el-input v-model="uploadForm.title" maxlength="60" show-word-limit placeholder="例如：企业费用报销审批系统" />
              </el-form-item>
            </div>

            <div class="form-grid three-columns">
              <el-form-item label="来源类型" prop="sourceType">
                <el-segmented v-model="uploadForm.sourceType" :options="sourceTypes" />
              </el-form-item>
              <el-form-item label="优先级" prop="priority">
                <el-select v-model="uploadForm.priority" placeholder="选择优先级">
                  <el-option label="高" value="HIGH" />
                  <el-option label="中" value="MEDIUM" />
                  <el-option label="低" value="LOW" />
                </el-select>
              </el-form-item>
              <el-form-item label="期望完成日期">
                <el-date-picker v-model="uploadForm.expectedDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
              </el-form-item>
            </div>

            <div class="form-grid two-columns">
              <el-form-item label="需求方" prop="requester">
                <el-input v-model="uploadForm.requester" placeholder="业务部门 / 需求联系人" />
              </el-form-item>
              <el-form-item label="产品负责人" prop="productOwner">
                <el-input v-model="uploadForm.productOwner" placeholder="产品经理姓名" />
              </el-form-item>
            </div>

            <el-form-item label="业务背景" prop="background">
              <el-input v-model="uploadForm.background" type="textarea" :rows="4" maxlength="800" show-word-limit />
            </el-form-item>
            <el-form-item label="目标与成功标准" prop="objective">
              <el-input v-model="uploadForm.objective" type="textarea" :rows="4" maxlength="800" show-word-limit />
            </el-form-item>
            <el-form-item label="范围与边界" prop="scope">
              <el-input v-model="uploadForm.scope" type="textarea" :rows="3" maxlength="600" show-word-limit />
            </el-form-item>

            <div class="upload-drop-section">
              <label class="upload-label">需求材料</label>
              <el-upload
                v-model:file-list="uploadFileList"
                class="requirement-uploader"
                drag
                multiple
                :auto-upload="false"
                :limit="8"
                :on-exceed="handleUploadExceed"
                :before-remove="confirmUploadRemove"
              >
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="el-upload__text">拖拽文件到这里，或 <em>点击选择</em></div>
                <template #tip>
                  <div class="el-upload__tip">支持 PRD 草稿、会议纪要、流程图、截图等材料，单次最多 8 个文件。</div>
                </template>
              </el-upload>
            </div>

            <div class="sensitive-row">
              <div>
                <strong>敏感信息处理</strong>
                <span>提交前确认已移除账号、密钥、客户身份信息和生产数据。</span>
              </div>
              <el-switch v-model="uploadForm.sensitiveMasked" active-text="已脱敏" inactive-text="待确认" />
            </div>

            <div class="prd-upload-actions">
              <el-button :icon="Refresh" @click="resetUploadForm">重置</el-button>
              <el-button type="primary" :icon="UploadFilled" :loading="submittingUpload" :disabled="!canSubmitUpload" @click="submitRequirementInFlow">
                提交并分析
              </el-button>
            </div>
          </el-form>
        </div>
      </section>

      <section v-show="activeFlowStep === 'analysis'" class="prd-agent-layout is-single">
        <div class="panel analysis-panel">
          <div class="panel-header">
            <div>
              <h2>需求分析结果</h2>
              <p>{{ analysisResult ? formatDateTime(analysisResult.analyzedAt) : '选择项目后可重新触发最新需求分析。' }}</p>
            </div>
            <div class="analysis-panel-tools">
              <el-segmented v-model="analysisViewMode" :options="viewModeOptions" />
              <el-tag :type="analysisResult ? 'success' : 'info'" effect="light">
                {{ analysisResult?.status || '待分析' }}
              </el-tag>
            </div>
          </div>

          <el-skeleton v-if="analyzing" :rows="8" animated />
          <el-empty v-else-if="!analysisResult" description="暂无分析结果" :image-size="90">
            <el-button type="primary" :disabled="!selectedProjectId" @click="runAnalysis">开始分析</el-button>
          </el-empty>
          <div v-else class="analysis-stack">
            <article class="analysis-block">
              <div class="analysis-block-title">
                <el-icon><Memo /></el-icon>
                <strong>结构化摘要</strong>
              </div>
              <div v-if="analysisViewMode === 'preview'" class="markdown-render compact" v-html="renderMarkdownHtml(analysisResult.structuredSummary)" />
              <pre v-else>{{ analysisResult.structuredSummary }}</pre>
            </article>
            <article class="analysis-block warning">
              <div class="analysis-block-title">
                <el-icon><Warning /></el-icon>
                <strong>缺失信息</strong>
              </div>
              <div v-if="analysisViewMode === 'preview'" class="markdown-render compact" v-html="renderMarkdownHtml(analysisResult.missingInfo)" />
              <pre v-else>{{ analysisResult.missingInfo }}</pre>
            </article>
            <article class="analysis-block">
              <div class="analysis-block-title">
                <el-icon><QuestionFilled /></el-icon>
                <strong>澄清问题</strong>
              </div>
              <div v-if="analysisViewMode === 'preview'" class="markdown-render compact" v-html="renderMarkdownHtml(analysisResult.clarificationQuestions)" />
              <pre v-else>{{ analysisResult.clarificationQuestions }}</pre>
            </article>
          </div>
        </div>
      </section>

      <section v-show="activeFlowStep === 'prd'" class="prd-agent-layout is-single">
        <div class="panel prd-preview-panel">
          <div class="panel-header">
            <div>
              <h2>PRD 预览</h2>
              <p>{{ prdDocument ? `${prdDocument.title} / ${prdDocument.versionNo}` : '生成后将在这里展示 Markdown 正文。' }}</p>
            </div>
            <div class="prd-preview-tools">
              <el-segmented v-model="prdViewMode" :options="viewModeOptions" />
              <el-tag :type="prdDocument ? 'warning' : 'info'" effect="light">
                {{ prdDocument?.status || '待生成' }}
              </el-tag>
              <el-button :loading="confirmingPrd" :disabled="!prdDocument || prdDocument.status === 'PRD_CONFIRMED'" @click="confirmCurrentPrd">
                确认 PRD
              </el-button>
              <el-button
                type="primary"
                :loading="generatingPrototype"
                :disabled="!prdDocument || prdDocument.status !== 'PRD_CONFIRMED'"
                @click="generateCurrentPrototype"
              >
                生成原型
              </el-button>
              <el-button :icon="CopyDocument" :disabled="!prdDocument" @click="copyPrd">复制</el-button>
            </div>
          </div>

          <el-skeleton v-if="generating || loadingLatest" :rows="12" animated />
          <el-empty v-else-if="!prdDocument" description="暂无 PRD 文档" :image-size="110">
            <el-button type="primary" :disabled="!selectedProjectId" @click="regeneratePrd">生成 PRD</el-button>
          </el-empty>
          <article v-else-if="prdViewMode === 'preview'" class="prd-markdown-preview rendered" v-html="renderMarkdownHtml(prdDocument.content)" />
          <article v-else class="prd-markdown-preview">
            <pre>{{ prdDocument.content }}</pre>
          </article>
        </div>
      </section>

      <section v-show="activeFlowStep === 'prototype'" class="panel prototype-preview-panel">
        <div class="panel-header">
          <div>
            <h2>页面原型</h2>
            <p>{{ prototypeArtifact ? `${prototypeArtifact.title} / ${prototypeArtifact.versionNo}` : '确认 PRD 后生成可直接预览的前端页面原型。' }}</p>
          </div>
          <div class="prd-preview-tools">
            <el-segmented v-model="prototypeViewMode" :options="viewModeOptions" />
            <el-tag :type="prototypeArtifact ? 'success' : 'info'" effect="light">
              {{ prototypeArtifact?.status || '待生成' }}
            </el-tag>
            <el-button :icon="Refresh" :loading="loadingPrototype" :disabled="!selectedProjectId" @click="loadLatestPrototype">
              刷新
            </el-button>
          </div>
        </div>

        <el-skeleton v-if="generatingPrototype || loadingPrototype" :rows="10" animated />
        <el-empty v-else-if="!prototypeArtifact" description="暂无页面原型" :image-size="100">
          <el-button
            type="primary"
            :disabled="!prdDocument || prdDocument.status !== 'PRD_CONFIRMED'"
            @click="generateCurrentPrototype"
          >
            生成原型
          </el-button>
        </el-empty>
        <div v-else-if="prototypeViewMode === 'preview' && isHtmlPrototype" class="prototype-frame-shell">
          <iframe
            class="prototype-frame"
            title="页面原型预览"
            sandbox="allow-scripts"
            :srcdoc="prototypeFrameHtml"
          />
        </div>
        <div v-else-if="prototypeViewMode === 'preview'" class="prototype-legacy-panel">
          <strong>当前原型不是 HTML 页面</strong>
          <span>检测到旧版文字原型或模型返回了非 HTML 内容，请重新生成页面原型。</span>
          <el-button
            type="primary"
            :loading="generatingPrototype"
            :disabled="!prdDocument || prdDocument.status !== 'PRD_CONFIRMED'"
            @click="generateCurrentPrototype"
          >
            重新生成页面原型
          </el-button>
        </div>
        <article v-else class="prd-markdown-preview">
          <pre>{{ prototypeArtifact.content }}</pre>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadProps, type UploadUserFile } from 'element-plus'
import { marked } from 'marked'
import {
  Connection,
  CopyDocument,
  Document,
  DocumentChecked,
  Grid,
  Loading,
  Memo,
  Monitor,
  Notebook,
  QuestionFilled,
  Refresh,
  Search,
  Tickets,
  UploadFilled,
  Warning,
} from '@element-plus/icons-vue'
import {
  analyzeRequirement,
  confirmPrd,
  generatePrd,
  generatePrototype,
  getGenerationTaskSteps,
  getLatestAnalysis,
  getLatestPrd,
  getLatestPrototype,
  getLatestRequirement,
  getRequirementDetail,
  uploadRequirement,
  type GenerationTaskStepResult,
  type PrdDocumentResult,
  type PrototypeArtifactResult,
  type RequirementAnalysisResult,
  type RequirementDetailResult,
} from '@/api/requirement'
import { listProjects } from '@/api/project'
import type { BackendProject } from '@/types/project'

const route = useRoute()
const router = useRouter()

const projectLoading = ref(false)
const submittingUpload = ref(false)
const analyzing = ref(false)
const generating = ref(false)
const confirmingPrd = ref(false)
const generatingPrototype = ref(false)
const loadingLatest = ref(false)
const loadingPrototype = ref(false)
const loadingAgentTrace = ref(false)
const waitingSeconds = ref(0)
let waitingTimer: number | undefined
const projects = ref<BackendProject[]>([])
const selectedProjectId = ref<string>()
const selectedRequirementId = ref<string>()
const uploadFormRef = ref<FormInstance>()
const uploadFileList = ref<UploadUserFile[]>([])
const requirementRecord = ref<RequirementDetailResult>()
const analysisResult = ref<RequirementAnalysisResult>()
const prdDocument = ref<PrdDocumentResult>()
const prototypeArtifact = ref<PrototypeArtifactResult>()
const agentTraceSteps = ref<GenerationTaskStepResult[]>([])
const loadingRequirement = ref(false)
const analysisViewMode = ref('preview')
const prdViewMode = ref('preview')
const prototypeViewMode = ref('preview')
type FlowPanel = 'upload' | 'analysis' | 'prd' | 'prototype'
type FlowStepKey = 'upload' | 'analysis' | 'prd' | 'prdReview' | 'prototype'

type FlowStep = {
  key: FlowStepKey
  title: string
  hint: string
  panel: FlowPanel
  done: boolean
  unlocked: boolean
}

const activeFlowStep = ref<FlowPanel>('analysis')
const sourceTypes = ['文本', '文档', '图片', '会议纪要']

const uploadForm = reactive({
  projectId: undefined as string | undefined,
  title: '',
  sourceType: '文本',
  priority: 'MEDIUM',
  requester: '',
  productOwner: '',
  expectedDate: '',
  background: '',
  objective: '',
  scope: '',
  sensitiveMasked: true,
})

const uploadRules: FormRules = {
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
  title: [{ required: true, message: '请填写需求标题', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  requester: [{ required: true, message: '请填写需求方', trigger: 'blur' }],
  productOwner: [{ required: true, message: '请填写产品负责人', trigger: 'blur' }],
  background: [{ required: true, message: '请填写业务背景', trigger: 'blur' }],
  objective: [{ required: true, message: '请填写目标与成功标准', trigger: 'blur' }],
  scope: [{ required: true, message: '请填写范围与边界', trigger: 'blur' }],
}
const viewModeOptions = [
  { label: '预览', value: 'preview' },
  { label: '原文', value: 'raw' },
]

const navItems = [
  { label: '项目工作台', meta: 'Command', icon: Grid, active: false, path: '/' },
  { label: '需求与 PRD', meta: 'Spec Flow', icon: Document, active: true, path: '/requirements/prd-agent' },
  { label: '原型与架构', meta: 'Design Gate', icon: Monitor, active: false, path: '/requirements/prd-agent' },
  { label: 'Git 交付', meta: 'Gitea Sync', icon: Connection, active: false, path: '/requirements/prd-agent' },
  { label: '知识库', meta: 'Standards', icon: Notebook, active: false, path: '/requirements/prd-agent' },
  { label: '审计日志', meta: 'Trace Log', icon: Tickets, active: false, path: '/requirements/prd-agent' },
]

const projectOptions = computed(() =>
  projects.value.map((project) => ({
    ...project,
    label: `${project.projectName} / ${project.projectCode}`,
  })),
)

const currentProject = computed(() => projects.value.find((project) => project.id === selectedProjectId.value))

const requirementMeta = computed(() => {
  const record = requirementRecord.value
  if (!record) {
    return []
  }
  return [
    { label: '需求ID', value: record.requirementId },
    { label: '来源类型', value: record.sourceType || '-' },
    { label: '优先级', value: record.priority || '-' },
    { label: '需求方', value: record.requester || '-' },
    { label: '产品负责人', value: record.productOwner || '-' },
    { label: '期望日期', value: record.expectedDate || '待确认' },
    { label: '材料数量', value: `${record.materialCount || 0}` },
    { label: '上传时间', value: formatDateTime(record.createdAt) },
  ]
})

const canSubmitUpload = computed(
  () =>
    Boolean(
      uploadForm.projectId &&
        uploadForm.title.trim() &&
        uploadForm.requester.trim() &&
        uploadForm.productOwner.trim() &&
        uploadForm.background.trim() &&
        uploadForm.objective.trim() &&
        uploadForm.scope.trim() &&
        uploadForm.sensitiveMasked,
    ),
)

const isHtmlPrototype = computed(() => {
  const content = prototypeArtifact.value?.content?.trim().toLowerCase() || ''
  return prototypeArtifact.value?.prototypeType === 'HTML_PROTOTYPE' && (content.startsWith('<!doctype html') || content.includes('<html'))
})

const prototypeFrameHtml = computed(() => (isHtmlPrototype.value ? prototypeArtifact.value?.content || '' : ''))

const flowSteps = computed<FlowStep[]>(() => {
  const hasRequirement = Boolean(requirementRecord.value || selectedRequirementId.value || analysisResult.value || prdDocument.value)
  const hasAnalysis = Boolean(analysisResult.value)
  const hasPrd = Boolean(prdDocument.value)
  const prdConfirmed = prdDocument.value?.status === 'PRD_CONFIRMED'
  const hasPrototype = Boolean(prototypeArtifact.value)

  return [
    {
      key: 'upload',
      title: '需求上传',
      hint: hasRequirement ? '已接入需求' : '填写需求输入',
      panel: 'upload',
      done: hasRequirement,
      unlocked: true,
    },
    {
      key: 'analysis',
      title: '需求分析',
      hint: hasAnalysis ? '可回看分析结果' : '等待分析',
      panel: 'analysis',
      done: hasAnalysis,
      unlocked: Boolean(selectedProjectId.value),
    },
    {
      key: 'prd',
      title: 'PRD 生成',
      hint: hasPrd ? '可预览文档' : '分析后生成',
      panel: 'prd',
      done: hasPrd,
      unlocked: hasAnalysis || hasPrd,
    },
    {
      key: 'prdReview',
      title: 'PRD 确认',
      hint: prdConfirmed ? '已确认' : '等待审批',
      panel: 'prd',
      done: prdConfirmed,
      unlocked: hasPrd,
    },
    {
      key: 'prototype',
      title: '页面原型',
      hint: hasPrototype ? '可预览页面' : '确认 PRD 后生成',
      panel: 'prototype',
      done: hasPrototype,
      unlocked: prdConfirmed || hasPrototype,
    },
  ]
})

const agentProgress = computed(() => {
  if (prototypeArtifact.value) {
    return 92
  }
  if (prdDocument.value) {
    return 78
  }
  if (analysisResult.value) {
    return 46
  }
  return 18
})

const agentBusy = computed(() => analyzing.value || generating.value || generatingPrototype.value)

const waitTitle = computed(() => {
  if (generatingPrototype.value) {
    return '原型正在生成'
  }
  return generating.value ? 'PRD 正在生成' : '需求正在分析'
})

const waitDescription = computed(() =>
  generatingPrototype.value
    ? '大模型正在根据已确认 PRD 梳理页面清单、关键布局、字段组件和交互规则，请稍等。'
    :
  generating.value
    ? '大模型正在整理业务流程、功能清单、规则和验收标准，复杂需求可能需要一两分钟。'
    : '大模型正在读取需求背景、目标和范围，并生成摘要、缺失信息与澄清问题。',
)

const statusCards = computed(() => [
  {
    label: '当前项目',
    value: currentProject.value?.projectCode || '-',
    hint: currentProject.value?.projectName || '未选择项目',
  },
  {
    label: '需求分析',
    value: analysisResult.value ? '已完成' : '待触发',
    hint: analysisResult.value
      ? analysisResult.value.taskId
        ? `Task ${analysisResult.value.taskId}`
        : formatDateTime(analysisResult.value.analyzedAt)
      : '使用最新需求输入',
  },
  {
    label: 'PRD 版本',
    value: prdDocument.value?.versionNo || '-',
    hint: prdDocument.value ? formatDateTime(prdDocument.value.updatedAt || prdDocument.value.createdAt) : '尚未生成',
  },
  {
    label: '审批状态',
    value: prdDocument.value?.status || currentProject.value?.currentStage || '-',
    hint: '生成后进入 PRD 审批',
  },
])

onMounted(async () => {
  hydrateRouteParams()
  await loadProjects()
  if (selectedProjectId.value) {
    await loadRequirementRecord(false)
    await loadLatestAnalysis(false)
    await loadLatestPrd(false)
    await loadLatestPrototype(false)
    syncActiveFlowStep()
  }
})

onUnmounted(() => {
  stopWaitingTimer()
})

function handleFlowStepClick(step: FlowStep) {
  if (!step.unlocked) {
    ElMessage.info('请先完成前置步骤')
    return
  }
  activeFlowStep.value = step.panel
}

function syncActiveFlowStep() {
  if (prototypeArtifact.value) {
    activeFlowStep.value = 'prototype'
    return
  }
  if (prdDocument.value) {
    activeFlowStep.value = 'prd'
    return
  }
  activeFlowStep.value = 'analysis'
}

function hydrateRouteParams() {
  const projectId = firstQueryValue(route.query.projectId)
  const requirementId = firstQueryValue(route.query.requirementId)
  if (projectId) {
    selectedProjectId.value = projectId
  }
  if (requirementId) {
    selectedRequirementId.value = requirementId
  }
}

function firstQueryValue(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : undefined
  }
  return typeof value === 'string' ? value : undefined
}

async function loadProjects() {
  projectLoading.value = true
  try {
    const data = await listProjects()
    projects.value = data
    if (!selectedProjectId.value && data.length > 0) {
      selectedProjectId.value = data[0].id
    }
    uploadForm.projectId = selectedProjectId.value
  } catch {
    ElMessage.warning('项目列表加载失败，请确认后端服务已启动')
  } finally {
    projectLoading.value = false
  }
}

async function handleProjectChange() {
  uploadForm.projectId = selectedProjectId.value
  selectedRequirementId.value = undefined
  requirementRecord.value = undefined
  analysisResult.value = undefined
  prdDocument.value = undefined
  prototypeArtifact.value = undefined
  agentTraceSteps.value = []
  activeFlowStep.value = 'analysis'
  if (selectedProjectId.value) {
    await loadRequirementRecord(false)
    await loadLatestAnalysis(false)
    await loadLatestPrd(false)
    await loadLatestPrototype(false)
    syncActiveFlowStep()
  }
}

async function handleUploadProjectChange(value: string) {
  selectedProjectId.value = value
  await handleProjectChange()
  activeFlowStep.value = 'upload'
}

function resetUploadForm() {
  uploadForm.title = ''
  uploadForm.sourceType = '文本'
  uploadForm.priority = 'MEDIUM'
  uploadForm.requester = ''
  uploadForm.productOwner = ''
  uploadForm.expectedDate = ''
  uploadForm.background = ''
  uploadForm.objective = ''
  uploadForm.scope = ''
  uploadForm.sensitiveMasked = true
  uploadFileList.value = []
  uploadFormRef.value?.clearValidate()
}

const handleUploadExceed: UploadProps['onExceed'] = () => {
  ElMessage.warning('单次最多上传 8 个文件')
}

const confirmUploadRemove: UploadProps['beforeRemove'] = (file) => {
  return ElMessageBox.confirm(`确认移除 ${file.name}？`, '移除材料', {
    confirmButtonText: '移除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => true)
    .catch(() => false)
}

async function submitRequirementInFlow() {
  if (!uploadFormRef.value) {
    return
  }
  const valid = await uploadFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请补全必填需求信息')
    return
  }
  if (!uploadForm.sensitiveMasked) {
    ElMessage.warning('请先确认敏感信息已脱敏')
    return
  }
  if (!uploadForm.projectId) {
    ElMessage.warning('请选择所属项目')
    return
  }

  submittingUpload.value = true
  try {
    const files = uploadFileList.value.flatMap((file) => (file.raw ? [file.raw as File] : []))
    const result = await uploadRequirement({
      ...uploadForm,
      projectId: uploadForm.projectId,
      files,
    })
    selectedProjectId.value = uploadForm.projectId
    selectedRequirementId.value = result.requirementId
    await loadRequirementRecord(false)
    analysisResult.value = undefined
    prdDocument.value = undefined
    prototypeArtifact.value = undefined
    agentTraceSteps.value = []
    activeFlowStep.value = 'analysis'
    ElMessage.success('需求已提交，正在进入分析步骤')
    await runAnalysis(false)
  } catch {
    ElMessage.warning('需求提交失败，请确认后端服务和上传接口可用')
  } finally {
    submittingUpload.value = false
  }
}

async function runAnalysis(showMessage = true) {
  if (!selectedProjectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  analyzing.value = true
  startWaitingTimer()
  try {
    analysisResult.value = await analyzeRequirement({
      projectId: selectedProjectId.value,
      requirementId: selectedRequirementId.value,
      operatorId: 1,
    })
    selectedRequirementId.value = analysisResult.value.requirementId
    activeFlowStep.value = 'analysis'
    if (showMessage) {
      ElMessage.success('需求分析已完成')
    }
  } catch {
    ElMessage.warning('需求分析失败，请确认该项目已有需求并且模型配置可用')
  } finally {
    analyzing.value = false
    if (!generating.value) {
      stopWaitingTimer()
    }
  }
}

async function loadLatestAnalysis(showMessage = true) {
  if (!selectedProjectId.value) {
    return false
  }
  try {
    analysisResult.value = await getLatestAnalysis(selectedProjectId.value)
    selectedRequirementId.value = analysisResult.value.requirementId
    if (showMessage) {
      activeFlowStep.value = 'analysis'
      ElMessage.success('已刷新最新需求分析')
    }
    return true
  } catch {
    analysisResult.value = undefined
    if (showMessage) {
      ElMessage.info('当前项目暂无已保存的需求分析')
    }
    return false
  }
}

async function loadRequirementRecord(showMessage = true) {
  if (!selectedProjectId.value) {
    return false
  }
  loadingRequirement.value = true
  try {
    requirementRecord.value = selectedRequirementId.value
      ? await getRequirementDetail(selectedProjectId.value, selectedRequirementId.value)
      : await getLatestRequirement(selectedProjectId.value)
    selectedRequirementId.value = requirementRecord.value.requirementId
    if (showMessage) {
      ElMessage.success('已刷新最新需求记录')
    }
    return true
  } catch {
    requirementRecord.value = undefined
    if (showMessage) {
      ElMessage.info('当前项目暂无需求记录，可先上传')
    }
    return false
  } finally {
    loadingRequirement.value = false
  }
}

async function loadLatestRequirement(showMessage = true) {
  selectedRequirementId.value = undefined
  return loadRequirementRecord(showMessage)
}

async function regeneratePrd() {
  if (!selectedProjectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  generating.value = true
  startWaitingTimer()
  try {
    if (!analysisResult.value) {
      const loaded = await loadLatestAnalysis(false)
      if (!loaded) {
        await runAnalysis(false)
      }
    }
    prdDocument.value = await generatePrd({
      projectId: selectedProjectId.value,
      requirementId: selectedRequirementId.value,
      operatorId: 1,
    })
    selectedRequirementId.value = prdDocument.value.requirementId
    prototypeArtifact.value = undefined
    await loadAgentTrace(false)
    activeFlowStep.value = 'prd'
    ElMessage.success('PRD 已生成')
  } catch {
    ElMessage.warning('PRD 生成失败，请稍后重试')
  } finally {
    generating.value = false
    stopWaitingTimer()
  }
}

function startWaitingTimer() {
  stopWaitingTimer()
  waitingSeconds.value = 0
  waitingTimer = window.setInterval(() => {
    waitingSeconds.value += 1
  }, 1000)
}

function stopWaitingTimer() {
  if (waitingTimer) {
    window.clearInterval(waitingTimer)
    waitingTimer = undefined
  }
}

async function refreshLatestResults() {
  if (!selectedProjectId.value) {
    return
  }
  await loadRequirementRecord(false)
  await loadLatestAnalysis(false)
  await loadLatestPrd(false)
  await loadLatestPrototype(false)
  syncActiveFlowStep()
  ElMessage.success('已刷新最新结果')
}

async function loadLatestPrd(showMessage = true) {
  if (!selectedProjectId.value) {
    return
  }
  loadingLatest.value = true
  try {
    prdDocument.value = await getLatestPrd(selectedProjectId.value)
    selectedRequirementId.value = prdDocument.value.requirementId
    await loadAgentTrace(false)
    if (showMessage) {
      activeFlowStep.value = 'prd'
    }
    if (showMessage) {
      ElMessage.success('已刷新最新 PRD')
    }
  } catch {
    prdDocument.value = undefined
    agentTraceSteps.value = []
    if (showMessage) {
      ElMessage.info('当前项目暂无 PRD，可先生成')
    }
  } finally {
    loadingLatest.value = false
  }
}

async function loadAgentTrace(showMessage = true) {
  if (!prdDocument.value?.taskId) {
    agentTraceSteps.value = []
    return false
  }
  loadingAgentTrace.value = true
  try {
    agentTraceSteps.value = await getGenerationTaskSteps(prdDocument.value.taskId)
    if (showMessage) {
      ElMessage.success('已刷新 Agent 执行轨迹')
    }
    return true
  } catch {
    agentTraceSteps.value = []
    if (showMessage) {
      ElMessage.warning('Agent 执行轨迹加载失败')
    }
    return false
  } finally {
    loadingAgentTrace.value = false
  }
}

async function confirmCurrentPrd() {
  if (!selectedProjectId.value || !prdDocument.value) {
    ElMessage.warning('请先生成 PRD')
    return
  }
  confirmingPrd.value = true
  try {
    prdDocument.value = await confirmPrd({
      projectId: selectedProjectId.value,
      prdId: prdDocument.value.id,
      operatorId: 1,
    })
    activeFlowStep.value = 'prd'
    ElMessage.success('PRD 已确认，可以生成原型')
  } catch {
    ElMessage.warning('PRD 确认失败，请稍后重试')
  } finally {
    confirmingPrd.value = false
  }
}

async function generateCurrentPrototype() {
  if (!selectedProjectId.value || !prdDocument.value) {
    ElMessage.warning('请先生成并确认 PRD')
    return
  }
  if (prdDocument.value.status !== 'PRD_CONFIRMED') {
    ElMessage.warning('请先确认 PRD，再生成原型')
    return
  }
  generatingPrototype.value = true
  startWaitingTimer()
  try {
    prototypeArtifact.value = await generatePrototype({
      projectId: selectedProjectId.value,
      prdId: prdDocument.value.id,
      operatorId: 1,
    })
    activeFlowStep.value = 'prototype'
    ElMessage.success('原型说明已生成')
  } catch {
    ElMessage.warning('原型生成失败，请稍后重试')
  } finally {
    generatingPrototype.value = false
    if (!analyzing.value && !generating.value) {
      stopWaitingTimer()
    }
  }
}

async function loadLatestPrototype(showMessage = true) {
  if (!selectedProjectId.value) {
    return
  }
  loadingPrototype.value = true
  try {
    prototypeArtifact.value = await getLatestPrototype(selectedProjectId.value)
    if (showMessage) {
      activeFlowStep.value = 'prototype'
    }
    if (showMessage) {
      ElMessage.success('已刷新最新原型')
    }
  } catch {
    prototypeArtifact.value = undefined
    if (showMessage) {
      ElMessage.info('当前项目暂无原型，可在确认 PRD 后生成')
    }
  } finally {
    loadingPrototype.value = false
  }
}

async function copyPrd() {
  if (!prdDocument.value?.content) {
    return
  }
  try {
    await navigator.clipboard.writeText(prdDocument.value.content)
    ElMessage.success('PRD 内容已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

function formatElapsed(value?: number) {
  if (!value) {
    return '0ms'
  }
  if (value < 1000) {
    return `${value}ms`
  }
  return `${(value / 1000).toFixed(1)}s`
}

function goTo(path: string) {
  router.push(path)
}

function renderMarkdownHtml(content: string) {
  const rawHtml = marked.parse(content || '', {
    async: false,
    breaks: true,
    gfm: true,
  }) as string
  return DOMPurify.sanitize(rawHtml)
}

type MarkdownBlock =
  | {
      type: 'heading'
      text: string
      level: 1 | 2 | 3 | 4
    }
  | {
      type: 'list'
      items: string[]
    }
  | {
      type: 'paragraph'
      text: string
    }
  | {
      type: 'table'
      headers: string[]
      rows: string[][]
    }

function renderMarkdown(content: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = []
  let listItems: string[] = []
  let tableRows: string[][] = []

  function flushList() {
    if (listItems.length) {
      blocks.push({ type: 'list', items: listItems })
      listItems = []
    }
  }

  function flushTable() {
    if (!tableRows.length) {
      return
    }
    const [headers, ...rows] = tableRows
    blocks.push({
      type: 'table',
      headers,
      rows,
    })
    tableRows = []
  }

  content.split(/\r?\n/).forEach((line) => {
    const text = line.trim()
    if (!text) {
      flushList()
      if (tableRows.length) {
        return
      }
      return
    }

    if (isMarkdownTableDivider(text)) {
      return
    }

    if (isMarkdownTableRow(text)) {
      flushList()
      tableRows.push(parseTableRow(text))
      return
    }

    flushTable()

    const headingMatch = text.match(/^(#{1,4})\s+(.+)$/)
    if (headingMatch) {
      flushList()
      blocks.push({
        type: 'heading',
        level: Math.min(headingMatch[1].length, 4) as 1 | 2 | 3 | 4,
        text: headingMatch[2],
      })
      return
    }

    const numberedHeadingMatch = text.match(/^(\d{1,2})[.、]\s+(.+)$/)
    if (numberedHeadingMatch && !numberedHeadingMatch[2].includes('|')) {
      flushList()
      blocks.push({
        type: 'heading',
        level: 2,
        text: `${numberedHeadingMatch[1]}. ${numberedHeadingMatch[2]}`,
      })
      return
    }

    const listMatch = text.match(/^[-*]\s+(.+)$/)
    if (listMatch) {
      listItems.push(listMatch[1])
      return
    }

    flushList()
    blocks.push({ type: 'paragraph', text })
  })

  flushList()
  flushTable()
  return blocks
}

function isMarkdownTableRow(text: string) {
  return text.startsWith('|') && text.endsWith('|') && text.split('|').length >= 4
}

function isMarkdownTableDivider(text: string) {
  if (!isMarkdownTableRow(text)) {
    return false
  }
  return parseTableRow(text).every((cell) => /^:?-{3,}:?$/.test(cell))
}

function parseTableRow(text: string) {
  return text
    .slice(1, -1)
    .split('|')
    .map((cell) => cell.trim())
}
</script>
