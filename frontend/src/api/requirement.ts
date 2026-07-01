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
  taskId: string
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

export function analyzeRequirement(payload: RequirementAnalyzePayload) {
  return http.post<RequirementAnalysisResult, RequirementAnalysisResult>('/requirement/analyze', payload)
}

export function generatePrd(payload: GeneratePrdPayload) {
  return http.post<PrdDocumentResult, PrdDocumentResult>('/prd/generate', payload)
}

export function getLatestPrd(projectId: string) {
  return http.get<PrdDocumentResult, PrdDocumentResult>(`/prd/latest/${projectId}`)
}
