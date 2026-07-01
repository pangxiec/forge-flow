import { http } from './http'
import type { BackendProject } from '@/types/project'

export interface CreateProjectPayload {
  projectName: string
  projectCode: string
  description?: string
  managerId: number
}

export function createProject(payload: CreateProjectPayload) {
  return http.post<BackendProject, BackendProject>('/project/create', payload)
}

export function listProjects() {
  return http.get<BackendProject[], BackendProject[]>('/project/list')
}

export function getProject(id: number) {
  return http.get<BackendProject, BackendProject>(`/project/detail/${id}`)
}
