import assert from 'node:assert/strict'
import test from 'node:test'
import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

Object.defineProperty(globalThis, 'window', {
  configurable: true,
  value: {
    location: { protocol: 'http:', hostname: '127.0.0.1' },
    dispatchEvent: () => true,
  },
})

let axiosAdapter: AxiosAdapter = async () => {
  throw new Error('Unexpected Axios request in test')
}
axios.defaults.adapter = (config) => axiosAdapter(config)

const {
  api,
  beginAuthenticationTransition,
  commitAuthenticationTransition,
  createAuthenticatedFetch,
  createAuthRefreshController,
  createAuthSessionState,
  getAuthenticationSnapshot,
  refreshAccessToken,
} = await import('./api')

type FetchCall = {
  input: string | URL
  init?: RequestInit
}

type TestAuth = {
  accessToken: string
  userId: string
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function authResponse(userId: string, accessToken: string) {
  return {
    accessToken,
    user: {
      id: userId,
      email: `${userId}@example.com`,
      displayName: userId,
      avatarUrl: '',
      createdAt: '2026-01-01T00:00:00.000Z',
    },
  }
}

function installSession(state: ReturnType<typeof createAuthSessionState>, auth: TestAuth) {
  const transition = state.beginTransition()
  assert.equal(state.commitTransition(transition, auth), true)
}

function axiosResponse<T>(
  config: InternalAxiosRequestConfig,
  status: number,
  data: T,
): AxiosResponse<T> {
  return {
    config,
    data,
    headers: new AxiosHeaders(),
    status,
    statusText: status === 200 ? 'OK' : 'Unauthorized',
  }
}

function rejectUnauthorized(config: InternalAxiosRequestConfig): never {
  const response = axiosResponse(config, 401, { error: 'Unauthorized' })
  throw new AxiosError(
    'Request failed with status code 401',
    'ERR_BAD_REQUEST',
    config,
    undefined,
    response,
  )
}

test('parallel refresh callers share one in-flight refresh request', async () => {
  const state = createAuthSessionState()
  installSession(state, { userId: 'reader-a', accessToken: 'expired-a' })
  const pendingResponse = deferred<ReturnType<typeof authResponse>>()
  let requestCount = 0
  const refresh = createAuthRefreshController({
    state,
    requestRefresh: () => {
      requestCount += 1
      return pendingResponse.promise
    },
    onRefreshed: () => undefined,
  })

  const first = refresh.refresh()
  const second = refresh.refresh()

  assert.equal(requestCount, 1)
  pendingResponse.resolve(authResponse('reader-a', 'fresh-a'))
  const [firstResult, secondResult] = await Promise.all([first, second])
  assert.equal(firstResult?.accessToken, 'fresh-a')
  assert.equal(secondResult?.accessToken, 'fresh-a')
  assert.equal(state.snapshot().accessToken, 'fresh-a')
})

test('a refresh started before a newer login cannot overwrite the new session', async () => {
  const state = createAuthSessionState()
  installSession(state, { userId: 'reader-a', accessToken: 'expired-a' })
  const pendingResponse = deferred<ReturnType<typeof authResponse>>()
  let refreshedEvents = 0
  const refresh = createAuthRefreshController({
    state,
    requestRefresh: () => pendingResponse.promise,
    onRefreshed: () => {
      refreshedEvents += 1
    },
  })

  const staleRefresh = refresh.refresh()
  const login = state.beginTransition()
  assert.equal(state.commitTransition(login, { userId: 'reader-b', accessToken: 'fresh-b' }), true)
  pendingResponse.resolve(authResponse('reader-a', 'late-a'))

  assert.equal(await staleRefresh, null)
  assert.deepEqual(state.snapshot(), {
    epoch: login.epoch,
    userId: 'reader-b',
    accessToken: 'fresh-b',
    transitioning: false,
  })
  assert.equal(refreshedEvents, 0)
})

test('a failed refresh from an older epoch cannot clear a newer login', async () => {
  const state = createAuthSessionState()
  installSession(state, { userId: 'reader-a', accessToken: 'expired-a' })
  const pendingResponse = deferred<ReturnType<typeof authResponse>>()
  const refresh = createAuthRefreshController({
    state,
    requestRefresh: () => pendingResponse.promise,
    onRefreshed: () => assert.fail('a failed refresh must not publish an identity event'),
  })

  const staleRefresh = refresh.refresh()
  const rejection = assert.rejects(staleRefresh, /late refresh failure/)
  const login = state.beginTransition()
  assert.equal(state.commitTransition(login, { userId: 'reader-b', accessToken: 'fresh-b' }), true)
  pendingResponse.reject(new Error('late refresh failure'))

  await rejection
  assert.equal(state.snapshot().userId, 'reader-b')
  assert.equal(state.snapshot().accessToken, 'fresh-b')
})

test('a refresh started before logout cannot restore the logged-out session', async () => {
  const state = createAuthSessionState()
  installSession(state, { userId: 'reader-a', accessToken: 'expired-a' })
  const pendingResponse = deferred<ReturnType<typeof authResponse>>()
  const refresh = createAuthRefreshController({
    state,
    requestRefresh: () => pendingResponse.promise,
    onRefreshed: () => assert.fail('stale refresh must not publish an identity event'),
  })

  const staleRefresh = refresh.refresh()
  const logout = state.beginTransition()
  assert.equal(state.commitTransition(logout, null), true)
  pendingResponse.resolve(authResponse('reader-a', 'late-a'))

  assert.equal(await staleRefresh, null)
  assert.equal(state.snapshot().userId, null)
  assert.equal(state.snapshot().accessToken, null)
})

test('rolling back a failed transition restores the session at a newer epoch', () => {
  const state = createAuthSessionState()
  installSession(state, { userId: 'reader-a', accessToken: 'access-a' })
  const oldEpoch = state.snapshot().epoch
  const transition = state.beginTransition()

  assert.equal(state.rollbackTransition(transition), true)
  assert.equal(state.snapshot().userId, 'reader-a')
  assert.equal(state.snapshot().accessToken, 'access-a')
  assert.ok(state.snapshot().epoch > oldEpoch)
})

test('authenticated fetch attaches the in-memory token and credentials', async () => {
  const calls: FetchCall[] = []
  const request = createAuthenticatedFetch({
    fetchRequest: async (input, init) => {
      calls.push({ input, init })
      return new Response('ok', { status: 200 })
    },
    getAuthSnapshot: () => ({
      epoch: 1,
      userId: 'reader-a',
      accessToken: 'access-one',
      transitioning: false,
    }),
    refreshAccessToken: async () => ({ userId: 'reader-a', accessToken: 'unused' }),
    onAuthExpired: () => undefined,
  })

  const response = await request('http://127.0.0.1:4000/api/protected', {
    headers: { Accept: 'text/event-stream' },
  })

  assert.equal(response.status, 200)
  assert.equal(calls.length, 1)
  assert.equal(calls[0]?.init?.credentials, 'include')
  assert.equal(new Headers(calls[0]?.init?.headers).get('Authorization'), 'Bearer access-one')
  assert.equal(new Headers(calls[0]?.init?.headers).get('Accept'), 'text/event-stream')
})

test('authenticated fetch publishes quota UI only for stable QUOTA_EXCEEDED 429 responses', async () => {
  const events: Event[] = []
  const testWindow = globalThis.window as unknown as { dispatchEvent: (event: Event) => boolean }
  const originalDispatch = testWindow.dispatchEvent
  testWindow.dispatchEvent = (event) => {
    events.push(event)
    return true
  }
  let responseCode = 'RATE_LIMITED'
  const request = createAuthenticatedFetch({
    fetchRequest: async () =>
      new Response(
        JSON.stringify({
          error: { code: responseCode, message: 'Daily AI request limit reached' },
        }),
        {
          status: 429,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    getAuthSnapshot: () => ({
      epoch: 1,
      userId: 'reader-a',
      accessToken: 'access-one',
      transitioning: false,
    }),
    refreshAccessToken: async () => ({ userId: 'reader-a', accessToken: 'unused' }),
    onAuthExpired: () => undefined,
  })

  try {
    await request('http://127.0.0.1:4000/api/ai')
    assert.equal(events.length, 0)

    responseCode = 'QUOTA_EXCEEDED'
    await request('http://127.0.0.1:4000/api/ai')
    assert.equal(events.length, 1)
    assert.equal(events[0]?.type, 'readquest:ai-quota-exceeded')
    assert.deepEqual((events[0] as CustomEvent).detail, {
      message: 'Daily AI request limit reached',
    })
  } finally {
    testWindow.dispatchEvent = originalDispatch
  }
})

test('reader conversation SSE uses the authenticated REST path', async () => {
  const transition = beginAuthenticationTransition()
  assert.equal(
    commitAuthenticationTransition(transition, authResponse('reader-stream', 'stream-access')),
    true,
  )

  const originalFetch = globalThis.fetch
  const calls: FetchCall[] = []
  globalThis.fetch = async (input, init) => {
    calls.push({ input, init })
    return new Response(
      'data: {"type":"token","delta":"A short answer."}\n\ndata: {"type":"done"}\n\n',
      {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      },
    )
  }

  try {
    const { streamReaderConversation } =
      await import('../features/reader/core/readerConversationStream')
    const events: unknown[] = []
    await streamReaderConversation({
      bookId: 'book',
      sentence: 'A short paragraph.',
      sentenceId: 'local:1',
      question: 'Explain it',
      history: [],
      signal: new AbortController().signal,
      onToken: (event) => events.push(event),
    })
    assert.equal(String(calls[0]?.input), '/api/books/book/reading-conversations')
    assert.equal(JSON.parse(String(calls[0]?.init?.body)).sourceSentenceKey, 'local:1')

    assert.equal(calls.length, 1)
    assert.equal(new Headers(calls[0]?.init?.headers).get('Authorization'), 'Bearer stream-access')
    assert.equal(calls[0]?.init?.credentials, 'include')
    assert.equal(events.length, 1)
  } finally {
    globalThis.fetch = originalFetch
    const logout = beginAuthenticationTransition()
    assert.equal(commitAuthenticationTransition(logout, null), true)
  }
})

test('authenticated fetch refreshes once and retries for the same identity epoch', async () => {
  const calls: FetchCall[] = []
  let snapshot = {
    epoch: 1,
    userId: 'reader-a',
    accessToken: 'expired-access',
    transitioning: false,
  }
  let refreshCount = 0
  const request = createAuthenticatedFetch({
    fetchRequest: async (input, init) => {
      calls.push({ input, init })
      return new Response(calls.length === 1 ? 'expired' : 'stream', {
        status: calls.length === 1 ? 401 : 200,
      })
    },
    getAuthSnapshot: () => snapshot,
    refreshAccessToken: async () => {
      refreshCount += 1
      snapshot = { ...snapshot, accessToken: 'refreshed-access' }
      return { userId: 'reader-a', accessToken: snapshot.accessToken }
    },
    onAuthExpired: () => undefined,
  })

  const response = await request('http://127.0.0.1:4000/api/books/book-1/reader-conversation', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: 'Why?' }),
  })

  assert.equal(response.status, 200)
  assert.equal(refreshCount, 1)
  assert.equal(calls.length, 2)
  assert.equal(new Headers(calls[0]?.init?.headers).get('Authorization'), 'Bearer expired-access')
  assert.equal(new Headers(calls[1]?.init?.headers).get('Authorization'), 'Bearer refreshed-access')
  assert.equal(calls[1]?.init?.body, calls[0]?.init?.body)
})

