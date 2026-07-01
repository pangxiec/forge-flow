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
          <el-button :icon="UploadFilled" @click="goTo('/requirements/upload')">上传需求</el-button>
          <el-button :icon="Refresh" :loading="loadingLatest" :disabled="!selectedProjectId" @click="loadLatestPrd">
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

      <section class="prd-agent-layout">
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
              <div v-if="analysisViewMode === 'preview'" class="markdown-render compact">
                <template v-for="(block, index) in renderMarkdown(analysisResult.structuredSummary)" :key="index">
                  <component :is="`h${block.level}`" v-if="block.type === 'heading'" class="markdown-heading">
                    {{ block.text }}
                  </component>
                  <ul v-else-if="block.type === 'list'" class="markdown-list">
                    <li v-for="item in block.items" :key="item">{{ item }}</li>
                  </ul>
                  <div v-else-if="block.type === 'table'" class="markdown-table-wrap">
                    <table class="markdown-table">
                      <thead>
                        <tr>
                          <th v-for="header in block.headers" :key="header">{{ header }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                          <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <p v-else class="markdown-paragraph">{{ block.text }}</p>
                </template>
              </div>
              <pre v-else>{{ analysisResult.structuredSummary }}</pre>
            </article>
            <article class="analysis-block warning">
              <div class="analysis-block-title">
                <el-icon><Warning /></el-icon>
                <strong>缺失信息</strong>
              </div>
              <div v-if="analysisViewMode === 'preview'" class="markdown-render compact">
                <template v-for="(block, index) in renderMarkdown(analysisResult.missingInfo)" :key="index">
                  <component :is="`h${block.level}`" v-if="block.type === 'heading'" class="markdown-heading">
                    {{ block.text }}
                  </component>
                  <ul v-else-if="block.type === 'list'" class="markdown-list">
                    <li v-for="item in block.items" :key="item">{{ item }}</li>
                  </ul>
                  <div v-else-if="block.type === 'table'" class="markdown-table-wrap">
                    <table class="markdown-table">
                      <thead>
                        <tr>
                          <th v-for="header in block.headers" :key="header">{{ header }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                          <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <p v-else class="markdown-paragraph">{{ block.text }}</p>
                </template>
              </div>
              <pre v-else>{{ analysisResult.missingInfo }}</pre>
            </article>
            <article class="analysis-block">
              <div class="analysis-block-title">
                <el-icon><QuestionFilled /></el-icon>
                <strong>澄清问题</strong>
              </div>
              <div v-if="analysisViewMode === 'preview'" class="markdown-render compact">
                <template v-for="(block, index) in renderMarkdown(analysisResult.clarificationQuestions)" :key="index">
                  <component :is="`h${block.level}`" v-if="block.type === 'heading'" class="markdown-heading">
                    {{ block.text }}
                  </component>
                  <ul v-else-if="block.type === 'list'" class="markdown-list">
                    <li v-for="item in block.items" :key="item">{{ item }}</li>
                  </ul>
                  <div v-else-if="block.type === 'table'" class="markdown-table-wrap">
                    <table class="markdown-table">
                      <thead>
                        <tr>
                          <th v-for="header in block.headers" :key="header">{{ header }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                          <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <p v-else class="markdown-paragraph">{{ block.text }}</p>
                </template>
              </div>
              <pre v-else>{{ analysisResult.clarificationQuestions }}</pre>
            </article>
          </div>
        </div>

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
              <el-button :icon="CopyDocument" :disabled="!prdDocument" @click="copyPrd">复制</el-button>
            </div>
          </div>

          <el-skeleton v-if="generating || loadingLatest" :rows="12" animated />
          <el-empty v-else-if="!prdDocument" description="暂无 PRD 文档" :image-size="110">
            <el-button type="primary" :disabled="!selectedProjectId" @click="regeneratePrd">生成 PRD</el-button>
          </el-empty>
          <article v-else-if="prdViewMode === 'preview'" class="prd-markdown-preview rendered">
            <template v-for="(block, index) in renderMarkdown(prdDocument.content)" :key="index">
              <component :is="`h${block.level}`" v-if="block.type === 'heading'" class="markdown-heading">
                {{ block.text }}
              </component>
              <ul v-else-if="block.type === 'list'" class="markdown-list">
                <li v-for="item in block.items" :key="item">{{ item }}</li>
              </ul>
              <div v-else-if="block.type === 'table'" class="markdown-table-wrap">
                <table class="markdown-table">
                  <thead>
                    <tr>
                      <th v-for="header in block.headers" :key="header">{{ header }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                      <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p v-else class="markdown-paragraph">{{ block.text }}</p>
            </template>
          </article>
          <article v-else class="prd-markdown-preview">
            <pre>{{ prdDocument.content }}</pre>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
import { analyzeRequirement, generatePrd, getLatestPrd, type PrdDocumentResult, type RequirementAnalysisResult } from '@/api/requirement'
import { listProjects } from '@/api/project'
import type { BackendProject } from '@/types/project'

const route = useRoute()
const router = useRouter()

const projectLoading = ref(false)
const analyzing = ref(false)
const generating = ref(false)
const loadingLatest = ref(false)
const waitingSeconds = ref(0)
let waitingTimer: number | undefined
const projects = ref<BackendProject[]>([])
const selectedProjectId = ref<string>()
const selectedRequirementId = ref<string>()
const analysisResult = ref<RequirementAnalysisResult>()
const prdDocument = ref<PrdDocumentResult>()
const analysisViewMode = ref('preview')
const prdViewMode = ref('preview')
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

const agentProgress = computed(() => {
  if (prdDocument.value) {
    return 78
  }
  if (analysisResult.value) {
    return 46
  }
  return 18
})

const agentBusy = computed(() => analyzing.value || generating.value)

const waitTitle = computed(() => (generating.value ? 'PRD 正在生成' : '需求正在分析'))

const waitDescription = computed(() =>
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
    hint: analysisResult.value ? `Task ${analysisResult.value.taskId}` : '使用最新需求输入',
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
    if (selectedRequirementId.value) {
      await runAnalysis(false)
    }
    await loadLatestPrd(false)
  }
})

onUnmounted(() => {
  stopWaitingTimer()
})

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
  } catch {
    ElMessage.warning('项目列表加载失败，请确认后端服务已启动')
  } finally {
    projectLoading.value = false
  }
}

async function handleProjectChange() {
  selectedRequirementId.value = undefined
  analysisResult.value = undefined
  prdDocument.value = undefined
  if (selectedProjectId.value) {
    await loadLatestPrd(false)
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

async function regeneratePrd() {
  if (!selectedProjectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  generating.value = true
  startWaitingTimer()
  try {
    if (!analysisResult.value) {
      await runAnalysis(false)
    }
    prdDocument.value = await generatePrd({
      projectId: selectedProjectId.value,
      requirementId: selectedRequirementId.value,
      operatorId: 1,
    })
    selectedRequirementId.value = prdDocument.value.requirementId
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

async function loadLatestPrd(showMessage = true) {
  if (!selectedProjectId.value) {
    return
  }
  loadingLatest.value = true
  try {
    prdDocument.value = await getLatestPrd(selectedProjectId.value)
    selectedRequirementId.value = prdDocument.value.requirementId
    if (showMessage) {
      ElMessage.success('已刷新最新 PRD')
    }
  } catch {
    prdDocument.value = undefined
    if (showMessage) {
      ElMessage.info('当前项目暂无 PRD，可先生成')
    }
  } finally {
    loadingLatest.value = false
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

function goTo(path: string) {
  router.push(path)
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
