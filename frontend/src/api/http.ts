import axios from 'axios'

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 120000,
})

http.interceptors.response.use((response) => {
  const body = response.data
  if (body && typeof body === 'object' && 'success' in body && body.success === false) {
    return Promise.reject(new Error(body.msg || '接口请求失败'))
  }
  if (body && typeof body === 'object' && 'data' in body) {
    return body.data
  }
  return body
})
