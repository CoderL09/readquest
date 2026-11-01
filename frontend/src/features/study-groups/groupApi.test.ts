import assert from 'node:assert/strict'
import test from 'node:test'
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { api } from '../../lib/api.js'
import type { StudyGroupMember } from './contracts.js'
import {
  blockStudyGroupMember,
  createStudyGroupReport,
  deleteStudyGroupMessage,
  getStudyGroupMember,
  getStudyGroupMembers,
  getStudyGroupMessages,
  moderateStudyGroupMember,
  removeStudyGroupMember,
  unblockStudyGroupMember,
} from './groupApi.js'

test('member detail client requests the group-scoped member endpoint and returns its snapshot', async () => {
  const bookId = '64b64c1f2f4f3a0012345678'
  const memberUserId = '64b64c1f2f4f3a0012345679'
  const member: StudyGroupMember = {
    userId: memberUserId,
    displayName: 'Current Profile Name',
    avatarUrl: '/uploads/avatars/current.webp',
    joinedAt: '2026-07-15T09:00:00.000Z',
    isCurrentUser: false,
    percent: 70,
    completedChapters: 4,
    totalChapters: 12,
    lastActiveAt: '2026-07-16T09:00:00.000Z',
    role: 'moderator',
    mutedUntil: '2026-07-16T12:00:00.000Z',
    blockedByCurrentUser: true,
  }
  const legacyResponseMember = {
    ...member,
    progress: 70,
    completedParagraphs: 7,
    totalParagraphs: 10,
    totalReadingMs: 120_000,
    updatedAt: '2026-07-16T09:00:00.000Z',
  }
  let observedConfig: InternalAxiosRequestConfig | undefined
  const originalAdapter = api.defaults.adapter
  const adapter: AxiosAdapter = async (config) => {
    observedConfig = config
    return {
      data: { member: legacyResponseMember },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }
  api.defaults.adapter = adapter

  try {
    assert.deepEqual(await getStudyGroupMember(bookId, memberUserId), member)
    assert.equal(observedConfig?.method, 'get')
    assert.equal(observedConfig?.url, `/study-groups/${bookId}/members/${memberUserId}`)
    for (const legacyField of [
      'progress',
      'completedParagraphs',
      'totalParagraphs',
      'totalReadingMs',
      'updatedAt',
    ]) {
      assert.equal(legacyField in (await getStudyGroupMember(bookId, memberUserId)), false)
    }
  } finally {
    api.defaults.adapter = originalAdapter
  }
})

test('member list client strips legacy fine-grained progress fields', async () => {
  const member = {
    userId: 'reader-2',
    displayName: 'Second Reader',
    avatarUrl: '',
    joinedAt: '2026-07-15T09:00:00.000Z',
    isCurrentUser: false,
    percent: 40,
    completedChapters: 2,
    totalChapters: 5,
    lastActiveAt: '2026-07-16T09:00:00.000Z',
    role: 'member',
    mutedUntil: null,
    blockedByCurrentUser: false,
    completedParagraphs: 48,
    totalParagraphs: 100,
    totalReadingMs: 600_000,
  }
  const originalAdapter = api.defaults.adapter
  api.defaults.adapter = async (config) => ({
    data: {
      members: [member],
      pagination: { page: 1, pageSize: 20, total: 1, hasMore: false },
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })

  try {
    const result = await getStudyGroupMembers('book-1')
    assert.deepEqual(result.members[0], {
      userId: 'reader-2',
      displayName: 'Second Reader',
      avatarUrl: '',
      joinedAt: '2026-07-15T09:00:00.000Z',
      isCurrentUser: false,
      percent: 40,
      completedChapters: 2,
      totalChapters: 5,
      lastActiveAt: '2026-07-16T09:00:00.000Z',
      role: 'member',
      mutedUntil: null,
      blockedByCurrentUser: false,
    })
  } finally {
    api.defaults.adapter = originalAdapter
  }
})

test('message client strips deleted content at the API boundary', async () => {
  const originalAdapter = api.defaults.adapter
  api.defaults.adapter = async (config) => ({
    data: {
      messages: [
        {
          id: 'message-1',
          bookId: 'book-1',
          sender: { userId: 'reader-2', displayName: 'Second Reader', avatarUrl: '' },
          body: '<script>globalThis.compromised=true</script>',
          clientMessageId: 'client-message-1',
          createdAt: '2026-07-15T08:00:00.000Z',
          isCurrentUser: false,
          deleted: true,
          deletedAt: '2026-07-15T09:00:00.000Z',
          deletionReason: 'private audit detail',
        },
      ],
      pageInfo: { hasMore: false, oldestCursor: 'message-1', newestCursor: 'message-1' },
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })

  try {
    const result = await getStudyGroupMessages('book-1')
    assert.equal(result.messages[0].body, '')
    assert.equal(result.messages[0].deleted, true)
    assert.equal('deletionReason' in result.messages[0], false)
  } finally {
    api.defaults.adapter = originalAdapter
  }
})

test('governance clients use the existing group-scoped endpoints and strict request bodies', async () => {
  const bookId = '64b64c1f2f4f3a0012345678'
  const memberUserId = '64b64c1f2f4f3a0012345679'
  const messageId = '64b64c1f2f4f3a0012345680'
  const observed: InternalAxiosRequestConfig[] = []
  const originalAdapter = api.defaults.adapter
  api.defaults.adapter = async (config) => {
    observed.push(config)
    return {
      data: config.url?.endsWith('/reports')
        ? { report: { id: 'report-1', status: 'open' } }
        : undefined,
      status: config.url?.endsWith('/reports') ? 201 : 204,
      statusText: 'OK',
      headers: {},
      config,
    }
  }

  try {
    assert.deepEqual(
      await createStudyGroupReport(bookId, {
        targetType: 'message',
        targetMessageId: messageId,
        reason: 'spam',
        details: 'Repeated promotions',
      }),
      { id: 'report-1', status: 'open' },
    )
    await blockStudyGroupMember(bookId, memberUserId)
    await unblockStudyGroupMember(bookId, memberUserId)
    await moderateStudyGroupMember(bookId, memberUserId, {
      mutedUntil: '2026-08-02T12:00:00.000Z',
      reason: 'Cooling-off period',
    })
    await removeStudyGroupMember(bookId, memberUserId, 'Repeated abuse')
    await deleteStudyGroupMessage(bookId, messageId, 'Personal attack')

    assert.deepEqual(
      observed.map(({ method, url }) => [method, url]),
      [
        ['post', `/study-groups/${bookId}/reports`],
        ['put', `/study-groups/${bookId}/blocks/${memberUserId}`],
        ['delete', `/study-groups/${bookId}/blocks/${memberUserId}`],
        ['patch', `/study-groups/${bookId}/members/${memberUserId}`],
        ['delete', `/study-groups/${bookId}/members/${memberUserId}`],
        ['delete', `/study-groups/${bookId}/messages/${messageId}`],
      ],
    )
    assert.deepEqual(JSON.parse(String(observed[0].data)), {
      targetType: 'message',
      targetMessageId: messageId,
      reason: 'spam',
      details: 'Repeated promotions',
    })
    assert.deepEqual(JSON.parse(String(observed[3].data)), {
      mutedUntil: '2026-08-02T12:00:00.000Z',
      reason: 'Cooling-off period',
    })
    assert.deepEqual(JSON.parse(String(observed[4].data)), { reason: 'Repeated abuse' })
    assert.deepEqual(JSON.parse(String(observed[5].data)), { reason: 'Personal attack' })
  } finally {
    api.defaults.adapter = originalAdapter
  }
})
