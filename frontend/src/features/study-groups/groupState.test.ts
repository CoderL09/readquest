import assert from 'node:assert/strict'
import test from 'node:test'
import type { StudyGroupMember, StudyGroupMessage } from './contracts.js'
import {
  appendUniqueMembers,
  analyzeMessageListChange,
  canChangeStudyGroupRole,
  canModerateStudyGroupMember,
  createStudyGroupComposerRevisionGuard,
  createStudyGroupPrivateRequestGuard,
  isStudyGroupMemberMuted,
  markStudyGroupMessageFailed,
  mergeStudyGroupMessages,
  nextMemberRefreshPage,
  nextPollingDelay,
  reconcileMemberPageRefresh,
  removeStudyGroupPendingMessage,
  routeStudyGroupPrivateRequestFailure,
  shouldApplyMemberRefresh,
  studyGroupRetryRestoreRevision,
  studyGroupMessageIdentity,
} from './groupState.js'

function message(overrides: Partial<StudyGroupMessage> = {}): StudyGroupMessage {
  return {
    id: 'message-1',
    bookId: 'book-1',
    sender: { userId: 'reader-1', displayName: 'A Reader', avatarUrl: '' },
    body: 'Hello',
    clientMessageId: 'client-message-1',
    createdAt: '2026-07-15T08:00:00.000Z',
    isCurrentUser: true,
    deleted: false,
    deletedAt: null,
    delivery: 'sent',
    ...overrides,
  }
}

function member(overrides: Partial<StudyGroupMember> = {}): StudyGroupMember {
  return {
    userId: 'reader-1',
    displayName: 'A Reader',
    avatarUrl: '',
    joinedAt: '2026-07-15T08:00:00.000Z',
    isCurrentUser: false,
    percent: 0,
    completedChapters: 0,
    totalChapters: 0,
    lastActiveAt: null,
    role: 'member',
    mutedUntil: null,
    blockedByCurrentUser: false,
    ...overrides,
  }
}

test('message merge removes cursor overlap and keeps chronological order', () => {
  const current = [
    message({
      id: 'message-2',
      clientMessageId: 'client-2',
      createdAt: '2026-07-15T08:02:00.000Z',
    }),
  ]
  const incoming = [
    message({
      id: 'message-1',
      clientMessageId: 'client-1',
      createdAt: '2026-07-15T08:01:00.000Z',
    }),
    message({
      id: 'message-2',
      clientMessageId: 'client-2',
      createdAt: '2026-07-15T08:02:00.000Z',
      body: 'Confirmed',
    }),
  ]

  const merged = mergeStudyGroupMessages(current, incoming)

  assert.deepEqual(
    merged.map((entry) => entry.id),
    ['message-1', 'message-2'],
  )
  assert.equal(merged[1].body, 'Confirmed')
})

test('a server message replaces its optimistic client message', () => {
  const optimistic = message({
    id: 'pending:client-7',
    clientMessageId: 'client-7',
    delivery: 'sending',
  })
  const confirmed = message({ id: 'server-7', clientMessageId: 'client-7', delivery: 'sent' })

  const merged = mergeStudyGroupMessages([optimistic], [confirmed])

  assert.equal(merged.length, 1)
  assert.equal(merged[0].id, 'server-7')
  assert.equal(merged[0].delivery, 'sent')
  assert.equal(studyGroupMessageIdentity(optimistic), studyGroupMessageIdentity(confirmed))
})

test('a deleted server message replaces its body with an inert placeholder state', () => {
  const deleted = message({
    body: '<img src=x onerror="globalThis.compromised=true">',
    deleted: true,
    deletedAt: '2026-07-15T08:05:00.000Z',
  })

  const [normalized] = mergeStudyGroupMessages([], [deleted])

  assert.equal(normalized.body, '')
  assert.equal(normalized.deleted, true)
  assert.equal(normalized.deletedAt, '2026-07-15T08:05:00.000Z')
})