test('authenticated fetch never replays an old request as a different refreshed user', async () => {
  let snapshot = { epoch: 1, userId: 'reader-a', accessToken: 'expired-a', transitioning: false }
  let fetchCount = 0
  let expiredEventCount = 0
  const request = createAuthenticatedFetch({
    fetchRequest: async () => {
      fetchCount += 1
      return new Response('expired', { status: 401 })
    },
    getAuthSnapshot: () => snapshot,
    refreshAccessToken: async () => {
      snapshot = { epoch: 2, userId: 'reader-b', accessToken: 'fresh-b', transitioning: false }
      return { userId: 'reader-b', accessToken: 'fresh-b' }
    },
    onAuthExpired: () => {
      expiredEventCount += 1
    },
  })

  const response = await request('http://127.0.0.1:4000/api/reading-progress', {
    method: 'PUT',
    body: JSON.stringify({ chapterId: 'chapter-from-a' }),
  })

  assert.equal(response.status, 401)
  assert.equal(fetchCount, 1)
  assert.equal(expiredEventCount, 0)
})

test('authenticated fetch expires the current session when the retry is also unauthorized', async () => {
  let snapshot = { epoch: 1, userId: 'reader-a', accessToken: 'expired-a', transitioning: false }
  let fetchCount = 0
  const expiredEpochs: number[] = []
  const request = createAuthenticatedFetch({
    fetchRequest: async () => {
      fetchCount += 1
      return new Response('expired', { status: 401 })
    },
    getAuthSnapshot: () => snapshot,
    refreshAccessToken: async () => {
      snapshot = { ...snapshot, accessToken: 'fresh-a' }
      return { userId: 'reader-a', accessToken: 'fresh-a' }
    },
    onAuthExpired: (epoch) => {
      expiredEpochs.push(epoch)
    },
  })

  const response = await request('http://127.0.0.1:4000/api/protected')

  assert.equal(response.status, 401)
  assert.equal(fetchCount, 2)
  assert.deepEqual(expiredEpochs, [1])
})

