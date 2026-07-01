import { http } from './http'

export interface RequirementUploadPayload {
  projectId: number
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
  requirementId: number
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
