import assert from 'node:assert/strict'
import test from 'node:test'
import type { StudyGroupMessage } from './contracts'
import {
  canResumeRealtimeAfterAuthenticationChange,
  createStudyGroupRealtimeController,
  drainStudyGroupCursorCatchUp,
  refreshCurrentRealtimeAuthentication,
  resolveVisibleRealtimeStatus,
  StudyGroupRealtimeError,
  toStudyGroupMessage,
  type RealtimeSocket,
} from './groupRealtime'

const BOOK_ID = '64b64c1f2f4f3a0012345678'

test('a disabled or not-yet-started realtime session cannot expose a stale connected status', () => {
  assert.equal(
    resolveVisibleRealtimeStatus({
      enabled: false,
      controllerActive: true,
      status: 'connected',
      isOnline: true,
    }),
    'retrying',
  )
  assert.equal(
    resolveVisibleRealtimeStatus({
      enabled: true,
      controllerActive: false,
      status: 'connected',
      isOnline: true,
    }),
    'retrying',
  )
  assert.equal(
    resolveVisibleRealtimeStatus({
      enabled: false,
      controllerActive: true,
      status: 'connected',
      isOnline: false,
    }),
    'paused',
  )
  assert.equal(
    resolveVisibleRealtimeStatus({
      enabled: true,
      controllerActive: true,
      status: 'connected',
      isOnline: true,
    }),
    'connected',
  )
})

type Listener = (...args: unknown[]) => void

class FakeSocket implements RealtimeSocket {
  connected = false
  readonly listeners = new Map<string, Set<Listener>>()
  readonly acknowledgements = new Map<string, unknown[]>()
  readonly emitted: Array<{ event: string; payload: unknown }> = []
  connectCount = 0
  disconnectCount = 0

  on(event: string, listener: Listener): this {
    const listeners = this.listeners.get(event) ?? new Set<Listener>()
    listeners.add(listener)
    this.listeners.set(event, listeners)
    return this
  }

  off(event: string, listener: Listener): this {
    this.listeners.get(event)?.delete(listener)
    return this
  }

  connect(): this {
    this.connectCount += 1
    return this
  }

  disconnect(): this {
    this.connected = false
    this.disconnectCount += 1
    return this
  }

  timeout(_milliseconds: number) {
    return {
      emitWithAck: async (event: string, payload: unknown) => {
        this.emitted.push({ event, payload })
        const queue = this.acknowledgements.get(event) ?? []
        if (queue.length === 0) throw new Error(`No fake acknowledgement for ${event}`)
        return queue.shift()
      },
    }
  }

  queueAck(event: string, acknowledgement: unknown): void {
    const queue = this.acknowledgements.get(event) ?? []
    queue.push(acknowledgement)
    this.acknowledgements.set(event, queue)
  }

  trigger(event: string, ...args: unknown[]): void {
    if (event === 'connect') this.connected = true
    if (event === 'disconnect') this.connected = false
    for (const listener of this.listeners.get(event) ?? []) listener(...args)
  }
}

function wireMessage(
  overrides: Partial<{
    id: string
    bookId: string
    senderUserId: string
    body: string
    clientMessageId: string
  }> = {},
) {
  return {
    id: overrides.id ?? 'message-1',
    bookId: overrides.bookId ?? BOOK_ID,
    sender: {
      userId: overrides.senderUserId ?? 'user-b',
      displayName: 'Reader B',
      avatarUrl: '',
    },
    body: overrides.body ?? 'Hello',
    clientMessageId: overrides.clientMessageId ?? 'client-message-1',
    createdAt: '2026-07-15T12:00:00.000Z',
  }
}

