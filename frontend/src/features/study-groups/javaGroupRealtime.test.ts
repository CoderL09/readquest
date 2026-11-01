import assert from 'node:assert/strict'
import test from 'node:test'
import { createJavaGroupSocket } from './javaGroupRealtime'
import { mergeStudyGroupMessages } from './groupState'
import type { StudyGroupMessage } from './contracts'

test('REST bridge resumes from its event cursor and keeps message submissions idempotent', async () => {
  const calls: any[] = []
  const socket = createJavaGroupSocket(async (config) => {
    calls.push(config)
    return config.method === 'post'
      ? { status: 200, data: { message: { id: 'message-1' } } }
      : { status: 200, data: { cursor: '17', events: [], hasMore: false } }
  })
  try {
    socket.connect()
    await Promise.resolve()
    assert.equal(socket.connected, true)
    assert.deepEqual(
      await socket.timeout(8000).emitWithAck('study-group:join', { bookId: 'book-1' }),
      { ok: true },
    )
    const ack = await socket.timeout(8000).emitWithAck('study-group:message:send', {
      bookId: 'book-1',
      body: 'Hello there.',
      clientMessageId: 'same-id',
    })
    assert.deepEqual(ack, { ok: true, created: false, message: { id: 'message-1' } })
    assert.deepEqual(calls[1].data, { body: 'Hello there.', clientMessageId: 'same-id' })
    socket.disconnect()
    socket.connect()
    await Promise.resolve()
    await socket.timeout(8000).emitWithAck('study-group:join', { bookId: 'book-1' })
    assert.deepEqual(calls[2].params, { after: '17' })
    await socket.timeout(8000).emitWithAck('study-group:join', { bookId: 'book-2' })
    assert.equal(calls[3].params, undefined)
  } finally {
    socket.disconnect()
  }
})

test('REST bridge aborts outstanding requests and suppresses stale group events', async () => {
  let finish!: (result: any) => void
  let signal: AbortSignal | undefined
  const received: unknown[] = []
  const socket = createJavaGroupSocket((config) => {
    signal = config.signal as AbortSignal
    return new Promise((resolve) => {
      finish = resolve
    })
  })
  socket.on('study-group:message:new', (value) => received.push(value))
  socket.connect()
  await Promise.resolve()
  const joining = socket.timeout(8000).emitWithAck('study-group:join', { bookId: 'book-1' })
  socket.disconnect()
  assert.equal(signal?.aborted, true)
  finish({
    status: 200,
    data: { cursor: '18', events: [{ type: 'message', message: { id: 'private' } }] },
  })
  await joining
  assert.deepEqual(received, [])
  assert.equal(socket.connected, false)
})

test('redacted senders merge by message ID and stale history cannot restore deleted text', () => {
  const original: StudyGroupMessage = {
    id: '1',
    bookId: 'b',
    body: 'Old text',
    sender: { userId: 'reader', displayName: 'Reader', avatarUrl: '' },
    clientMessageId: 'c',
    createdAt: '2026-01-01T00:00:00Z',
    isCurrentUser: false,
    deleted: false,
    deletedAt: null,
  }
  const redacted = {
    ...original,
    sender: { userId: '', displayName: 'Former member', avatarUrl: '' },
  }
  const merged = mergeStudyGroupMessages([original], [redacted])
  assert.equal(merged.length, 1)
  assert.equal(merged[0].sender.userId, '')
  const deleted = { ...redacted, deleted: true, deletedAt: '2026-01-02T00:00:00Z' }
  const result = mergeStudyGroupMessages([deleted], [redacted])
  assert.equal(result.length, 1)
  assert.equal(result[0].body, '')
  assert.equal(result[0].deleted, true)
})
