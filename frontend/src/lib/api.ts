import { createRestAdapter } from './restAdapter'
import axios, { AxiosError, CanceledError, type InternalAxiosRequestConfig } from 'axios'
import {
  AI_QUOTA_EXCEEDED_EVENT,
  ENTITLEMENT_REQUIRED_EVENT,
  entitlementNoticeFromResponse,
  quotaNoticeFromResponse,
} from '../features/entitlements/entitlementState'
import { resolveApiBaseUrl } from './runtimeEndpoints'

export const apiBaseUrl = resolveApiBaseUrl(import.meta.env?.VITE_API_URL)

export type AuthUser = {
  id: string
  email: string
  displayName: string
  avatarUrl: string
  createdAt: string
}

export type AuthResponse = {
  user: AuthUser
  accessToken: string
}

export type AuthSession = {
  userId: string
  accessToken: string
}

export type AuthSnapshot = {
  epoch: number
  userId: string | null
  accessToken: string | null
  transitioning: boolean
}

export type AuthTransition = {
  epoch: number
  previous: AuthSnapshot
}

export function createAuthSessionState() {
  let epoch = 0
  let userId: string | null = null
  let accessToken: string | null = null
  let transitionEpoch: number | null = null

  const snapshot = (): AuthSnapshot => ({
    epoch,
    userId,
    accessToken,
    transitioning: transitionEpoch !== null,
  })

  return {
    snapshot,
    beginTransition(): AuthTransition {
      if (transitionEpoch !== null) throw new Error('Authentication transition already in progress')
      const previous = snapshot()
      epoch += 1
      transitionEpoch = epoch
      userId = null
      accessToken = null
      return { epoch, previous }
    },
    commitTransition(transition: AuthTransition, session: AuthSession | null): boolean {
      if (transitionEpoch !== transition.epoch || epoch !== transition.epoch) return false
      userId = session?.userId ?? null
      accessToken = session?.accessToken ?? null
      transitionEpoch = null
      return true
    },
    rollbackTransition(transition: AuthTransition): boolean {
      if (transitionEpoch !== transition.epoch || epoch !== transition.epoch) return false
      userId = transition.previous.userId
      accessToken = transition.previous.accessToken
      transitionEpoch = null
      return true
    },
    applyRefresh(startedEpoch: number, session: AuthSession): boolean {
      if (transitionEpoch !== null || epoch !== startedEpoch) return false
      if (userId !== session.userId) epoch += 1
      userId = session.userId
      accessToken = session.accessToken
      return true
    },
    discardAccessTokenIfCurrent(expectedEpoch: number): boolean {
      if (transitionEpoch !== null || epoch !== expectedEpoch) return false
      accessToken = null
      return true
    },
    expireIfCurrent(expectedEpoch: number): boolean {
      if (transitionEpoch !== null || epoch !== expectedEpoch) return false
      epoch += 1
      userId = null
      accessToken = null
      return true
    },
  }
}

type AuthSessionState = ReturnType<typeof createAuthSessionState>

export function createAuthRefreshController(dependencies: {
  state: AuthSessionState
  requestRefresh: () => Promise<AuthResponse>
  onRefreshed: (auth: AuthResponse) => void
}) {
  let pending: Promise<AuthResponse | null> | null = null

  return {
    refresh(): Promise<AuthResponse | null> {
      if (dependencies.state.snapshot().transitioning && !pending) return Promise.resolve(null)
      if (!pending) {
        const startedEpoch = dependencies.state.snapshot().epoch
        const request = dependencies
          .requestRefresh()
          .then((auth) => {
            const applied = dependencies.state.applyRefresh(startedEpoch, {
              userId: auth.user.id,
              accessToken: auth.accessToken,
            })
            if (!applied) return null
            dependencies.onRefreshed(auth)
            return auth
          })
          .catch((error) => {
            dependencies.state.discardAccessTokenIfCurrent(startedEpoch)
            throw error
          })
          .finally(() => {
            if (pending === request) pending = null
          })
        pending = request
      }
      return pending
    },
    async waitForPending(): Promise<void> {
      if (!pending) return
      try {
        await pending
      } catch {
        // The explicit login/logout request determines the transition outcome.
      }
    },
  }
}

export const AUTH_REFRESHED_EVENT = 'readquest:auth-refreshed'
export const AUTH_STATE_CHANGED_EVENT = 'readquest:auth-state-changed'