test('a late refresh from an old identity cannot expire the newly logged-in user', async () => {
  let snapshot = { epoch: 4, userId: 'user-a' as string | null }
  let finishRefresh: ((value: { userId: string } | null) => void) | undefined
  const refresh = new Promise<{ userId: string } | null>((resolve) => {
    finishRefresh = resolve
  })
  const pending = refreshCurrentRealtimeAuthentication({
    getSnapshot: () => snapshot,
    refresh: async () => refresh,
  })

  snapshot = { epoch: 5, userId: 'user-b' }
  finishRefresh?.(null)

  assert.deepEqual(await pending, { status: 'superseded' })
  snapshot = { epoch: 6, userId: 'user-b' }
  assert.deepEqual(
    await refreshCurrentRealtimeAuthentication({
      getSnapshot: () => snapshot,
      refresh: async () => {
        throw new Error('refresh denied')
      },
    }),
    { status: 'expired', epoch: 6 },
  )
})

test('authentication settlement resumes only the same user and never an old controller after account switch', () => {
  assert.equal(
    canResumeRealtimeAfterAuthenticationChange(
      {
        userId: 'user-a',
        accessToken: 'restored-token',
        transitioning: false,
      },
      'user-a',
    ),
    true,
  )
  assert.equal(
    canResumeRealtimeAfterAuthenticationChange(
      {
        userId: 'user-b',
        accessToken: 'new-user-token',
        transitioning: false,
      },
      'user-a',
    ),
    false,
  )
  assert.equal(
    canResumeRealtimeAfterAuthenticationChange(
      {
        userId: null,
        accessToken: null,
        transitioning: true,
      },
      'user-a',
    ),
    false,
  )
})

test('wire messages derive current-user state locally for every browser tab', () => {
  assert.equal(
    toStudyGroupMessage(wireMessage({ senderUserId: 'user-a' }), 'user-a').isCurrentUser,
    true,
  )
  assert.equal(
    toStudyGroupMessage(wireMessage({ senderUserId: 'user-a' }), 'user-b').isCurrentUser,
    false,
  )
})

test('cursor catch-up drains every page without letting a concurrent live cursor skip the gap', async () => {
  const requested: Array<{ after?: string; before?: string }> = []
  const accepted: string[] = []
  let globalRealtimeCursor = 'cursor-start'
  await drainStudyGroupCursorCatchUp({
    initialCursor: 'cursor-start',
    signal: new AbortController().signal,
    fetchPage: async (query) => {
      requested.push(query)
      if (query.after === 'cursor-start') {
        globalRealtimeCursor = 'cursor-live-after-page-one'
        return {
          items: ['message-1', 'message-50'],
          hasMore: true,
          oldestCursor: 'cursor-1',
          newestCursor: 'cursor-50',
        }
      }
      return {
        items: ['message-51', 'message-75'],
        hasMore: false,
        oldestCursor: 'cursor-51',
        newestCursor: 'cursor-75',
      }
    },
    acceptPage: (items) => accepted.push(...items),
  })

  assert.equal(globalRealtimeCursor, 'cursor-live-after-page-one')
  assert.deepEqual(requested, [{ after: 'cursor-start' }, { after: 'cursor-50' }])
  assert.deepEqual(accepted, ['message-1', 'message-50', 'message-51', 'message-75'])
})

test('a failed later catch-up page retries from the last contiguous cursor, not a newer live event', async () => {
  let contiguousCursor = 'cursor-0'
  let liveCursor = 'cursor-0'
  const accepted: string[] = []
  let firstRunCalls = 0

  await assert.rejects(
    drainStudyGroupCursorCatchUp({
      initialCursor: contiguousCursor,
      signal: new AbortController().signal,
      fetchPage: async () => {
        firstRunCalls += 1
        if (firstRunCalls === 1) {
          liveCursor = 'cursor-100'
          return {
            items: ['message-1', 'message-50'],
            hasMore: true,
            oldestCursor: 'cursor-1',
            newestCursor: 'cursor-50',
          }
        }
        throw new Error('second page failed')
      },
      acceptPage: (items) => accepted.push(...items),
      onContiguousCursor: (cursor) => {
        contiguousCursor = cursor
      },
    }),
    /second page failed/,
  )
  assert.equal(liveCursor, 'cursor-100')
  assert.equal(contiguousCursor, 'cursor-50')

  const retryRequests: string[] = []
  await drainStudyGroupCursorCatchUp({
    initialCursor: contiguousCursor,
    signal: new AbortController().signal,
    fetchPage: async (query) => {
      retryRequests.push(query.after ?? '')
      return {
        items: ['message-51', 'message-100'],
        hasMore: false,
        oldestCursor: 'cursor-51',
        newestCursor: 'cursor-100',
      }
    },
    acceptPage: (items) => accepted.push(...items),
    onContiguousCursor: (cursor) => {
      contiguousCursor = cursor
    },
  })

  assert.deepEqual(retryRequests, ['cursor-50'])
  assert.deepEqual(accepted, ['message-1', 'message-50', 'message-51', 'message-100'])
  assert.equal(contiguousCursor, 'cursor-100')
})