test('an acknowledgement timeout cannot downgrade a message already confirmed by broadcast', () => {
  const optimistic = message({
    id: 'pending:client-7',
    clientMessageId: 'client-7',
    delivery: 'sending',
  })
  const confirmed = message({ id: 'server-7', clientMessageId: 'client-7', delivery: 'sent' })

  assert.equal(
    markStudyGroupMessageFailed([optimistic], 'reader-1', 'client-7')[0]?.delivery,
    'failed',
  )
  assert.equal(
    markStudyGroupMessageFailed([confirmed], 'reader-1', 'client-7')[0]?.delivery,
    'sent',
  )
})

test('a server English-only rejection removes only its optimistic message', () => {
  const rejected = message({
    id: 'pending:rejected',
    clientMessageId: 'rejected',
    delivery: 'sending',
  })
  const other = message({ id: 'server-other', clientMessageId: 'other', delivery: 'sent' })

  const remaining = removeStudyGroupPendingMessage([other, rejected], 'reader-1', 'rejected')

  assert.deepEqual(
    remaining.map((entry) => entry.id),
    ['server-other'],
  )
})

test('a delayed English-only acknowledgement cannot replace a newer composer draft', async () => {
  const guard = createStudyGroupComposerRevisionGuard()
  const sentRevision = guard.advance()
  let rejectAcknowledgement: ((error: Error) => void) | undefined
  const delayedAcknowledgement = new Promise<never>((_resolve, reject) => {
    rejectAcknowledgement = reject
  })
  const restoreDecision = delayedAcknowledgement.catch(() => guard.canRestore(sentRevision))

  guard.advance()
  rejectAcknowledgement?.(new Error('ENGLISH_ONLY'))

  assert.equal(await restoreDecision, false)
  assert.equal(guard.canRestore(guard.capture()), true)
})

test('retrying an old failed message cannot claim a composer that already has a draft', () => {
  const guard = createStudyGroupComposerRevisionGuard()
  guard.advance()

  const restoreRevision = studyGroupRetryRestoreRevision('Keep this newer draft.', guard.capture())

  assert.equal(restoreRevision, null)
  assert.equal(guard.canRestore(restoreRevision), false)
})

test('different senders may safely use the same client message id', () => {
  const first = message({ id: 'server-a', clientMessageId: 'shared-client-id' })
  const second = message({
    id: 'server-b',
    clientMessageId: 'shared-client-id',
    sender: { userId: 'reader-2', displayName: 'Second Reader', avatarUrl: '' },
    isCurrentUser: false,
  })

  assert.equal(mergeStudyGroupMessages([first], [second]).length, 2)
})

test('a remote message inserted before an optimistic newest message is still new', () => {
  const change = analyzeMessageListChange(
    ['message-a', 'pending-c'],
    ['message-a', 'message-b', 'pending-c'],
  )

  assert.equal(change.tailChanged, false)
  assert.equal(change.newerCount, 1)
})

test('loading older history is classified as prepend-only', () => {
  const change = analyzeMessageListChange(
    ['message-b', 'message-c'],
    ['message-a', 'message-b', 'message-c'],
  )

  assert.equal(change.tailChanged, false)
  assert.equal(change.newerCount, 0)
})

test('confirming an optimistic message does not create a false new-message signal', () => {
  const stableOptimisticIdentity = 'reader-1:client-c'
  const change = analyzeMessageListChange(
    ['reader-1:client-a', stableOptimisticIdentity, 'reader-2:client-b'],
    ['reader-1:client-a', stableOptimisticIdentity, 'reader-2:client-b'],
  )

  assert.equal(change.tailChanged, false)
  assert.equal(change.newerCount, 0)
})

test('member pagination updates existing members without duplicating them', () => {
  const current = [member({ percent: 10 })]
  const incoming = [
    member({ percent: 35 }),
    member({ userId: 'reader-2', displayName: 'Second Reader' }),
  ]

  const merged = appendUniqueMembers(current, incoming)

  assert.equal(merged.length, 2)
  assert.equal(merged[0].percent, 35)
  assert.equal(merged[1].userId, 'reader-2')
})

test('an authoritative member refresh removes readers who have left', () => {
  const current = [member(), member({ userId: 'reader-2', displayName: 'Second Reader' })]
  const refreshed = [member({ percent: 40 })]

  const result = reconcileMemberPageRefresh(current, refreshed, {
    page: 1,
    pageSize: 20,
    total: 1,
    loadedPageCount: 1,
  })

  assert.deepEqual(
    result.members.map((entry) => entry.userId),
    ['reader-1'],
  )
  assert.equal(result.members[0].percent, 40)
  assert.equal(result.loadedPageCount, 1)
  assert.equal(result.hasMore, false)
})

