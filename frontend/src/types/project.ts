export type ProjectStage =
  | 'DRAFT'
  | 'REQUIREMENT_ANALYZING'
  | 'REQUIREMENT_REVIEWING'
  | 'PRD_GENERATING'
  | 'PRD_REVIEWING'
  | 'PRD_FROZEN'
  | 'PROTOTYPE_GENERATING'
  | 'PROTOTYPE_REVIEWING'
  | 'PROTOTYPE_FROZEN'
  | 'ARCHITECTURE_GENERATING'
  | 'ARCHITECTURE_REVIEWING'
  | 'CODE_GENERATING'
  | 'CODE_VALIDATING'
  | 'CODE_REVIEWING'
  | 'GIT_INITIALIZING'
  | 'GIT_PUBLISHED'
  | 'DEVELOPMENT_STARTED'
  | 'REJECTED'
  | 'BLOCKED'
  | 'FAILED'
  | 'CANCELLED'

export interface ProjectSummary {
  id: string
  projectName: string
  projectCode: string
  owner: string
  stage: ProjectStage
  statusText: string
  updatedAt: string
  progress: number
  blockers: number
}

export interface BackendProject {
  id: string
  projectName: string
  projectCode: string
  description?: string
  currentStage: ProjectStage
  status: ProjectStage
  managerId: string
  createdAt?: string
  updatedAt?: string
}

export interface ApprovalTask {
  id: number
  nodeName: string
  projectName: string
  approverRole: string
  dueText: string
  priority: 'high' | 'normal'
}

export interface QualityGate {
  name: string
  status: 'passed' | 'running' | 'pending' | 'failed'
  detail: string
}
