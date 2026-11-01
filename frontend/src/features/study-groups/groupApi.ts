import axios from 'axios'
import { api } from '../../lib/api'
import type {
  StudyGroupListResponse,
  StudyGroupEnglishCoachRequest,
  StudyGroupEnglishCoachResponse,
  StudyGroupMembershipResponse,
  StudyGroupMemberResponse,
  StudyGroupMembersResponse,
  StudyGroupMessageResponse,
  StudyGroupMessagesResponse,
  StudyGroupModerateMemberInput,
  StudyGroupReportInput,
  StudyGroupReportResponse,
  StudyGroupSummaryResponse,
} from './contracts'
import { normalizeStudyGroupMember, normalizeStudyGroupMessage } from './groupState'

function groupPath(bookId: string): string {
  return `/study-groups/${encodeURIComponent(bookId)}`
}

export async function getJoinedStudyGroups(signal?: AbortSignal) {
  return (await api.get<StudyGroupListResponse>('/study-groups', { signal })).data.groups
}

export async function getStudyGroupSummary(bookId: string, signal?: AbortSignal) {
  return (await api.get<StudyGroupSummaryResponse>(groupPath(bookId), { signal })).data.group
}

export async function joinStudyGroup(bookId: string) {
  return (await api.post<StudyGroupMembershipResponse>(`${groupPath(bookId)}/membership`)).data
    .membership
}

export async function leaveStudyGroup(bookId: string) {
  await api.delete(`${groupPath(bookId)}/membership`)
}

export async function getStudyGroupMembers(
  bookId: string,
  options: { page?: number; pageSize?: number; signal?: AbortSignal } = {},
) {
  const response = (
    await api.get<StudyGroupMembersResponse>(`${groupPath(bookId)}/members`, {
      params: { page: options.page ?? 1, pageSize: options.pageSize ?? 20 },
      signal: options.signal,
    })
  ).data
  return {
    members: response.members.map(normalizeStudyGroupMember),
    pagination: response.pagination,
  }
}

export async function getStudyGroupMember(
  bookId: string,
  memberUserId: string,
  signal?: AbortSignal,
) {
  const response = (
    await api.get<StudyGroupMemberResponse>(
      `${groupPath(bookId)}/members/${encodeURIComponent(memberUserId)}`,
      { signal },
    )
  ).data.member
  return normalizeStudyGroupMember(response)
}

export async function getStudyGroupMessages(
  bookId: string,
  options: { before?: string; after?: string; limit?: number; signal?: AbortSignal } = {},
) {
  const response = (
    await api.get<StudyGroupMessagesResponse>(`${groupPath(bookId)}/messages`, {
      params: {
        before: options.before,
        after: options.after,
        limit: options.limit ?? 50,
      },
      signal: options.signal,
    })
  ).data
  return {
    messages: response.messages.map(normalizeStudyGroupMessage),
    pageInfo: response.pageInfo,
  }
}

export async function sendStudyGroupMessage(
  bookId: string,
  input: { body: string; clientMessageId: string },
) {
  const message = (
    await api.post<StudyGroupMessageResponse>(`${groupPath(bookId)}/messages`, input)
  ).data.message
  return normalizeStudyGroupMessage(message)
}

export async function createStudyGroupReport(bookId: string, input: StudyGroupReportInput) {
  return (await api.post<StudyGroupReportResponse>(`${groupPath(bookId)}/reports`, input)).data
    .report
}

export async function blockStudyGroupMember(bookId: string, memberUserId: string) {
  await api.put(`${groupPath(bookId)}/blocks/${encodeURIComponent(memberUserId)}`, {})
}

export async function unblockStudyGroupMember(bookId: string, memberUserId: string) {
  await api.delete(`${groupPath(bookId)}/blocks/${encodeURIComponent(memberUserId)}`, { data: {} })
}

export async function moderateStudyGroupMember(
  bookId: string,
  memberUserId: string,
  input: StudyGroupModerateMemberInput,
) {
  await api.patch(`${groupPath(bookId)}/members/${encodeURIComponent(memberUserId)}`, input)
}

export async function removeStudyGroupMember(bookId: string, memberUserId: string, reason: string) {
  await api.delete(`${groupPath(bookId)}/members/${encodeURIComponent(memberUserId)}`, {
    data: { reason },
  })
}

export async function deleteStudyGroupMessage(bookId: string, messageId: string, reason: string) {
  await api.delete(`${groupPath(bookId)}/messages/${encodeURIComponent(messageId)}`, {
    data: { reason },
  })
}

export async function requestStudyGroupEnglishCoach(
  bookId: string,
  input: StudyGroupEnglishCoachRequest,
  signal?: AbortSignal,
) {
  return (
    await api.post<StudyGroupEnglishCoachResponse>(`${groupPath(bookId)}/english-coach`, input, {
      signal,
    })
  ).data
}

type ApiErrorBody = { error?: string | { code?: string; message?: string } }

export function studyGroupErrorMessage(error: unknown, fallback: string): string {
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
  if (!error.response && navigator.onLine === false)
    return 'You appear to be offline. Reconnect and try again.'
  return fallback
}

export function isStudyGroupMembershipRequired(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 403
}

export function isStudyGroupMemberNotFound(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 404
}

export function studyGroupErrorCode(error: unknown): string | null {
  if (!axios.isAxiosError<ApiErrorBody>(error)) return null
  const apiError = error.response?.data?.error
  return apiError && typeof apiError === 'object' && typeof apiError.code === 'string'
    ? apiError.code
    : null
}
