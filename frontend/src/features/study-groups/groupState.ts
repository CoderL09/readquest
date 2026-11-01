import type { StudyGroupMember, StudyGroupMessage, StudyGroupRole } from './contracts'

export function createStudyGroupComposerRevisionGuard() {
  let revision = 0

  return {
    advance(): number {
      revision += 1
      return revision
    },
    capture(): number {
      return revision
    },
    canRestore(capturedRevision: number | null): boolean {
      return capturedRevision !== null && capturedRevision === revision
    },
  }
}

export function studyGroupRetryRestoreRevision(
  currentDraft: string,
  currentRevision: number,
): number | null {
  return currentDraft.trim() ? null : currentRevision
}

export function createStudyGroupPrivateRequestGuard() {
  let generation = 0
  let active = false

  return {
    activate(): void {
      generation += 1
      active = true
    },
    revoke(): void {
      generation += 1
      active = false
    },
    capture(): number | null {
      return active ? generation : null
    },
    canCommit(capturedGeneration: number | null): boolean {
      return capturedGeneration !== null && active && capturedGeneration === generation
    },
  }
}

export async function routeStudyGroupPrivateRequestFailure(options: {
  error: unknown
  canCommit: () => boolean
  isMembershipRequired: (error: unknown) => boolean
  onMembershipRequired: (error: unknown) => void | Promise<void>
  onRequestError: (error: unknown) => void
}): Promise<'ignored' | 'membership-required' | 'request-error'> {
  if (!options.canCommit()) return 'ignored'

  if (options.isMembershipRequired(options.error)) {
    await options.onMembershipRequired(options.error)
    return 'membership-required'
  }

  options.onRequestError(options.error)
  return 'request-error'
}

export function analyzeMessageListChange(previousIds: string[], currentIds: string[]) {
  const tailChanged = previousIds.at(-1) !== currentIds.at(-1)
  if (previousIds.length === 0) return { tailChanged, newerCount: 0 }

  const firstPreviousIndex = currentIds.indexOf(previousIds[0])
  if (firstPreviousIndex < 0) return { tailChanged, newerCount: tailChanged ? 1 : 0 }

  const previousSet = new Set(previousIds)
  const newerCount = currentIds
    .slice(firstPreviousIndex)
    .filter((id) => !previousSet.has(id)).length
  return { tailChanged, newerCount }
}

export function studyGroupMessageIdentity(message: StudyGroupMessage): string {
  return message.clientMessageId && message.sender.userId
    ? `${message.sender.userId}:${message.clientMessageId}`
    : message.id
}

function nullableDateTime(value: unknown): string | null {
  if (typeof value !== 'string' || !value) return null
  return Number.isNaN(new Date(value).getTime()) ? null : value
}

export function normalizeStudyGroupMessage(message: StudyGroupMessage): StudyGroupMessage {
  const deleted = message.deleted === true
  const normalized: StudyGroupMessage = {
    id: message.id,
    bookId: message.bookId,
    sender: {
      userId: message.sender.userId,
      displayName: message.sender.displayName,
      avatarUrl: message.sender.avatarUrl,
    },
    body: deleted ? '' : message.body,
    clientMessageId: message.clientMessageId,
    createdAt: message.createdAt,
    isCurrentUser: message.isCurrentUser === true,
    deleted,
    deletedAt: deleted ? nullableDateTime(message.deletedAt) : null,
  }
  if (
    message.delivery === 'sending' ||
    message.delivery === 'sent' ||
    message.delivery === 'failed'
  ) {
    normalized.delivery = message.delivery
  }
  return normalized
}

export function mergeStudyGroupMessages(
  current: StudyGroupMessage[],
  incoming: StudyGroupMessage[],
): StudyGroupMessage[] {
  const merged = new Map<string, StudyGroupMessage>()
  const ids = new Map<string, string>()
  for (const message of [...current, ...incoming]) {
    let normalized = normalizeStudyGroupMessage(message)
    const key = studyGroupMessageIdentity(normalized)
    const priorKey = ids.get(normalized.id)
    const prior = merged.get(priorKey ?? key)
    if (prior && !prior.sender.userId && normalized.sender.userId)
      normalized = { ...normalized, sender: prior.sender }
    if (prior?.deleted && !normalized.deleted)
      normalized = { ...normalized, body: '', deleted: true, deletedAt: prior.deletedAt }
    if (priorKey && priorKey !== key) merged.delete(priorKey)
    const normalizedKey = studyGroupMessageIdentity(normalized)
    merged.set(normalizedKey, normalized)
    ids.set(normalized.id, normalizedKey)
  }

  return [...merged.values()].sort((left, right) => {
    const timeDifference = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
    return timeDifference || left.id.localeCompare(right.id)
  })
}

export function markStudyGroupMessageFailed(
  messages: StudyGroupMessage[],
  senderUserId: string,
  clientMessageId: string,
): StudyGroupMessage[] {
  return messages.map((message) =>
    message.delivery === 'sending' &&
    message.sender.userId === senderUserId &&
    message.clientMessageId === clientMessageId
      ? { ...message, delivery: 'failed' }
      : message,
  )
}

export function removeStudyGroupPendingMessage(
  messages: StudyGroupMessage[],
  senderUserId: string,
  clientMessageId: string,
): StudyGroupMessage[] {
  return messages.filter(
    (message) =>
      !(
        message.delivery === 'sending' &&
        message.sender.userId === senderUserId &&
        message.clientMessageId === clientMessageId
      ),
  )
}

function boundedPercent(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.min(100, Math.round(value))) : 0
}

function chapterCount(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0
}