test('the realtime controller reports Live only after room join and cursor catch-up', async () => {
  const socket = new FakeSocket()
  const statuses: string[] = []
  let releaseCatchUp: (() => void) | undefined
  const catchUp = new Promise<void>((resolve) => {
    releaseCatchUp = resolve
  })
  socket.queueAck('study-group:join', { ok: true })
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => catchUp,
    refreshAuthentication: async () => ({ status: 'refreshed' }),
    onAuthenticationExpired: () => undefined,
    onMessage: () => undefined,
    onMembershipRequired: () => undefined,
    onError: () => undefined,
    onStatusChange: (status) => statuses.push(status),
    isOnline: () => true,
  })

  controller.start()
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))
  assert.equal(statuses.at(-1), 'retrying')
  assert.deepEqual(socket.emitted[0], { event: 'study-group:join', payload: { bookId: BOOK_ID } })

  releaseCatchUp?.()
  await new Promise((resolve) => setTimeout(resolve, 0))
  assert.equal(statuses.at(-1), 'connected')
  controller.stop()
})

test('reconnecting rejoins the room, catches up again, and filters other-book events', async () => {
  const socket = new FakeSocket()
  const messages: StudyGroupMessage[] = []
  let catchUpCount = 0
  socket.queueAck('study-group:join', { ok: true })
  socket.queueAck('study-group:join', { ok: true })
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => {
      catchUpCount += 1
    },
    refreshAuthentication: async () => ({ status: 'refreshed' }),
    onAuthenticationExpired: () => undefined,
    onMessage: (message) => messages.push(message),
    onMembershipRequired: () => undefined,
    onError: () => undefined,
    onStatusChange: () => undefined,
    isOnline: () => true,
  })

  controller.start()
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))
  socket.trigger('disconnect')
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))
  socket.trigger('study-group:message:new', {
    message: wireMessage({ bookId: '64b64c1f2f4f3a0012345679' }),
  })
  socket.trigger('study-group:message:new', { message: wireMessage({ senderUserId: 'user-a' }) })

  assert.equal(catchUpCount, 2)
  assert.equal(messages.length, 1)
  assert.equal(messages[0]?.isCurrentUser, true)
  controller.stop()
})

test('send uses acknowledgement data and surfaces membership revocation', async () => {
  const socket = new FakeSocket()
  let membershipRevoked = false
  socket.queueAck('study-group:join', { ok: true })
  socket.queueAck('study-group:message:send', {
    ok: true,
    created: true,
    message: wireMessage({ senderUserId: 'user-a', body: 'Sent' }),
  })
  socket.queueAck('study-group:message:send', {
    ok: false,
    error: { code: 'MEMBERSHIP_REQUIRED', message: 'Study group membership required' },
  })
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => undefined,
    refreshAuthentication: async () => ({ status: 'refreshed' }),
    onAuthenticationExpired: () => undefined,
    onMessage: () => undefined,
    onMembershipRequired: () => {
      membershipRevoked = true
    },
    onError: () => undefined,
    onStatusChange: () => undefined,
    isOnline: () => true,
  })
  controller.start()
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))

  const sent = await controller.send({ body: 'Sent', clientMessageId: 'client-message-1' })
  assert.equal(sent.body, 'Sent')
  assert.equal(sent.isCurrentUser, true)
  await assert.rejects(
    controller.send({ body: 'Denied', clientMessageId: 'client-message-2' }),
    /Study group membership required/,
  )
  assert.equal(membershipRevoked, true)
  controller.stop()
})

