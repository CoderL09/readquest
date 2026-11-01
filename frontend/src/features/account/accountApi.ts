import axios from 'axios'
import { api } from '../../lib/api'

export type AccountSession = {
  id: string
  createdAt: string
  expiresAt: string
}

type AccountSessionsResponse = { sessions: AccountSession[] }

type ApiErrorBody = {
  error?: string | { message?: string }
}

export async function getAccountExport(): Promise<Blob> {
  return (await api.get<Blob>('/account/export', { responseType: 'blob' })).data
}

export async function listAccountSessions(): Promise<AccountSession[]> {
  return (await api.get<AccountSessionsResponse>('/account/sessions')).data.sessions
}

export async function revokeAccountSession(sessionId: string): Promise<void> {
  await api.delete(`/account/sessions/${encodeURIComponent(sessionId)}`)
}

export async function requestAccountDeletion(password: string): Promise<void> {
  await api.post('/account/delete', { confirmation: 'DELETE', password })
}

export function accountErrorMessage(error: unknown, fallback: string): string {
  if (!axios.isAxiosError<ApiErrorBody>(error)) return fallback
  const apiError = error.response?.data?.error
  if (typeof apiError === 'string' && apiError.trim()) return apiError
  if (
    apiError &&
    typeof apiError === 'object' &&
    typeof apiError.message === 'string' &&
    apiError.message.trim()
  )
    return apiError.message
  return fallback
}