function notifyAuthenticationStateChanged(): void {
  window.dispatchEvent(new Event(AUTH_STATE_CHANGED_EVENT))
}

const refreshClient = axios.create({ baseURL: apiBaseUrl, withCredentials: true })

export const api = axios.create({ baseURL: apiBaseUrl, withCredentials: true })

api.defaults.adapter = createRestAdapter(axios.getAdapter(api.defaults.adapter))
refreshClient.defaults.adapter = createRestAdapter(axios.getAdapter(refreshClient.defaults.adapter))

const authState = createAuthSessionState()
const authRefresh = createAuthRefreshController({
  state: authState,
  requestRefresh: async () => (await refreshClient.post<AuthResponse>('/auth/refresh')).data,
  onRefreshed: (auth) => {
    window.dispatchEvent(new CustomEvent<AuthResponse>(AUTH_REFRESHED_EVENT, { detail: auth }))
    notifyAuthenticationStateChanged()
  },
})

export function getAuthenticationSnapshot(): AuthSnapshot {
  return authState.snapshot()
}

export function refreshAccessToken(): Promise<AuthResponse | null> {
  return authRefresh.refresh()
}

export function beginAuthenticationTransition(): AuthTransition {
  const transition = authState.beginTransition()
  notifyAuthenticationStateChanged()
  return transition
}

export function commitAuthenticationTransition(
  transition: AuthTransition,
  auth: AuthResponse | null,
): boolean {
  const committed = authState.commitTransition(
    transition,
    auth ? { userId: auth.user.id, accessToken: auth.accessToken } : null,
  )
  if (committed) notifyAuthenticationStateChanged()
  return committed
}

export function rollbackAuthenticationTransition(transition: AuthTransition): boolean {
  const rolledBack = authState.rollbackTransition(transition)
  if (rolledBack) notifyAuthenticationStateChanged()
  return rolledBack
}

export function waitForPendingAuthRefresh(): Promise<void> {
  return authRefresh.waitForPending()
}

export function expireAuthentication(expectedEpoch: number): boolean {
  if (!authState.expireIfCurrent(expectedEpoch)) return false
  notifyAuthenticationStateChanged()
  window.dispatchEvent(new Event('readquest:auth-expired'))
  return true
}

function isSameIdentityEpoch(
  original: Pick<AuthSnapshot, 'epoch' | 'userId'>,
  current: AuthSnapshot,
): boolean {
  return (
    original.userId !== null &&
    current.userId === original.userId &&
    current.epoch === original.epoch &&
    !current.transitioning
  )
}

function isSameAuthenticatedRequest(
  original: Pick<AuthSnapshot, 'epoch' | 'userId'>,
  current: AuthSnapshot,
  refreshedUserId: string,
): boolean {
  return refreshedUserId === original.userId && isSameIdentityEpoch(original, current)
}

type AuthenticatedFetchDependencies = {
  fetchRequest: (input: string | URL, init?: RequestInit) => Promise<Response>
  getAuthSnapshot: () => AuthSnapshot
  refreshAccessToken: () => Promise<AuthSession | null>
  onAuthExpired: (expectedEpoch: number) => void
}

export function createAuthenticatedFetch(dependencies: AuthenticatedFetchDependencies) {
  return async (input: string | URL, init: RequestInit = {}): Promise<Response> => {
    const requestInit = (token: string | null): RequestInit => {
      const headers = new Headers(init.headers)
      if (token) headers.set('Authorization', `Bearer ${token}`)
      else headers.delete('Authorization')
      return { ...init, credentials: 'include', headers }
    }

    const originalAuth = dependencies.getAuthSnapshot()
    const response = await dependencies.fetchRequest(input, requestInit(originalAuth.accessToken))
    await publishFetchAiAccessNotice(response)
    const isAuthRequest = String(input).includes('/auth/')
    if (response.status !== 401 || isAuthRequest || init.signal?.aborted) return response

    let refreshedAuth: AuthSession | null
    try {
      refreshedAuth = await dependencies.refreshAccessToken()
    } catch {
      dependencies.onAuthExpired(originalAuth.epoch)
      return response
    }

    const currentAuth = dependencies.getAuthSnapshot()
    if (
      !refreshedAuth ||
      !isSameAuthenticatedRequest(originalAuth, currentAuth, refreshedAuth.userId) ||
      init.signal?.aborted
    )
      return response

    try {
      await response.body?.cancel()
    } catch {
      // The retry can proceed even if the rejected response body is already closed.
    }
    const retryResponse = await dependencies.fetchRequest(
      input,
      requestInit(refreshedAuth.accessToken),
    )
    await publishFetchAiAccessNotice(retryResponse)
    if (retryResponse.status === 401) dependencies.onAuthExpired(originalAuth.epoch)
    return retryResponse
  }
}