test('an English-only acknowledgement preserves its code and does not break the next send', async () => {
  const socket = new FakeSocket()
  socket.queueAck('study-group:join', { ok: true })
  socket.queueAck('study-group:message:send', {
    ok: false,
    error: { code: 'ENGLISH_ONLY', message: 'Study group messages must be written in English' },
  })
  socket.queueAck('study-group:message:send', {
    ok: true,
    created: true,
    message: wireMessage({
      senderUserId: 'user-a',
      body: 'I rewrote it in English.',
      clientMessageId: 'client-message-2',
    }),
  })
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => undefined,
    refreshAuthentication: async () => ({ status: 'refreshed' }),
    onAuthenticationExpired: () => undefined,
    onMessage: () => undefined,
    onMembershipRequired: () => undefined,
    onError: () => undefined,
    onStatusChange: () => undefined,
    isOnline: () => true,
  })
  controller.start()
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))

  await assert.rejects(
    controller.send({ body: '中文', clientMessageId: 'client-message-1' }),
    (error: unknown) => error instanceof StudyGroupRealtimeError && error.code === 'ENGLISH_ONLY',
  )
  const confirmed = await controller.send({
    body: 'I rewrote it in English.',
    clientMessageId: 'client-message-2',
  })
  assert.equal(confirmed.body, 'I rewrote it in English.')
  controller.stop()
})

test('offline pause disconnects immediately and online reconnect is explicit', async () => {
  const socket = new FakeSocket()
  const statuses: string[] = []
  let online = true
  let catchUpCount = 0
  socket.queueAck('study-group:join', { ok: true })
  socket.queueAck('study-group:join', { ok: true })
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => {
      catchUpCount += 1
    },
    refreshAuthentication: async () => ({ status: 'refreshed' }),
    onAuthenticationExpired: () => undefined,
    onMessage: () => undefined,
    onMembershipRequired: () => undefined,
    onError: () => undefined,
    onStatusChange: (status) => statuses.push(status),
    isOnline: () => online,
  })
  controller.start()
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))

  online = false
  controller.pause()
  assert.equal(statuses.at(-1), 'paused')
  assert.equal(socket.disconnectCount, 1)
  controller.reconnect()
  assert.equal(socket.connectCount, 1)

  online = true
  controller.reconnect()
  assert.equal(socket.connectCount, 2)
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))
  assert.equal(catchUpCount, 2)
  assert.equal(statuses.at(-1), 'connected')
  controller.stop()
})

test('an unauthorized connect error refreshes the token and reconnects once', async () => {
  const socket = new FakeSocket()
  let refreshCount = 0
  let expiredCount = 0
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => undefined,
    refreshAuthentication: async () => {
      refreshCount += 1
      return { status: 'refreshed' }
    },
    onAuthenticationExpired: () => {
      expiredCount += 1
    },
    onMessage: () => undefined,
    onMembershipRequired: () => undefined,
    onError: () => undefined,
    onStatusChange: () => undefined,
    isOnline: () => true,
  })
  controller.start()
  socket.trigger('connect_error', {
    message: 'Authentication required',
    data: { code: 'UNAUTHORIZED' },
  })
  socket.trigger('connect_error', {
    message: 'Authentication required',
    data: { code: 'UNAUTHORIZED' },
  })
  await new Promise((resolve) => setTimeout(resolve, 0))

  assert.equal(refreshCount, 1)
  assert.equal(expiredCount, 0)
  assert.equal(socket.connectCount, 2)
  controller.stop()
})

