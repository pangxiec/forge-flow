import { createRouter, createWebHistory } from 'vue-router'
import PrdAgentWorkspaceView from '@/views/PrdAgentWorkspaceView.vue'
import RequirementUploadView from '@/views/RequirementUploadView.vue'
import WorkspaceView from '@/views/WorkspaceView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'workspace',
      component: WorkspaceView,
    },
    {
      path: '/requirements/upload',
      name: 'requirement-upload',
      component: RequirementUploadView,
    },
    {
      path: '/requirements/prd-agent',
      name: 'prd-agent-workspace',
      component: PrdAgentWorkspaceView,
    },
  ],
})

export default router
