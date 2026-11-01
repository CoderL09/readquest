import type { StudyGroupMessage } from './contracts'

export const STUDY_GROUP_ACK_TIMEOUT_MS = 8_000

export type RealtimeConnectionStatus = 'connected' | 'retrying' | 'paused'

export function resolveVisibleRealtimeStatus(options: {
  enabled: boolean
  controllerActive: boolean
  status: RealtimeConnectionStatus
  isOnline: boolean
}): RealtimeConnectionStatus {
  if (!options.enabled || !options.controllerActive) {
    return options.isOnline ? 'retrying' : 'paused'
  }
  return options.status
}

export type RealtimeAuthenticationRefreshResult =
  { status: 'refreshed' } | { status: 'expired'; epoch: number } | { status: 'superseded' }

type RealtimeAuthenticationSnapshot = {
  epoch: number
  userId: string | null
}

export type RealtimeWireMessage = {
  id: string
  bookId: string
  sender: { userId: string; displayName: string; avatarUrl: string }
  body: string
  clientMessageId: string
  createdAt: string
  deleted?: boolean
  deletedAt?: string | null
}

type RealtimeError = {
  code: string
  message: string
  retryAfterMs?: number
}

type JoinAck = { ok: true } | { ok: false; error: RealtimeError }
type MessageAck =
  { ok: true; created: boolean; message: RealtimeWireMessage } | { ok: false; error: RealtimeError }

type Listener = (...args: unknown[]) => void

export type RealtimeSocket = {
  connected: boolean
  on: (event: string, listener: Listener) => RealtimeSocket
  off: (event: string, listener: Listener) => RealtimeSocket
  connect: () => RealtimeSocket
  disconnect: () => RealtimeSocket
  timeout: (milliseconds: number) => {
    emitWithAck: (event: string, payload: unknown) => Promise<unknown>
  }
}

type ControllerOptions = {
  socket: RealtimeSocket
  bookId: string
  userId: string
  catchUp: (signal: AbortSignal) => Promise<void>
  refreshAuthentication: () => Promise<RealtimeAuthenticationRefreshResult>
  onAuthenticationExpired: (expectedEpoch: number) => void
  onMessage: (message: StudyGroupMessage) => void
  onMembershipRequired: () => void
  onError: (message: string) => void
  onStatusChange: (status: RealtimeConnectionStatus) => void
  isOnline: () => boolean
  retryDelayMs?: number
}

export class StudyGroupRealtimeError extends Error {
  readonly code: string
  readonly retryAfterMs?: number

  constructor(error: RealtimeError) {
    super(error.message)
    this.name = 'StudyGroupRealtimeError'
    this.code = error.code
    this.retryAfterMs = error.retryAfterMs
  }
}

export function canResumeRealtimeAfterAuthenticationChange(
  snapshot: { userId: string | null; accessToken: string | null; transitioning: boolean },
  expectedUserId: string,
): boolean {
  return (
    !snapshot.transitioning && snapshot.userId === expectedUserId && Boolean(snapshot.accessToken)
  )
}

export async function refreshCurrentRealtimeAuthentication(options: {
  getSnapshot: () => RealtimeAuthenticationSnapshot
  refresh: () => Promise<{ userId: string } | null>
}): Promise<RealtimeAuthenticationRefreshResult> {
  const started = options.getSnapshot()
  let refreshed: { userId: string } | null
  try {
    refreshed = await options.refresh()
  } catch {
    const current = options.getSnapshot()
    if (current.epoch !== started.epoch || current.userId !== started.userId) {
      return { status: 'superseded' }
    }
    return { status: 'expired', epoch: started.epoch }
  }

  const current = options.getSnapshot()
  if (current.epoch !== started.epoch || current.userId !== started.userId) {
    return { status: 'superseded' }
  }
  if (!refreshed || refreshed.userId !== started.userId) {
    return { status: 'expired', epoch: started.epoch }
  }
  return { status: 'refreshed' }
}

export async function drainStudyGroupCursorCatchUp<T>(options: {
  initialCursor: string | null
  signal: AbortSignal
  fetchPage: (
    query: { after?: string; before?: string },
    signal: AbortSignal,
  ) => Promise<{
    items: T[]
    hasMore: boolean
    oldestCursor: string | null
    newestCursor: string | null
  }>
  acceptPage: (items: T[]) => void
  onContiguousCursor?: (cursor: string) => void
}): Promise<void> {
  const forward = Boolean(options.initialCursor)
  let newestCursorFromInitialPage: string | null = null
  let query: { after?: string; before?: string } = options.initialCursor
    ? { after: options.initialCursor }
    : {}

  while (!options.signal.aborted) {
    const page = await options.fetchPage(query, options.signal)
    if (options.signal.aborted) return
    options.acceptPage(page.items)
    if (forward && page.newestCursor) {
      options.onContiguousCursor?.(page.newestCursor)
    } else if (!forward && !newestCursorFromInitialPage) {
      newestCursorFromInitialPage = page.newestCursor
    }
    if (!page.hasMore) {
      if (!forward && newestCursorFromInitialPage) {
        options.onContiguousCursor?.(newestCursorFromInitialPage)
      }
      return
    }

    const nextCursor = forward ? page.newestCursor : page.oldestCursor
    const previousCursor = forward ? query.after : query.before
    if (!nextCursor || nextCursor === previousCursor) {
      throw new Error('Realtime catch-up did not advance its message cursor')
    }
    query = forward ? { after: nextCursor } : { before: nextCursor }
  }
}

