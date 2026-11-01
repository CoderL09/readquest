import axios from 'axios'
export function errorMessage(error: unknown, fallback = '操作未完成，请稍后重试。'): string {
  if (axios.isAxiosError(error)) {
    const detail = error.response?.data?.error
    if (typeof detail === 'string') return detail
    if (typeof detail?.message === 'string') return detail.message
    if (!error.response) return '暂时无法连接服务，请检查网络后重试。'
  }
  return error instanceof Error && !axios.isAxiosError(error) ? error.message : fallback
}