function publishAiAccessNotice(status: number | undefined, body: unknown): void {
  if (typeof window === 'undefined') return
  const entitlementNotice = entitlementNoticeFromResponse(status, body)
  if (entitlementNotice) {
    window.dispatchEvent(new CustomEvent(ENTITLEMENT_REQUIRED_EVENT, { detail: entitlementNotice }))
    return
  }
  const quotaNotice = quotaNoticeFromResponse(status, body)
  if (quotaNotice) {
    window.dispatchEvent(new CustomEvent(AI_QUOTA_EXCEEDED_EVENT, { detail: quotaNotice }))
  }
}

async function publishFetchAiAccessNotice(response: Response): Promise<void> {
  if (response.status !== 402 && response.status !== 429) return
  try {
    publishAiAccessNotice(response.status, await response.clone().json())
  } catch {
    // A malformed access error remains an API failure; it simply cannot drive notice UI.
  }
}

export const authenticatedFetch = createAuthenticatedFetch({
  fetchRequest: (input, init) => fetch(input, init),
  getAuthSnapshot: getAuthenticationSnapshot,
  refreshAccessToken: async () => {
    const auth = await refreshAccessToken()
    return auth ? { userId: auth.user.id, accessToken: auth.accessToken } : null
  },
  onAuthExpired: expireAuthentication,
})

api.interceptors.request.use((config) => {
  const request = config as RetryableRequest
  const auth = authState.snapshot()
  if (request._authRetry) {
    const retryUserId = request._authUserId ?? null
    const retryStillBelongsToCurrentIdentity =
      request._authEpoch !== undefined &&
      isSameIdentityEpoch({ epoch: request._authEpoch, userId: retryUserId }, auth)
    if (!retryStillBelongsToCurrentIdentity || !config.headers.has('Authorization')) {
      throw new CanceledError('Authentication changed before request retry', config)
    }
    return config
  }
  if (request._authEpoch === undefined) {
    request._authEpoch = auth.epoch
    request._authUserId = auth.userId
  }
  if (auth.accessToken) config.headers.set('Authorization', `Bearer ${auth.accessToken}`)
  else config.headers.delete('Authorization')
  return config
})

type RetryableRequest = InternalAxiosRequestConfig & {
  _authRetry?: boolean
  _authEpoch?: number
  _authUserId?: string | null
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    publishAiAccessNotice(error.response?.status, error.response?.data)
    const request = error.config as RetryableRequest | undefined
    const isAuthRequest = request?.url?.includes('/auth/') ?? false
    if (error.response?.status !== 401 || !request || isAuthRequest) {
      throw error
    }

    const requestEpoch = request._authEpoch
    if (requestEpoch === undefined) throw error
    if (request._authRetry) {
      expireAuthentication(requestEpoch)
      throw error
    }

    let auth: AuthResponse | null
    try {
      auth = await refreshAccessToken()
    } catch {
      expireAuthentication(requestEpoch)
      throw error
    }

    const originalAuth = { epoch: requestEpoch, userId: request._authUserId ?? null }
    if (!auth || !isSameAuthenticatedRequest(originalAuth, authState.snapshot(), auth.user.id)) {
      throw error
    }

    request._authRetry = true
    request.headers.set('Authorization', `Bearer ${auth.accessToken}`)
    return api(request)
  },
)

export function mediaUrl(url: string) {
  if (!url || !url.startsWith('/')) return url
  return `${apiBaseUrl.replace(/\/api\/?$/, '')}${url}`
}

export type Book = {
  id: string
  title: string
  author: string
  coverUrl: string
  status: 'uploaded' | 'processing' | 'completed' | 'failed'
  progress: number
  wordCount: number
  totalChapters: number
  readingMode?: 'quest' | 'continuous'
  description?: string | null
  uploaderName?: string | null
  difficultyLevel?: 'starter' | 'easy' | 'medium' | 'advanced' | 'expert' | null
  lexile?: number | null
  createdAt?: string
  publishedAt?: string
  isUploader?: boolean
  inLibrary?: boolean
  shelfState?: 'saved' | 'reading' | 'completed' | null
}