test('a private response that started before membership revocation cannot restore cleared data', async () => {
  const guard = createStudyGroupPrivateRequestGuard()
  let finishRequest: ((value: string[]) => void) | undefined
  const response = new Promise<string[]>((resolve) => {
    finishRequest = resolve
  })
  const privateRows: string[] = []
  guard.activate()
  const requestGeneration = guard.capture()
  const pendingCommit = response.then((rows) => {
    if (guard.canCommit(requestGeneration)) privateRows.push(...rows)
  })

  guard.revoke()
  privateRows.splice(0)
  finishRequest?.(['stale private row'])
  await pendingCommit

  assert.deepEqual(privateRows, [])
  guard.activate()
  const currentGeneration = guard.capture()
  assert.equal(guard.canCommit(requestGeneration), false)
  assert.equal(guard.canCommit(currentGeneration), true)
})

test('a membership failure from any private request is routed to the immediate privacy reset', async () => {
  const events: string[] = []
  const membershipError = { code: 'MEMBERSHIP_REQUIRED' }

  const result = await routeStudyGroupPrivateRequestFailure({
    error: membershipError,
    canCommit: () => true,
    isMembershipRequired: (error) => error === membershipError,
    onMembershipRequired: async () => {
      events.push('membership-required')
    },
    onRequestError: () => {
      events.push('request-error')
    },
  })

  assert.equal(result, 'membership-required')
  assert.deepEqual(events, ['membership-required'])
})

test('a stale private request failure cannot mutate the revoked view', async () => {
  const events: string[] = []

  const result = await routeStudyGroupPrivateRequestFailure({
    error: new Error('late failure'),
    canCommit: () => false,
    isMembershipRequired: () => false,
    onMembershipRequired: () => {
      events.push('membership-required')
    },
    onRequestError: () => {
      events.push('request-error')
    },
  })

  assert.equal(result, 'ignored')
  assert.deepEqual(events, [])
})

test('a current non-membership private failure remains a scoped request error', async () => {
  const events: string[] = []

  const result = await routeStudyGroupPrivateRequestFailure({
    error: new Error('network unavailable'),
    canCommit: () => true,
    isMembershipRequired: () => false,
    onMembershipRequired: () => {
      events.push('membership-required')
    },
    onRequestError: () => {
      events.push('request-error')
    },
  })

  assert.equal(result, 'request-error')
  assert.deepEqual(events, ['request-error'])
})

test('a refresh started before another member page loads is discarded', () => {
  assert.equal(shouldApplyMemberRefresh(1, 1), true)
  assert.equal(shouldApplyMemberRefresh(1, 2), false)
})

test('member polling rotates one loaded page per tick and clamps when the page count shrinks', () => {
  let lastRefreshedPage = 0
  const requestedPages: number[] = []
  for (let tick = 0; tick < 4; tick += 1) {
    const page = nextMemberRefreshPage(lastRefreshedPage, 3)
    requestedPages.push(page)
    lastRefreshedPage = page
  }

  assert.deepEqual(requestedPages, [1, 2, 3, 1])
  assert.equal(nextMemberRefreshPage(3, 1), 1)
})

test('one-page refreshes merge profile changes and converge after membership total shrinks', () => {
  const current = [
    member({ userId: 'reader-1', displayName: 'Left Reader' }),
    member({ userId: 'reader-2', displayName: 'Reader 2', percent: 10 }),
    member({ userId: 'reader-3', displayName: 'Reader 3' }),
    member({ userId: 'reader-4', displayName: 'Reader 4' }),
    member({ userId: 'reader-5', displayName: 'Reader 5' }),
    member({ userId: 'reader-6', displayName: 'Reader 6' }),
  ]

  const result = reconcileMemberPageRefresh(
    current,
    [
      member({ userId: 'reader-2', displayName: 'Reader 2 updated', percent: 70 }),
      member({ userId: 'reader-3', displayName: 'Reader 3' }),
    ],
    { page: 1, pageSize: 2, total: 5, loadedPageCount: 3 },
  )

  assert.deepEqual(
    result.members.map((entry) => entry.userId),
    ['reader-2', 'reader-3', 'reader-4', 'reader-5', 'reader-6'],
  )
  assert.equal(result.members[0].displayName, 'Reader 2 updated')
  assert.equal(result.members[0].percent, 70)
  assert.equal(result.loadedPageCount, 3)
  assert.equal(result.hasMore, false)
})

