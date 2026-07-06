import { http } from './http'

export interface RequirementUploadPayload {
  projectId: string
  title: string
  sourceType: string
  priority: string
  requester: string
  productOwner: string
  expectedDate?: string
  background: string
  objective: string
  scope: string
  sensitiveMasked: boolean
  files: File[]
}

export interface RequirementUploadResult {
  requirementId: string
  versionNo: string
  status: 'DRAFT' | 'REQUIREMENT_ANALYZING' | 'REQUIREMENT_REVIEWING'
  materialCount: number
  createdAt: string
}

export interface RequirementDetailResult {
  requirementId: string
  projectId: string
  title: string
  sourceType: string
  priority: string
  requester: string
  productOwner: string
  expectedDate?: string
  background: string
  objective: string
  scope: string
  materialCount: number
  status: string
  versionNo: string
  sensitiveMasked: boolean
  createdAt: string
  updatedAt: string
}

export function uploadRequirement(payload: RequirementUploadPayload) {
  const formData = new FormData()
  formData.append('projectId', String(payload.projectId))
  formData.append('title', payload.title)
  formData.append('sourceType', payload.sourceType)
  formData.append('priority', payload.priority)
  formData.append('requester', payload.requester)
  formData.append('productOwner', payload.productOwner)
  formData.append('expectedDate', payload.expectedDate || '')
  formData.append('background', payload.background)
  formData.append('objective', payload.objective)
  formData.append('scope', payload.scope)
  formData.append('sensitiveMasked', String(payload.sensitiveMasked))
  payload.files.forEach((file) => {
    formData.append('files', file)
  })

  return http.post<RequirementUploadResult, RequirementUploadResult>('/requirement/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export interface RequirementAnalysisResult {
  requirementId: string
  taskId?: string
  title: string
  status: string
  structuredSummary: string
  missingInfo: string
  clarificationQuestions: string
  analyzedAt: string
}

export interface RequirementAnalyzePayload {
  projectId: string
  requirementId?: string
  operatorId?: number
}

export interface GeneratePrdPayload {
  projectId: string
  requirementId?: string
  operatorId?: number
}

export interface ConfirmPrdPayload {
  projectId: string
  prdId: string
  operatorId?: number
}

export interface GeneratePrototypePayload {
  projectId: string
  prdId?: string
  operatorId?: number
}

export interface PrdDocumentResult {
  id: string
  projectId: string
  requirementId: string
  taskId?: string
  title: string
  content: string
  status: string
  versionNo: string
  createdAt: string
  updatedAt: string
}

export interface PrototypeArtifactResult {
  id: string
  projectId: string
  requirementId: string
  prdId: string
  taskId?: string
  title: string
  prototypeType: string
  content: string
  status: string
  versionNo: string
  createdAt: string
  updatedAt: string
}

export interface GenerationTaskStepResult {
  id: string
  taskId: string
  projectId: string
  stepOrder: number
  stepName: string
  toolName: string
  status: string
  summary: string
  elapsedMillis: number
  startedAt?: string
  finishedAt?: string
}

export function analyzeRequirement(payload: RequirementAnalyzePayload) {
  return http.post<RequirementAnalysisResult, RequirementAnalysisResult>('/requirement/analyze', payload, {
    timeout: 180000,
  })
}

export function getLatestAnalysis(projectId: string) {
  return http.get<RequirementAnalysisResult, RequirementAnalysisResult>(`/requirement/latest-analysis/${projectId}`)
}

export function getLatestRequirement(projectId: string) {
  return http.get<RequirementDetailResult, RequirementDetailResult>(`/requirement/latest/${projectId}`)
}

export function getRequirementDetail(projectId: string, requirementId: string) {
  return http.get<RequirementDetailResult, RequirementDetailResult>(`/requirement/${projectId}/${requirementId}`)
}

export function generatePrd(payload: GeneratePrdPayload) {
  return http.post<PrdDocumentResult, PrdDocumentResult>('/prd/generate', payload, {
    timeout: 180000,
  })
}

export function confirmPrd(payload: ConfirmPrdPayload) {
  return http.post<PrdDocumentResult, PrdDocumentResult>('/prd/confirm', payload)
}

export function getLatestPrd(projectId: string) {
  return http.get<PrdDocumentResult, PrdDocumentResult>(`/prd/latest/${projectId}`)
}

export function generatePrototype(payload: GeneratePrototypePayload) {
  return http.post<PrototypeArtifactResult, PrototypeArtifactResult>('/prototype/generate', payload, {
    timeout: 180000,
  })
}

export function getLatestPrototype(projectId: string) {
  return http.get<PrototypeArtifactResult, PrototypeArtifactResult>(`/prototype/latest/${projectId}`)
}

export function getGenerationTaskSteps(taskId: string) {
  return http.get<GenerationTaskStepResult[], GenerationTaskStepResult[]>(`/generation-task/${taskId}/steps`)
}