export function toStudyGroupMessage(
  message: RealtimeWireMessage,
  currentUserId: string,
): StudyGroupMessage {
  return {
    ...message,
    isCurrentUser: message.sender.userId === currentUserId,
    body: message.deleted === true ? '' : message.body,
    deleted: message.deleted === true,
    deletedAt: message.deleted === true ? (message.deletedAt ?? null) : null,
    delivery: 'sent',
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isRealtimeError(value: unknown): value is RealtimeError {
  return (
    isRecord(value) &&
    typeof value.code === 'string' &&
    typeof value.message === 'string' &&
    (value.retryAfterMs === undefined || typeof value.retryAfterMs === 'number')
  )
}

function isJoinAck(value: unknown): value is JoinAck {
  if (!isRecord(value) || typeof value.ok !== 'boolean') return false
  return value.ok || isRealtimeError(value.error)
}

function isWireMessage(value: unknown): value is RealtimeWireMessage {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.bookId === 'string' &&
    isRecord(value.sender) &&
    typeof value.sender.userId === 'string' &&
    typeof value.sender.displayName === 'string' &&
    typeof value.sender.avatarUrl === 'string' &&
    typeof value.body === 'string' &&
    typeof value.clientMessageId === 'string' &&
    typeof value.createdAt === 'string'
  )
}

function isMessageAck(value: unknown): value is MessageAck {
  if (!isRecord(value) || typeof value.ok !== 'boolean') return false
  if (!value.ok) return isRealtimeError(value.error)
  return typeof value.created === 'boolean' && isWireMessage(value.message)
}

function eventError(value: unknown): RealtimeError | null {
  if (!isRecord(value)) return null
  if (isRealtimeError(value.error)) return value.error

  if (isRecord(value.data) && typeof value.data.code === 'string') {
    return {
      code: value.data.code,
      message:
        typeof value.message === 'string' ? value.message : 'Realtime chat could not connect',
      ...(typeof value.data.retryAfterMs === 'number'
        ? { retryAfterMs: value.data.retryAfterMs }
        : {}),
    }
  }

  return null
}

export function createStudyGroupRealtimeController(options: ControllerOptions) {
  const { socket } = options
  const bookId = options.bookId.toLowerCase()
  let started = false
  let joined = false
  let generation = 0
  let catchUpController: AbortController | null = null
  let refreshPromise: Promise<void> | null = null
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let retryAttempt = 0

  const reportDisconnected = () => {
    options.onStatusChange(options.isOnline() ? 'retrying' : 'paused')
  }

  const stopCurrentJoin = () => {
    generation += 1
    joined = false
    catchUpController?.abort()
    catchUpController = null
  }

  const clearRetry = () => {
    if (retryTimer) clearTimeout(retryTimer)
    retryTimer = null
  }

  const scheduleJoinRetry = () => {
    stopCurrentJoin()
    socket.disconnect()
    clearRetry()
    if (!started || !options.isOnline()) {
      reportDisconnected()
      return
    }
    const delay = options.retryDelayMs ?? Math.min(500 * 2 ** retryAttempt, 5_000)
    retryAttempt += 1
    retryTimer = setTimeout(() => {
      retryTimer = null
      if (!started || !options.isOnline()) {
        reportDisconnected()
        return
      }
      socket.connect()
    }, delay)
  }

  const handleMembershipRequired = () => {
    stopCurrentJoin()
    options.onMembershipRequired()
    socket.disconnect()
  }

  const refreshAndReconnect = (): Promise<void> => {
    if (refreshPromise) return refreshPromise
    stopCurrentJoin()
    clearRetry()
    reportDisconnected()
    socket.disconnect()
    refreshPromise = options
      .refreshAuthentication()
      .then((result) => {
        if (!started) return
        if (result.status === 'expired') {
          options.onAuthenticationExpired(result.epoch)
          return
        }
        if (result.status === 'refreshed') socket.connect()
      })
      .catch((error) => {
        if (started) {
          options.onError(
            error instanceof Error ? error.message : 'Authentication could not be refreshed',
          )
        }
      })
      .finally(() => {
        refreshPromise = null
      })
    return refreshPromise
  }

  const joinAndCatchUp = async () => {
    const localGeneration = ++generation
    joined = false
    catchUpController?.abort()
    const controller = new AbortController()
    catchUpController = controller
    options.onStatusChange('retrying')
    try {
      const rawAck = await socket
        .timeout(STUDY_GROUP_ACK_TIMEOUT_MS)
        .emitWithAck('study-group:join', { bookId })
      if (!started || localGeneration !== generation || controller.signal.aborted) return
      if (!isJoinAck(rawAck))
        throw new Error('The realtime server returned an invalid join response')
      if (!rawAck.ok) {
        if (rawAck.error.code === 'MEMBERSHIP_REQUIRED') {
          handleMembershipRequired()
          return
        }
        if (rawAck.error.code === 'UNAUTHORIZED') {
          await refreshAndReconnect()
          return
        }
        throw new StudyGroupRealtimeError(rawAck.error)
      }
      await options.catchUp(controller.signal)
      if (!started || localGeneration !== generation || controller.signal.aborted) return
      joined = true
      retryAttempt = 0
      clearRetry()
      options.onStatusChange('connected')
    } catch (error) {
      if (!started || localGeneration !== generation || controller.signal.aborted) return
      reportDisconnected()
      options.onError(error instanceof Error ? error.message : 'Realtime chat could not connect')
      scheduleJoinRetry()
    }
  }

  const onConnect: Listener = () => {
    clearRetry()
    void joinAndCatchUp()
  }
  const onDisconnect: Listener = () => {
    if (!started) return
    stopCurrentJoin()
    reportDisconnected()
  }
  const onConnectError: Listener = (value) => {
    if (!started) return
    const error = eventError(value)
    if (error?.code === 'UNAUTHORIZED') {
      void refreshAndReconnect()
      return
    }
    reportDisconnected()
    options.onError(error?.message ?? 'Realtime chat could not connect')
  }
  const onAuthExpired: Listener = () => {
    if (started) void refreshAndReconnect()
  }
  const onMessage: Listener = (value) => {
    if (!started || !isRecord(value) || !isWireMessage(value.message)) return
    if (value.message.bookId.toLowerCase() !== bookId) return
    options.onMessage(toStudyGroupMessage(value.message, options.userId))
  }

  return {
    start(): void {
      if (started) return
      started = true
      socket.on('connect', onConnect)
      socket.on('disconnect', onDisconnect)
      socket.on('connect_error', onConnectError)
      socket.on('study-group:auth-expired', onAuthExpired)
      socket.on('study-group:membership-required', handleMembershipRequired)
      socket.on('study-group:message:new', onMessage)
      reportDisconnected()
      if (options.isOnline()) socket.connect()
    },
    stop(): void {
      if (!started) return
      started = false
      clearRetry()
      stopCurrentJoin()
      socket.off('connect', onConnect)
      socket.off('disconnect', onDisconnect)
      socket.off('connect_error', onConnectError)
      socket.off('study-group:auth-expired', onAuthExpired)
      socket.off('study-group:membership-required', handleMembershipRequired)
      socket.off('study-group:message:new', onMessage)
      socket.disconnect()
    },
    pause(): void {
      if (!started) return
      clearRetry()
      stopCurrentJoin()
      socket.disconnect()
      options.onStatusChange('paused')
    },
    reconnect(): void {
      if (!started) return
      clearRetry()
      if (!options.isOnline()) {
        stopCurrentJoin()
        socket.disconnect()
        options.onStatusChange('paused')
        return
      }
      if (socket.connected) {
        void joinAndCatchUp()
        return
      }
      reportDisconnected()
      socket.connect()
    },
    async send(input: { body: string; clientMessageId: string }): Promise<StudyGroupMessage> {
      if (!started || !socket.connected || !joined) {
        throw new Error('Realtime chat is not connected')
      }
      const sendGeneration = generation
      const rawAck = await socket
        .timeout(STUDY_GROUP_ACK_TIMEOUT_MS)
        .emitWithAck('study-group:message:send', { bookId, ...input })
      if (!started || sendGeneration !== generation) {
        throw new Error('Realtime chat changed before the message was acknowledged')
      }
      if (!isMessageAck(rawAck))
        throw new Error('The realtime server returned an invalid message response')
      if (!rawAck.ok) {
        if (rawAck.error.code === 'MEMBERSHIP_REQUIRED') handleMembershipRequired()
        throw new StudyGroupRealtimeError(rawAck.error)
      }
      if (
        rawAck.message.bookId.toLowerCase() !== bookId ||
        rawAck.message.sender.userId !== options.userId ||
        rawAck.message.clientMessageId !== input.clientMessageId
      ) {
        throw new Error('The realtime server response does not match the request')
      }
      return toStudyGroupMessage(rawAck.message, options.userId)
    },
  }
}