test('authenticated fetch expires the current session when refresh fails', async () => {
  let expiredEventCount = 0
  let fetchCount = 0
  const request = createAuthenticatedFetch({
    fetchRequest: async () => {
      fetchCount += 1
      return new Response('expired', { status: 401 })
    },
    getAuthSnapshot: () => ({
      epoch: 1,
      userId: 'reader-a',
      accessToken: 'expired-access',
      transitioning: false,
    }),
    refreshAccessToken: async () => {
      throw new Error('refresh failed')
    },
    onAuthExpired: () => {
      expiredEventCount += 1
    },
  })

  const response = await request('http://127.0.0.1:4000/api/protected')

  assert.equal(response.status, 401)
  assert.equal(fetchCount, 1)
  assert.equal(expiredEventCount, 1)
})

test('axios cancels a 401 retry when the identity changes before the adapter dispatch', async () => {
  const session = beginAuthenticationTransition()
  assert.equal(commitAuthenticationTransition(session, authResponse('reader-a', 'expired-a')), true)

  const previousAdapter = axiosAdapter
  let protectedDispatches = 0
  axiosAdapter = async (config) => {
    if (config.url === '/sessions/current/tokens') {
      return axiosResponse(config, 200, authResponse('reader-a', 'fresh-a'))
    }
    if (config.url === '/books/book/chapters/chapter-from-reader-a/reading-events') {
      protectedDispatches += 1
      if (protectedDispatches === 1) rejectUnauthorized(config)
      return axiosResponse(config, 200, { saved: true })
    }
    throw new Error(`Unexpected Axios URL: ${config.url}`)
  }

  let changedIdentity = false
  const transitionBeforeRetry = api.interceptors.request.use((config) => {
    const request = config as InternalAxiosRequestConfig & { _authRetry?: boolean }
    if (request._authRetry && !changedIdentity) {
      changedIdentity = true
      const transition = beginAuthenticationTransition()
      assert.equal(
        commitAuthenticationTransition(transition, authResponse('reader-b', 'fresh-b')),
        true,
      )
    }
    return config
  })

  try {
    await assert.rejects(
      api.post('/books/book/chapters/chapter-from-reader-a/reading-events', {
        eventId: 'auth-retry-event',
      }),
      (error: unknown) => axios.isCancel(error),
    )
    assert.equal(changedIdentity, true)
    assert.equal(protectedDispatches, 1)
  } finally {
    api.interceptors.request.eject(transitionBeforeRetry)
    axiosAdapter = previousAdapter
    const cleanup = beginAuthenticationTransition()
    assert.equal(commitAuthenticationTransition(cleanup, null), true)
    assert.equal(getAuthenticationSnapshot().userId, null)
  }
})

