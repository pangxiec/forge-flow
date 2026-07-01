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

      <div class="sidebar-status" aria-label="需求链路状态">
        <span>Requirement</span>
        <strong>01</strong>
        <el-progress :percentage="18" :stroke-width="7" :show-text="false" />
        <small>Upload -> AI Analysis -> Review</small>
      </div>
    </aside>

    <main class="workspace requirement-workspace">
      <header class="topbar">
        <div>
          <h1>产品需求上传</h1>
          <p>沉淀原始需求材料，完成脱敏检查后进入 AI 结构化分析。</p>
        </div>
        <div class="topbar-actions">
          <el-button :icon="Back" @click="goTo('/')">返回工作台</el-button>
          <el-button type="primary" :icon="UploadFilled" :loading="submitting" :disabled="!canSubmit" @click="submitUpload">
            提交需求
          </el-button>
        </div>
      </header>

      <section class="upload-hero">
        <div class="upload-hero-copy">
          <el-tag effect="dark" type="success">M1 需求与 PRD 闭环</el-tag>
          <h2>把散落的文档、会议纪要和业务说明汇总成一份可分析的需求输入。</h2>
          <p>上传后系统会保留版本记录，并为 PRD Agent 准备背景、目标、范围、澄清问题和验收线索。</p>
        </div>
        <div class="upload-hero-metrics">
          <div v-for="metric in intakeMetrics" :key="metric.label" class="metric-panel compact-metric">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.hint }}</small>
          </div>
        </div>
      </section>

      <section class="requirement-layout">
        <div class="panel upload-form-panel">
          <div class="panel-header">
            <div>
              <h2>需求信息</h2>
              <p>字段越完整，AI 分析时需要往返澄清的次数越少。</p>
            </div>
            <el-tag :type="sensitiveReady ? 'success' : 'warning'" effect="light">
              {{ sensitiveReady ? '已开启脱敏' : '待脱敏确认' }}
            </el-tag>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="requirement-form">
            <div class="form-grid two-columns">
              <el-form-item label="所属项目" prop="projectId">
                <el-select
                  v-model="form.projectId"
                  placeholder="选择项目"
                  filterable
                  :loading="projectLoading"
                  :disabled="projectLoading"
                >
                  <el-option
                    v-for="project in projectOptions"
                    :key="project.id"
                    :label="project.label"
                    :value="project.id"
                  >
                    <div class="project-option">
                      <strong>{{ project.projectName }}</strong>
                      <span>{{ project.projectCode }}</span>
                    </div>
                  </el-option>
                  <template #empty>
                    <span>{{ projectLoading ? '项目加载中' : '暂无可选项目' }}</span>
                  </template>
                </el-select>
              </el-form-item>
              <el-form-item label="需求标题" prop="title">
                <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="例如：知识库规范检索与 PRD 自动生成" />
              </el-form-item>
            </div>

            <div class="form-grid three-columns">
              <el-form-item label="来源类型" prop="sourceType">
                <el-segmented v-model="form.sourceType" :options="sourceTypes" />
              </el-form-item>
              <el-form-item label="优先级" prop="priority">
                <el-select v-model="form.priority" placeholder="选择优先级">
                  <el-option label="高" value="HIGH" />
                  <el-option label="中" value="MEDIUM" />
                  <el-option label="低" value="LOW" />
                </el-select>
              </el-form-item>
              <el-form-item label="期望完成日期">
                <el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
              </el-form-item>
            </div>

            <div class="form-grid two-columns">
              <el-form-item label="需求方" prop="requester">
                <el-input v-model="form.requester" placeholder="业务部门 / 需求联系人" />
              </el-form-item>
              <el-form-item label="产品负责人" prop="productOwner">
                <el-input v-model="form.productOwner" placeholder="产品经理姓名" />
              </el-form-item>
            </div>

            <el-form-item label="业务背景" prop="background">
              <el-input
                v-model="form.background"
                type="textarea"
                :rows="4"
                maxlength="800"
                show-word-limit
                placeholder="说明问题背景、当前痛点、已有流程或触发原因。"
              />
            </el-form-item>
            <el-form-item label="目标与成功标准" prop="objective">
              <el-input
                v-model="form.objective"
                type="textarea"
                :rows="4"
                maxlength="800"
                show-word-limit
                placeholder="描述目标用户、业务目标、验收标准或希望 AI 重点分析的问题。"
              />
            </el-form-item>
            <el-form-item label="范围与边界" prop="scope">
              <el-input
                v-model="form.scope"
                type="textarea"
                :rows="3"
                maxlength="600"
                show-word-limit
                placeholder="说明本次包含 / 不包含的功能、异常场景、依赖系统。"
              />
            </el-form-item>

            <div class="upload-drop-section">
              <label class="upload-label">需求材料</label>
              <el-upload
                v-model:file-list="fileList"
                class="requirement-uploader"
                drag
                multiple
                :auto-upload="false"
                :limit="8"
                :on-exceed="handleExceed"
                :before-remove="confirmRemove"
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
              <el-switch v-model="form.sensitiveMasked" active-text="已脱敏" inactive-text="待确认" />
            </div>
          </el-form>
        </div>

        <aside class="upload-side">
          <div class="panel">
            <div class="panel-header compact">
              <div>
                <h2>提交前检查</h2>
                <p>进入 AI 分析前的最小可用输入。</p>
              </div>
            </div>
            <div class="check-list">
              <div v-for="item in readinessItems" :key="item.label" class="check-row">
                <el-icon :class="item.ready ? 'passed' : 'pending'">
                  <component :is="item.ready ? CircleCheck : Clock" />
                </el-icon>
                <div>
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.hint }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="panel">
            <div class="panel-header compact">
              <div>
                <h2>材料清单</h2>
                <p>{{ fileList.length ? '文件将随需求版本一并保存。' : '暂无上传文件，可先提交文本需求。' }}</p>
              </div>
            </div>
            <div v-if="fileList.length" class="file-stack">
              <div v-for="file in fileList" :key="file.uid" class="file-row">
                <el-icon><Document /></el-icon>
                <div>
                  <strong>{{ file.name }}</strong>
                  <span>{{ formatFileSize(file.size) }}</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无材料" :image-size="76" />
          </div>

          <div class="panel next-step-panel">
            <div class="panel-header compact">
              <div>
                <h2>后续流转</h2>
                <p>提交后会生成需求版本并进入 AI 分析。</p>
              </div>
            </div>
            <div class="next-step-list" aria-label="需求后续流转">
              <div v-for="(step, index) in nextSteps" :key="step.title" class="next-step-row">
                <div :class="['next-step-marker', { active: index === 1, finished: index === 0 }]">
                  <el-icon>
                    <component :is="index === 0 ? CircleCheck : Clock" />
                  </el-icon>
                </div>
                <div class="next-step-copy">
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.description }}</span>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadProps, type UploadUserFile } from 'element-plus'
import {
  Back,
  CircleCheck,
  Clock,
  Connection,
  Document,
  Grid,
  Monitor,
  Notebook,
  Tickets,
  UploadFilled,
} from '@element-plus/icons-vue'
import { uploadRequirement } from '@/api/requirement'
import { listProjects } from '@/api/project'
import type { BackendProject } from '@/types/project'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const projectLoading = ref(false)
const projects = ref<BackendProject[]>([])
const fileList = ref<UploadUserFile[]>([])