test('a temporary join catch-up failure disconnects and retries instead of staying stuck', async () => {
  const socket = new FakeSocket()
  const statuses: string[] = []
  let catchUpCount = 0
  socket.queueAck('study-group:join', { ok: true })
  socket.queueAck('study-group:join', { ok: true })
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => {
      catchUpCount += 1
      if (catchUpCount === 1) throw new Error('temporary catch-up failure')
    },
    refreshAuthentication: async () => ({ status: 'refreshed' }),
    onAuthenticationExpired: () => undefined,
    onMessage: () => undefined,
    onMembershipRequired: () => undefined,
    onError: () => undefined,
    onStatusChange: (status) => statuses.push(status),
    isOnline: () => true,
    retryDelayMs: 0,
  })

  controller.start()
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 5))
  assert.equal(socket.disconnectCount, 1)
  assert.equal(socket.connectCount, 2)

  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))
  assert.equal(catchUpCount, 2)
  assert.equal(statuses.at(-1), 'connected')
  controller.stop()
})

test('stopping during a failed catch-up cancels the scheduled reconnect', async () => {
  const socket = new FakeSocket()
  socket.queueAck('study-group:join', { ok: true })
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => {
      throw new Error('temporary catch-up failure')
    },
    refreshAuthentication: async () => ({ status: 'refreshed' }),
    onAuthenticationExpired: () => undefined,
    onMessage: () => undefined,
    onMembershipRequired: () => undefined,
    onError: () => undefined,
    onStatusChange: () => undefined,
    isOnline: () => true,
    retryDelayMs: 20,
  })

  controller.start()
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))
  controller.stop()
  await new Promise((resolve) => setTimeout(resolve, 30))

  assert.equal(socket.connectCount, 1)
})

test('send rejects an acknowledgement that does not match the active book, user, and client id', async () => {
  const socket = new FakeSocket()
  socket.queueAck('study-group:join', { ok: true })
  socket.queueAck('study-group:message:send', {
    ok: true,
    created: true,
    message: wireMessage({ bookId: '64b64c1f2f4f3a0012345679', senderUserId: 'user-a' }),
  })
  socket.queueAck('study-group:message:send', {
    ok: true,
    created: true,
    message: wireMessage({ senderUserId: 'user-b' }),
  })
  socket.queueAck('study-group:message:send', {
    ok: true,
    created: true,
    message: wireMessage({ senderUserId: 'user-a', clientMessageId: 'different-client-id' }),
  })
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => undefined,
    refreshAuthentication: async () => ({ status: 'refreshed' }),
    onAuthenticationExpired: () => undefined,
    onMessage: () => undefined,
    onMembershipRequired: () => undefined,
    onError: () => undefined,
    onStatusChange: () => undefined,
    isOnline: () => true,
  })
  controller.start()
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))

  for (const clientMessageId of ['client-message-1', 'client-message-1', 'client-message-1']) {
    await assert.rejects(
      controller.send({ body: 'Sent', clientMessageId }),
      /does not match the request/,
    )
  }
  controller.stop()
})

test('stop removes every listener and prevents a late send acknowledgement from updating the old view', async () => {
  const socket = new FakeSocket()
  let resolveSend: ((value: unknown) => void) | undefined
  const delayedAck = new Promise<unknown>((resolve) => {
    resolveSend = resolve
  })
  socket.queueAck('study-group:join', { ok: true })
  socket.queueAck('study-group:message:send', delayedAck)
  const controller = createStudyGroupRealtimeController({
    socket,
    bookId: BOOK_ID,
    userId: 'user-a',
    catchUp: async () => undefined,
    refreshAuthentication: async () => ({ status: 'refreshed' }),
    onAuthenticationExpired: () => undefined,
    onMessage: () => undefined,
    onMembershipRequired: () => undefined,
    onError: () => undefined,
    onStatusChange: () => undefined,
    isOnline: () => true,
  })
  controller.start()
  socket.trigger('connect')
  await new Promise((resolve) => setTimeout(resolve, 0))

  const sending = controller.send({ body: 'Sent', clientMessageId: 'client-message-1' })
  controller.stop()
  resolveSend?.({
    ok: true,
    created: true,
    message: wireMessage({ senderUserId: 'user-a' }),
  })

  await assert.rejects(sending, /changed before the message was acknowledged/)
  for (const event of [
    'connect',
    'disconnect',
    'connect_error',
    'study-group:auth-expired',
    'study-group:message:new',
  ]) {
    assert.equal(socket.listeners.get(event)?.size ?? 0, 0)
  }
})