export type BookMetadata = {
  _id: string
  id?: string
  title: string
  author: string
  cover: string
  description: string
  lexile: number
  difficultyLevel: 'starter' | 'easy' | 'medium' | 'advanced' | 'expert'
  genre: string
  sourceUrl: string
  downloadStatus: 'metadataOnly' | 'downloading' | 'downloaded' | 'failed'
  linkedBookId?: string
  createdAt: string
}

export type ReadingProfile = {
  currentLexile: number
  comfortZoneMin: number
  comfortZoneMax: number
  iPlusOneMin: number
  iPlusOneMax: number
  totalReadingMinutes: number
  averageLookupPerSentence: number
  averageComprehensionScore: number
  updatedAt: string
}

export type LibraryBand = {
  label: string
  min: number
  max: number
  books: BookMetadata[]
}

export type LibraryMetadataResponse = {
  profile: ReadingProfile
  recommended: BookMetadata | null
  bands: LibraryBand[]
}

export type CalibrationSample = {
  id: string
  lexile: number
  title: string
  text: string
  target: {
    text: string
    normalized: string
    start?: number
    end?: number
  } | null
}

export type CalibrationStartResponse = {
  sessionId: string
  currentStep: number
  totalSteps: number
  estimatedLexile: number
  sample: CalibrationSample
}

export type CalibrationSubmitResponse = {
  completed: boolean
  sessionId: string
  currentStep?: number
  totalSteps?: number
  estimatedLexile?: number
  sample?: CalibrationSample
  profile?: ReadingProfile
}

export type ReadingPassportResponse = {
  profile: ReadingProfile
  comfortableBooks: BookMetadata[]
  challengeBooks: BookMetadata[]
  estimatedJourney: {
    dailyMinutes: number
    timeframe: string
    milestone: string
  }
}

export type LookupType = 'word' | 'phrase' | 'sentence' | 'grammar' | 'sentencePattern'

export type LookupItemResponse = {
  id: string
  userId: string
  bookId: string
  chapterId: string | null
  paragraphId: string | null
  paragraphIndex: number | null
  sentenceId: string | null
  chunkId: string | null
  sourceSentenceKey: string
  type: LookupType
  text: string
  reviewStatus: 'new' | 'learning' | 'reviewing' | 'mastered'
  masteryLevel: number
  nextReviewAt: string
}

export type DueReviewItem = {
  id: string
  type: LookupType
  text: string
  originalSentence: string
  masteryLevel: number
  prompt: {
    type: 'context_choice' | 'cloze' | 'same_pattern' | 'meaning_check'
    prompt: string
    correctAnswer: string
    choices: string[]
  }
}

export type DueReviewResponse = {
  dueCount: number
  items: DueReviewItem[]
}

export type GrowthSummaryResponse = {
  profile: ReadingProfile
  lookup: {
    total: number
    due: number
    mastered: number
    learning: number
    reviewing: number
  }
  review: {
    attempts: number
    correct: number
    accuracy: number
  }
  reading: {
    totalReadingMinutes: number
    completedBooks: number
    activeBook: {
      id: string
      title: string
      author: string
      coverUrl: string
      progress: number
      totalChapters: number
      completedParagraphs: number
      totalParagraphs: number
    } | null
    bookProgress: {
      bookId: string
      completedParagraphs: number
      totalParagraphs: number
      totalReadingMs: number
      progress: number
      updatedAt: string
    }[]
    recentProgress: {
      id: string
      bookId: string
      chapterId: string
      currentParagraphIndex: number
      completedParagraphs: number
      totalParagraphs: number
      totalReadingMs: number
      updatedAt: string
    }[]
  }
  flow: {
    averageSupportPerParagraph: number
    totalSupport: number
    quietParagraphs: number
    supportTrend: 'notEnoughData' | 'down' | 'up' | 'stable'
    recentParagraphs: Array<{
      paragraphIndex: number
      supportCount: number
      wordCount: number
      phraseCount: number
      grammarCount: number
    }>
  }
  adjustment: {
    direction: 'up' | 'down' | 'stable'
    lexileDelta: number
    confidence: number
    reason: string
    signals: {
      completedParagraphs: number
      averageSupportPerParagraph: number
      quietRatio: number
      reviewAccuracy: number
      reviewAttempts: number
    }
  }
  feedback: string[]
}