test('axios preserves the token bound to a retry when the same identity refreshes again before dispatch', async () => {
  const session = beginAuthenticationTransition()
  assert.equal(commitAuthenticationTransition(session, authResponse('reader-a', 'expired-a')), true)

  const previousAdapter = axiosAdapter
  let refreshDispatches = 0
  const protectedAuthorization: Array<string | null> = []
  axiosAdapter = async (config) => {
    if (config.url === '/sessions/current/tokens') {
      refreshDispatches += 1
      const token = refreshDispatches === 1 ? 'fresh-a' : 'newer-a'
      return axiosResponse(config, 200, authResponse('reader-a', token))
    }
    if (config.url === '/books/book/chapters/chapter-from-reader-a/reading-events') {
      protectedAuthorization.push(config.headers.get('Authorization')?.toString() ?? null)
      if (protectedAuthorization.length === 1) rejectUnauthorized(config)
      return axiosResponse(config, 200, { saved: true })
    }
    throw new Error(`Unexpected Axios URL: ${config.url}`)
  }

  let refreshedAgain = false
  const refreshBeforeRetry = api.interceptors.request.use(async (config) => {
    const request = config as InternalAxiosRequestConfig & { _authRetry?: boolean }
    if (request._authRetry && !refreshedAgain) {
      refreshedAgain = true
      const auth = await refreshAccessToken()
      assert.equal(auth?.accessToken, 'newer-a')
    }
    return config
  })

  try {
    const response = await api.post('/books/book/chapters/chapter-from-reader-a/reading-events', {
      eventId: 'auth-retry-event',
    })
    assert.equal(response.status, 200)
    assert.equal(refreshedAgain, true)
    assert.equal(refreshDispatches, 2)
    assert.deepEqual(protectedAuthorization, ['Bearer expired-a', 'Bearer fresh-a'])
  } finally {
    api.interceptors.request.eject(refreshBeforeRetry)
    axiosAdapter = previousAdapter
    const cleanup = beginAuthenticationTransition()
    assert.equal(commitAuthenticationTransition(cleanup, null), true)
  }
})