function normalizeStudyGroupRole(value: unknown): StudyGroupRole {
  return value === 'moderator' || value === 'admin' ? value : 'member'
}

export function normalizeStudyGroupMember(member: StudyGroupMember): StudyGroupMember {
  const totalChapters = chapterCount(member.totalChapters)
  return {
    userId: member.userId,
    displayName: member.displayName,
    avatarUrl: member.avatarUrl,
    joinedAt: member.joinedAt,
    isCurrentUser: member.isCurrentUser,
    percent: boundedPercent(member.percent),
    completedChapters: Math.min(chapterCount(member.completedChapters), totalChapters),
    totalChapters,
    lastActiveAt:
      typeof member.lastActiveAt === 'string' && member.lastActiveAt ? member.lastActiveAt : null,
    role: normalizeStudyGroupRole(member.role),
    mutedUntil: nullableDateTime(member.mutedUntil),
    blockedByCurrentUser: member.blockedByCurrentUser === true,
  }
}

const STUDY_GROUP_ROLE_RANK: Record<StudyGroupRole, number> = {
  member: 0,
  moderator: 1,
  admin: 2,
}

type StudyGroupRoleSubject = Pick<StudyGroupMember, 'userId' | 'role'>

export function canModerateStudyGroupMember(
  viewer: StudyGroupRoleSubject | null | undefined,
  target: StudyGroupRoleSubject | null | undefined,
): boolean {
  if (!viewer || !target || viewer.userId === target.userId) return false
  return (
    STUDY_GROUP_ROLE_RANK[normalizeStudyGroupRole(viewer.role)] >
    STUDY_GROUP_ROLE_RANK[normalizeStudyGroupRole(target.role)]
  )
}

export function canChangeStudyGroupRole(
  viewer: StudyGroupRoleSubject | null | undefined,
  target: StudyGroupRoleSubject | null | undefined,
): boolean {
  if (!viewer || !target) return false
  return (
    normalizeStudyGroupRole(viewer.role) === 'admin' &&
    normalizeStudyGroupRole(target.role) !== 'admin' &&
    viewer.userId !== target.userId
  )
}

export function isStudyGroupMemberMuted(
  member: Pick<StudyGroupMember, 'mutedUntil'> | null | undefined,
  now = Date.now(),
): boolean {
  const mutedUntil = nullableDateTime(member?.mutedUntil)
  return mutedUntil !== null && new Date(mutedUntil).getTime() > now
}

export function appendUniqueMembers(
  current: StudyGroupMember[],
  incoming: StudyGroupMember[],
): StudyGroupMember[] {
  const merged = new Map(
    current.map((member) => {
      const normalized = normalizeStudyGroupMember(member)
      return [normalized.userId, normalized]
    }),
  )
  for (const member of incoming) {
    const normalized = normalizeStudyGroupMember(member)
    merged.set(normalized.userId, normalized)
  }
  return [...merged.values()]
}

export function shouldApplyMemberRefresh(startedPage: number, currentPage: number): boolean {
  return startedPage === currentPage
}

export function nextMemberRefreshPage(lastRefreshedPage: number, loadedPageCount: number): number {
  const pageCount = Number.isFinite(loadedPageCount) ? Math.max(1, Math.floor(loadedPageCount)) : 1
  const previousPage = Number.isFinite(lastRefreshedPage) ? Math.floor(lastRefreshedPage) : 0
  if (previousPage < 1 || previousPage >= pageCount) return 1
  return previousPage + 1
}

export function reconcileMemberPageRefresh(
  current: StudyGroupMember[],
  refreshed: StudyGroupMember[],
  options: {
    page: number
    pageSize: number
    total: number
    loadedPageCount: number
  },
): {
  members: StudyGroupMember[]
  loadedPageCount: number
  hasMore: boolean
} {
  const pageSize = Number.isFinite(options.pageSize) ? Math.max(1, Math.floor(options.pageSize)) : 1
  const total = Number.isFinite(options.total) ? Math.max(0, Math.floor(options.total)) : 0
  const requestedPage = Number.isFinite(options.page) ? Math.max(1, Math.floor(options.page)) : 1
  const previouslyLoadedPageCount = Number.isFinite(options.loadedPageCount)
    ? Math.max(1, Math.floor(options.loadedPageCount))
    : 1
  const totalPageCount = Math.max(1, Math.ceil(total / pageSize))
  const loadedPageCount = Math.min(previouslyLoadedPageCount, totalPageCount)
  const pageStart = (requestedPage - 1) * pageSize
  const candidates = [
    ...current.slice(0, pageStart),
    ...refreshed,
    ...current.slice(pageStart + pageSize),
  ]
  const orderedUserIds: string[] = []
  const membersById = new Map<string, StudyGroupMember>()
  for (const member of candidates) {
    const normalized = normalizeStudyGroupMember(member)
    if (!membersById.has(normalized.userId)) orderedUserIds.push(normalized.userId)
    membersById.set(normalized.userId, normalized)
  }
  for (const member of refreshed) {
    const normalized = normalizeStudyGroupMember(member)
    membersById.set(normalized.userId, normalized)
  }

  const visibleMemberLimit = Math.min(total, loadedPageCount * pageSize)
  return {
    members: orderedUserIds.slice(0, visibleMemberLimit).flatMap((userId) => {
      const member = membersById.get(userId)
      return member ? [member] : []
    }),
    loadedPageCount,
    hasMore: loadedPageCount * pageSize < total,
  }
}

export function nextPollingDelay(failureCount: number, baseDelay = 3_000): number {
  return Math.min(30_000, baseDelay * 2 ** Math.max(0, failureCount))
}
