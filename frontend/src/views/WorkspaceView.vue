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

      <div class="sidebar-status" aria-label="平台运行状态">
        <span>Pipeline</span>
        <strong>46%</strong>
        <el-progress :percentage="46" :stroke-width="7" :show-text="false" />
        <small>PRD -> Prototype -> Dev Pack</small>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <h1>项目工作台</h1>
          <p>需求、审批、质量门禁与 Gitea 交付的实时状态。</p>
        </div>
        <div class="topbar-actions">
          <el-button :icon="Refresh" aria-label="刷新工作台" @click="refreshView">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="createDialogVisible = true">新建项目</el-button>
        </div>
      </header>

      <section class="status-strip" aria-label="平台状态">
        <div v-for="item in statusItems" :key="item.label" :class="['status-item', 'glass-service-card', item.tone]">
          <span :class="['status-dot', item.tone]" />
          <div>
            <strong>{{ item.label }}</strong>
            <small>{{ item.value }}</small>
          </div>
        </div>
      </section>

      <section class="overview-band">
        <div class="stage-summary">
          <span>当前主链路</span>
          <strong>原型审批中</strong>
          <el-progress :percentage="46" :stroke-width="10" />
        </div>
        <div v-for="metric in metrics" :key="metric.label" class="metric-panel">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.hint }}</small>
        </div>
      </section>

      <section class="content-grid">
        <div class="panel project-panel">
          <div class="panel-header">
            <div>
              <h2>项目队列</h2>
              <p>审批、验证和 Git 初始化的集中视图。</p>
            </div>
            <div class="table-tools">
              <el-input
                v-model="projectKeyword"
                :prefix-icon="Search"
                clearable
                placeholder="搜索项目或编码"
                aria-label="搜索项目或编码"
              />
              <el-segmented v-model="projectFilter" :options="projectFilters" />
            </div>
          </div>

          <el-table v-loading="projectLoading" :data="filteredProjects" class="project-table" height="340">
            <el-table-column prop="projectName" label="项目" min-width="210">
              <template #default="{ row }">
                <div class="project-name">
                  <strong>{{ row.projectName }}</strong>
                  <span>{{ row.projectCode }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="owner" label="负责人" width="110" />
            <el-table-column prop="statusText" label="阶段" width="145">
              <template #default="{ row }">
                <el-tag :type="stageTagType(row.stage)" effect="light">{{ row.statusText }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" min-width="160">
              <template #default="{ row }">
                <el-progress :percentage="row.progress" :stroke-width="8" />
              </template>
            </el-table-column>
            <el-table-column prop="blockers" label="阻断" width="78">
              <template #default="{ row }">
                <span :class="['blocker-count', { danger: row.blockers > 0 }]">{{ row.blockers }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="132" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-tooltip content="查看项目详情">
                    <el-button :icon="View" aria-label="查看项目详情" circle />
                  </el-tooltip>
                  <el-tooltip content="处理当前节点">
                    <el-button :icon="Operation" aria-label="处理当前节点" circle />
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="panel flow-panel">
          <div class="panel-header compact">
            <div>
              <h2>审批链路</h2>
              <p>会签节点完成后自动解锁下一阶段。</p>
            </div>
          </div>
          <el-steps direction="vertical" :active="5" finish-status="success">
            <el-step v-for="step in workflowSteps" :key="step.title" :title="step.title" :description="step.desc" />
          </el-steps>
        </div>
      </section>

      <section class="ops-grid">
        <div class="panel">
          <div class="panel-header">
            <div>
              <h2>审批待办</h2>
              <p>高优先级任务会优先出现在顶部。</p>
            </div>
          </div>
          <div class="task-list">
            <article v-for="task in approvalTasks" :key="task.id" class="task-row">
              <div>
                <strong>{{ task.nodeName }}</strong>
                <span>{{ task.projectName }} / {{ task.approverRole }}</span>
              </div>
              <el-tag :type="task.priority === 'high' ? 'danger' : 'warning'" effect="plain">{{ task.dueText }}</el-tag>
            </article>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <div>
              <h2>Git 初始化</h2>
              <p>当前项目：AI 全链路平台 MVP</p>
            </div>
          </div>
          <el-form :model="gitForm" label-position="top" class="git-form">
            <el-form-item label="Gitea 仓库名称">
              <el-input v-model="gitForm.repositoryName" placeholder="forge-flow-demo" />
            </el-form-item>
            <el-form-item label="目标分支">
              <el-input v-model="gitForm.branchName" placeholder="main" />
            </el-form-item>
            <div class="form-footnote">
              <span>默认分支</span>
              <strong>main</strong>
            </div>
            <el-button type="primary" :icon="Connection">准备初始化</el-button>
          </el-form>
        </div>

        <div class="panel">
          <div class="panel-header">
            <div>
              <h2>质量门禁</h2>
              <p>失败项会阻断 Git 初始化。</p>
            </div>
          </div>
          <div class="gate-list">
            <div v-for="gate in qualityGates" :key="gate.name" class="gate-row">
              <el-icon :class="gate.status">
                <component :is="gateIcon(gate.status)" />
              </el-icon>
              <div>
                <strong>{{ gate.name }}</strong>
                <span>{{ gate.detail }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="panel security-panel">
        <div class="panel-header">
          <div>
            <h2>敏感配置策略</h2>
            <p>敏感配置保持空值，模型调用前完成脱敏。</p>
          </div>
          <el-tag type="success" effect="light">脱敏后调用模型</el-tag>
        </div>
        <div class="security-fields">
          <code>spring.datasource.url:</code>
          <code>spring.datasource.username:</code>
          <code>spring.datasource.password:</code>
          <code>forge-flow.gitea.token:</code>
          <code>forge-flow.llm.api-key:</code>
          <code>minio.secret-key:</code>
        </div>
      </section>
    </main>

    <el-dialog v-model="createDialogVisible" title="新建项目" width="520px">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="项目名称" required>
          <el-input v-model="createForm.projectName" placeholder="AI 全链路平台 MVP" />
        </el-form-item>
        <el-form-item label="项目编码" required>
          <el-input v-model="createForm.projectCode" placeholder="forge-flow-mvp" />
        </el-form-item>
        <el-form-item label="项目经理 ID">
          <el-input-number v-model="createForm.managerId" :min="1" class="full-input" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!canCreateProject" :loading="creating" @click="submitCreateProject">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CircleCheck,
  Clock,
  CloseBold,
  Connection,
  Document,
  Grid,
  Loading,
  Monitor,
  Notebook,
  Operation,
  Plus,
  Refresh,
  Search,
  Tickets,
  View,
} from '@element-plus/icons-vue'
import { createProject, listProjects } from '@/api/project'
import type { ApprovalTask, BackendProject, ProjectStage, ProjectSummary, QualityGate } from '@/types/project'

const router = useRouter()

const navItems = [
  { label: '项目工作台', meta: 'Command', icon: Grid, active: true, path: '/' },
  { label: '需求与 PRD', meta: 'Spec Flow', icon: Document, active: false, path: '/requirements/prd-agent' },
  { label: '原型与架构', meta: 'Design Gate', icon: Monitor, active: false, path: '/' },
  { label: 'Git 交付', meta: 'Gitea Sync', icon: Connection, active: false, path: '/' },
  { label: '知识库', meta: 'Standards', icon: Notebook, active: false, path: '/' },
  { label: '审计日志', meta: 'Trace Log', icon: Tickets, active: false, path: '/' },
]

const projectFilter = ref('全部')
const projectKeyword = ref('')
const projectFilters = ['全部', '待审批', '验证中', 'Git']
const createDialogVisible = ref(false)
const creating = ref(false)
const projectLoading = ref(false)
const backendConnected = ref(false)

const createForm = reactive({
  projectName: '',
  projectCode: '',
  description: '',
  managerId: 1,
})

const gitForm = reactive({
  repositoryName: 'forge-flow-mvp',
  branchName: 'main',
})

const statusItems = [
  { label: '模型通道', value: '公网模型允许调用', tone: 'ok' },
  { label: '知识库', value: '2 份规范已接入', tone: 'ok' },
  { label: '审计日志', value: '全量记录开启', tone: 'ok' },
  { label: 'Git 集成', value: 'Gitea', tone: 'active' },
]

const projects = ref<ProjectSummary[]>([
  {
    id: '1',
    projectName: 'AI 全链路平台 MVP',
    projectCode: 'forge-flow-mvp',
    owner: '项目经理',
    stage: 'PROTOTYPE_REVIEWING',
    statusText: '原型审批中',
    updatedAt: '2026-06-30 15:45',
    progress: 46,
    blockers: 0,
  },
  {
    id: '2',
    projectName: '研发规范知识库',
    projectCode: 'rd-knowledge',
    owner: '产品经理',
    stage: 'PRD_REVIEWING',
    statusText: 'PRD 审批中',
    updatedAt: '2026-06-30 14:20',
    progress: 32,
    blockers: 1,
  },
  {
    id: '3',
    projectName: 'Gitea 交付适配',
    projectCode: 'gitea-delivery',
    owner: '架构师',
    stage: 'CODE_VALIDATING',
    statusText: '代码验证中',
    updatedAt: '2026-06-30 13:10',
    progress: 72,
    blockers: 0,
  },
])

const approvalTasks: ApprovalTask[] = [
  {
    id: 1,
    nodeName: '原型确认',
    projectName: 'AI 全链路平台 MVP',
    approverRole: '后端负责人',
    dueText: '今天',
    priority: 'high',
  },
  {
    id: 2,
    nodeName: 'PRD 确认',
    projectName: '研发规范知识库',
    approverRole: '测试负责人',
    dueText: '24 小时内',
    priority: 'normal',
  },
  {
    id: 3,
    nodeName: '开发包确认',
    projectName: 'Gitea 交付适配',
    approverRole: '架构师',
    dueText: '48 小时内',
    priority: 'normal',
  },
]

const workflowSteps = [
  { title: '需求创建', desc: '产品上传原始需求' },
  { title: 'AI 分析', desc: '生成澄清问题' },
  { title: '产品确认', desc: '确认需求理解' },
  { title: '需求方确认', desc: '确认业务规则' },
  { title: 'PRD 冻结', desc: '项目经理最终确认' },
  { title: 'AI 生成原型', desc: '生成 Vue 原型' },
  { title: '原型冻结', desc: '前后端负责人会签' },
  { title: 'AI 生成开发包', desc: '生成工程骨架' },
  { title: '架构师确认', desc: '确认技术方案' },
  { title: 'Git 初始化', desc: '创建仓库并推送' },
  { title: '开发开始', desc: '开发拉取代码' },
]

const qualityGates: QualityGate[] = [
  { name: '后端编译检查', status: 'passed', detail: 'Maven package 通过' },
  { name: '前端编译检查', status: 'passed', detail: 'npm run build 通过' },
  { name: '启动验证', status: 'pending', detail: '配置数据库后执行' },
  { name: '敏感信息扫描', status: 'passed', detail: '初始敏感配置为空' },
]

const metrics = computed(() => [
  { label: '进行中项目', value: projects.value.length, hint: '第一阶段内部项目' },
  { label: '待审批任务', value: approvalTasks.length, hint: '含会签节点' },
  { label: '阻断项', value: projects.value.reduce((sum, item) => sum + item.blockers, 0), hint: '需人工处理' },
  { label: 'Git 待初始化', value: 1, hint: '仓库名页面填写' },
])

const canCreateProject = computed(() => Boolean(createForm.projectName.trim() && createForm.projectCode.trim()))

const filteredProjects = computed(() => {
  const keyword = projectKeyword.value.trim().toLowerCase()
  const keywordMatched = keyword
    ? projects.value.filter((project) =>
        `${project.projectName} ${project.projectCode}`.toLowerCase().includes(keyword),
      )
    : projects.value
  if (projectFilter.value === '待审批') {
    return keywordMatched.filter((project) => project.stage.includes('REVIEWING'))
  }
  if (projectFilter.value === '验证中') {
    return keywordMatched.filter((project) => project.stage === 'CODE_VALIDATING')
  }
  if (projectFilter.value === 'Git') {
    return keywordMatched.filter((project) => project.stage === 'GIT_INITIALIZING' || project.stage === 'GIT_PUBLISHED')
  }
  return keywordMatched
})

onMounted(() => {
  loadProjects()
})

async function loadProjects(showSuccess = false) {
  projectLoading.value = true
  try {
    const backendProjects = await listProjects()
    projects.value = backendProjects.map(mapBackendProject)
    backendConnected.value = true
    if (showSuccess) {
      ElMessage.success('已同步后端项目数据')
    }
  } catch {
    backendConnected.value = false
    if (showSuccess) {
      ElMessage.warning('后端暂未连接，当前展示本地演示数据')
    }
  } finally {
    projectLoading.value = false
  }
}

function mapBackendProject(project: BackendProject): ProjectSummary {
  const stage = project.currentStage || project.status || 'DRAFT'
  return {
    id: project.id,
    projectName: project.projectName,
    projectCode: project.projectCode,
    owner: `PM-${project.managerId}`,
    stage,
    statusText: stageTextMap[stage] || stage,
    updatedAt: formatDateTime(project.updatedAt || project.createdAt),
    progress: stageProgressMap[stage] || 8,
    blockers: stage === 'REJECTED' || stage === 'FAILED' || stage === 'BLOCKED' ? 1 : 0,
  }
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

const stageTextMap: Record<ProjectStage, string> = {
  DRAFT: '草稿',
  REQUIREMENT_ANALYZING: 'AI 需求分析中',
  REQUIREMENT_REVIEWING: '需求审批中',
  PRD_GENERATING: 'PRD 生成中',
  PRD_REVIEWING: 'PRD 审批中',
  PRD_FROZEN: 'PRD 已冻结',
  PROTOTYPE_GENERATING: '原型生成中',
  PROTOTYPE_REVIEWING: '原型审批中',
  PROTOTYPE_FROZEN: '原型已冻结',
  ARCHITECTURE_GENERATING: '开发包生成中',
  ARCHITECTURE_REVIEWING: '架构审批中',
  CODE_GENERATING: '代码生成中',
  CODE_VALIDATING: '代码验证中',
  CODE_REVIEWING: '代码确认中',
  GIT_INITIALIZING: 'Git 初始化中',
  GIT_PUBLISHED: 'Git 已推送',
  DEVELOPMENT_STARTED: '开发开始',
  REJECTED: '已驳回',
  FAILED: '执行失败',
  BLOCKED: '已阻断',
  CANCELLED: '已取消',
}

const stageProgressMap: Record<ProjectStage, number> = {
  DRAFT: 8,
  REQUIREMENT_ANALYZING: 14,
  REQUIREMENT_REVIEWING: 18,
  PRD_GENERATING: 26,
  PRD_REVIEWING: 32,
  PRD_FROZEN: 40,
  PROTOTYPE_GENERATING: 46,
  PROTOTYPE_REVIEWING: 52,
  PROTOTYPE_FROZEN: 62,
  ARCHITECTURE_GENERATING: 68,
  ARCHITECTURE_REVIEWING: 72,
  CODE_GENERATING: 78,
  CODE_VALIDATING: 82,
  CODE_REVIEWING: 88,
  GIT_INITIALIZING: 92,
  GIT_PUBLISHED: 100,
  DEVELOPMENT_STARTED: 100,
  REJECTED: 24,
  FAILED: 24,
  BLOCKED: 24,
  CANCELLED: 0,
}

function stageTagType(stage: ProjectStage) {
  if (stage.includes('FROZEN') || stage === 'GIT_PUBLISHED') {
    return 'success'
  }
  if (stage.includes('REVIEWING')) {
    return 'warning'
  }
  if (stage === 'CODE_VALIDATING') {
    return 'primary'
  }
  return 'info'
}

function gateIcon(status: QualityGate['status']) {
  const iconMap = {
    passed: CircleCheck,
    running: Loading,
    pending: Clock,
    failed: CloseBold,
  }
  return iconMap[status]
}

function refreshView() {
  loadProjects(true)
}

function goTo(path: string) {
  router.push(path)
}

async function submitCreateProject() {
  if (!createForm.projectName || !createForm.projectCode) {
    ElMessage.warning('请填写项目名称和项目编码')
    return
  }
  creating.value = true
  try {
    const project = await createProject({ ...createForm })
    projects.value.unshift(mapBackendProject(project))
    createDialogVisible.value = false
    ElMessage.success('项目已创建')
  } catch {
    ElMessage.warning('项目创建失败，请确认后端服务和数据库连接正常')
  } finally {
    creating.value = false
  }
}
</script>
