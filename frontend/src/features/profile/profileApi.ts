import axios from 'axios'
import { api, type AuthUser, type GrowthSummaryResponse } from '../../lib/api'

type UserProfileResponse = {
  user: AuthUser
}

type ApiErrorBody = {
  error?: string | { message?: string }
}

export async function updateDisplayName(displayName: string): Promise<AuthUser> {
  return (await api.patch<UserProfileResponse>('/users/me', { displayName })).data.user
}

export async function uploadAvatar(file: File): Promise<AuthUser> {
  const formData = new FormData()
  formData.append('avatar', file)
  return (await api.post<UserProfileResponse>('/users/me/avatar', formData)).data.user
}

export async function getProfileGrowth(signal?: AbortSignal): Promise<GrowthSummaryResponse> {
  return (await api.get<GrowthSummaryResponse>('/growth/summary', { signal })).data
}

export function profileErrorMessage(error: unknown, fallback: string): string {
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
  if (!error.response && navigator.onLine === false) {
    return 'You appear to be offline. Reconnect and try again.'
  }
  return fallback
}