test('member refresh drops pages beyond a newly smaller total', () => {
  const current = Array.from({ length: 6 }, (_, index) =>
    member({
      userId: `reader-${index + 1}`,
      displayName: `Reader ${index + 1}`,
    }),
  )

  const result = reconcileMemberPageRefresh(current, [], {
    page: 3,
    pageSize: 2,
    total: 3,
    loadedPageCount: 3,
  })

  assert.equal(result.loadedPageCount, 2)
  assert.equal(result.members.length, 3)
  assert.equal(result.hasMore, false)
})

test('member state drops legacy fine-grained progress fields', () => {
  const legacyMember = {
    ...member({ percent: 35, completedChapters: 2, totalChapters: 5 }),
    progress: 35,
    completedParagraphs: 48,
    totalParagraphs: 100,
    totalReadingMs: 600_000,
    updatedAt: '2026-07-15T09:00:00.000Z',
  }

  const [normalized] = appendUniqueMembers([], [legacyMember])

  for (const legacyField of [
    'progress',
    'completedParagraphs',
    'totalParagraphs',
    'totalReadingMs',
    'updatedAt',
  ]) {
    assert.equal(legacyField in normalized, false)
  }
})

test('member governance fields are allowlisted and invalid roles fail closed', () => {
  const rawMember = {
    ...member(),
    role: 'owner',
    mutedUntil: 42,
    blockedByCurrentUser: 'yes',
    canDeleteAnything: true,
  } as unknown as StudyGroupMember

  const [normalized] = appendUniqueMembers([], [rawMember])

  assert.equal(normalized.role, 'member')
  assert.equal(normalized.mutedUntil, null)
  assert.equal(normalized.blockedByCurrentUser, false)
  assert.equal('canDeleteAnything' in normalized, false)
})

test('member moderation permissions use only server roles and preserve hierarchy', () => {
  const ordinaryMember = member({ userId: 'member', role: 'member' })
  const moderator = member({ userId: 'moderator', role: 'moderator' })
  const peerModerator = member({ userId: 'peer-moderator', role: 'moderator' })
  const admin = member({ userId: 'admin', role: 'admin' })

  assert.equal(canModerateStudyGroupMember(ordinaryMember, moderator), false)
  assert.equal(canModerateStudyGroupMember(moderator, ordinaryMember), true)
  assert.equal(canModerateStudyGroupMember(moderator, peerModerator), false)
  assert.equal(canModerateStudyGroupMember(admin, moderator), true)
  assert.equal(canModerateStudyGroupMember(admin, admin), false)
  assert.equal(canChangeStudyGroupRole(moderator, ordinaryMember), false)
  assert.equal(canChangeStudyGroupRole(admin, ordinaryMember), true)
  assert.equal(canChangeStudyGroupRole(admin, admin), false)
})

test('mute status is derived from the server timestamp', () => {
  const now = new Date('2026-08-01T12:00:00.000Z').getTime()

  assert.equal(
    isStudyGroupMemberMuted(member({ mutedUntil: '2026-08-01T12:30:00.000Z' }), now),
    true,
  )
  assert.equal(
    isStudyGroupMemberMuted(member({ mutedUntil: '2026-08-01T11:59:59.000Z' }), now),
    false,
  )
  assert.equal(isStudyGroupMemberMuted(member({ mutedUntil: null }), now), false)
})

test('polling delay backs off and is capped', () => {
  assert.equal(nextPollingDelay(0), 3_000)
  assert.equal(nextPollingDelay(1), 6_000)
  assert.equal(nextPollingDelay(3), 24_000)
  assert.equal(nextPollingDelay(9), 30_000)
})
