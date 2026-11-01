export type StudyGroupSummary = {
  bookId: string
  title: string
  author: string
  coverUrl: string
  memberCount: number
  joined: boolean
  joinedAt: string | null
}

export type StudyGroupProgress = {
  percent: number
  completedChapters: number
  totalChapters: number
  lastActiveAt: string | null
}

export type StudyGroupRole = 'member' | 'moderator' | 'admin'

export type StudyGroupMember = StudyGroupProgress & {
  userId: string
  displayName: string
  avatarUrl: string
  joinedAt: string
  isCurrentUser: boolean
  role: StudyGroupRole
  mutedUntil: string | null
  blockedByCurrentUser: boolean
}

export type StudyGroupMessage = {
  id: string
  bookId: string
  sender: {
    userId: string
    displayName: string
    avatarUrl: string
  }
  body: string
  clientMessageId: string
  createdAt: string
  isCurrentUser: boolean
  deleted: boolean
  deletedAt: string | null
  delivery?: 'sending' | 'sent' | 'failed'
}

export type StudyGroupReportReason =
  'harassment' | 'spam' | 'hate' | 'sexual' | 'violence' | 'other'

export type StudyGroupReportInput =
  | {
      targetType: 'message'
      targetMessageId: string
      reason: StudyGroupReportReason
      details: string
    }
  | {
      targetType: 'user'
      targetUserId: string
      reason: StudyGroupReportReason
      details: string
    }

export type StudyGroupModerateMemberInput = {
  role?: Exclude<StudyGroupRole, 'admin'>
  mutedUntil?: string | null
  reason: string
}

export type MemberPagination = {
  page: number
  pageSize: number
  total: number
  hasMore: boolean
}

export type MessagePageInfo = {
  hasMore: boolean
  oldestCursor: string | null
  newestCursor: string | null
}

export type StudyGroupListResponse = { groups: StudyGroupSummary[] }
export type StudyGroupSummaryResponse = { group: StudyGroupSummary }
export type StudyGroupMembershipResponse = {
  membership: { bookId: string; joinedAt: string }
}
export type StudyGroupMembersResponse = {
  members: StudyGroupMember[]
  pagination: MemberPagination
}
export type StudyGroupMemberResponse = { member: StudyGroupMember }
export type StudyGroupMessagesResponse = {
  messages: StudyGroupMessage[]
  pageInfo: MessagePageInfo
}
export type StudyGroupMessageResponse = { message: StudyGroupMessage }
export type StudyGroupReportResponse = {
  report: { id: string; status: 'open' }
}

export type StudyGroupEnglishCoachLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2'

export type StudyGroupEnglishCoachRequest =
  | { action: 'express'; text: string; level: StudyGroupEnglishCoachLevel }
  | { action: 'check'; text: string; level: StudyGroupEnglishCoachLevel }
  | { action: 'reply_suggestions'; level: StudyGroupEnglishCoachLevel }

export type StudyGroupEnglishCoachExpressResponse = {
  action: 'express'
  degraded?: boolean
  noticeZh?: string
  suggestions: Array<{
    style: 'simple' | 'natural' | 'casual'
    text: string
  }>
}

export type StudyGroupEnglishCoachCheckResponse = {
  action: 'check'
  degraded?: boolean
  noticeZh?: string
  isCorrect: boolean
  corrected: string
  issues: Array<{
    original: string
    replacement: string
    explanationZh: string
  }>
}

export type StudyGroupEnglishCoachReplyResponse = {
  action: 'reply_suggestions'
  degraded?: boolean
  noticeZh?: string
  suggestions: Array<{
    text: string
    intentZh: string
  }>
}

export type StudyGroupEnglishCoachResponse =
  | StudyGroupEnglishCoachExpressResponse
  | StudyGroupEnglishCoachCheckResponse
  | StudyGroupEnglishCoachReplyResponse