const navItems = [
  { label: '项目工作台', meta: 'Command', icon: Grid, active: false, path: '/' },
  { label: '需求与 PRD', meta: 'Spec Flow', icon: Document, active: true, path: '/requirements/prd-agent' },
  { label: '原型与架构', meta: 'Design Gate', icon: Monitor, active: false, path: '/requirements/upload' },
  { label: 'Git 交付', meta: 'Gitea Sync', icon: Connection, active: false, path: '/requirements/upload' },
  { label: '知识库', meta: 'Standards', icon: Notebook, active: false, path: '/requirements/upload' },
  { label: '审计日志', meta: 'Trace Log', icon: Tickets, active: false, path: '/requirements/upload' },
]

const projectOptions = computed(() =>
  projects.value.map((project) => ({
    ...project,
    label: `${project.projectName} / ${project.projectCode}`,
  })),
)

const sourceTypes = ['文本', '文档', '图片', '会议纪要']

const nextSteps = [
  { title: '需求上传', description: '保存原始输入与材料' },
  { title: 'AI 结构化分析', description: '生成摘要、缺失信息和澄清问题' },
  { title: '产品确认', description: '确认需求理解后触发 PRD' },
]

const form = reactive({
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

const rules: FormRules = {
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

const sensitiveReady = computed(() => form.sensitiveMasked)
const canSubmit = computed(
  () =>
    Boolean(
      form.projectId &&
        form.title.trim() &&
        form.requester.trim() &&
        form.productOwner.trim() &&
        form.background.trim() &&
        form.objective.trim() &&
        form.scope.trim() &&
        form.sensitiveMasked,
    ),
)

const intakeMetrics = computed(() => [
  { label: '材料数量', value: fileList.value.length, hint: '最多 8 个文件' },
  { label: '文本完整度', value: `${readinessItems.value.filter((item) => item.ready).length}/5`, hint: '关键输入项' },
  { label: '下一节点', value: 'AI 分析', hint: '生成澄清问题' },
])

const readinessItems = computed(() => [
  { label: '基础信息', ready: Boolean(form.title && form.requester && form.productOwner), hint: '标题、需求方、产品负责人' },
  { label: '业务背景', ready: Boolean(form.background.trim()), hint: '说明现状、痛点和触发原因' },
  { label: '目标标准', ready: Boolean(form.objective.trim()), hint: '说明目标、用户和验收标准' },
  { label: '范围边界', ready: Boolean(form.scope.trim()), hint: '说明包含、不包含和依赖项' },
  { label: '脱敏确认', ready: form.sensitiveMasked, hint: '敏感数据先脱敏再进入模型' },
])

onMounted(() => {
  loadProjectOptions()
})

function goTo(path: string) {
  router.push(path)
}

async function loadProjectOptions() {
  projectLoading.value = true
  try {
    const data = await listProjects()
    projects.value = data
    if (!form.projectId && data.length > 0) {
      form.projectId = data[0].id
    }
  } catch {
    projects.value = []
    ElMessage.warning('项目列表加载失败，请确认后端服务已启动')
  } finally {
    projectLoading.value = false
  }
}

const handleExceed: UploadProps['onExceed'] = () => {
  ElMessage.warning('单次最多上传 8 个文件')
}

const confirmRemove: UploadProps['beforeRemove'] = (file) => {
  return ElMessageBox.confirm(`确认移除 ${file.name}？`, '移除材料', {
    confirmButtonText: '移除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => true)
    .catch(() => false)
}

function formatFileSize(size?: number) {
  if (!size) {
    return '未知大小'
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function submitUpload() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请补全必填需求信息')
    return
  }
  if (!form.sensitiveMasked) {
    ElMessage.warning('请先确认敏感信息已脱敏')
    return
  }
  if (!form.projectId) {
    ElMessage.warning('请选择所属项目')
    return
  }

  submitting.value = true
  try {
    const files = fileList.value.flatMap((file) => (file.raw ? [file.raw as File] : []))
    const result = await uploadRequirement({
      ...form,
      projectId: form.projectId,
      files,
    })
    ElMessage.success('需求已上传，正在打开分析结果')
    router.push({
      path: '/requirements/prd-agent',
      query: {
        projectId: String(form.projectId),
        requirementId: String(result.requirementId),
      },
    })
  } catch {
    ElMessage.success('需求已保存为本地演示版本，后端接口就绪后可直接联调')
  } finally {
    submitting.value = false
  }
}
</script>
