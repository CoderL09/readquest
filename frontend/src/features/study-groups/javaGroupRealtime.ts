import axios, { type AxiosRequestConfig } from 'axios'
import type { RealtimeSocket } from './groupRealtime'

type Transport = (config: AxiosRequestConfig) => Promise<{ status: number; data: unknown }>
type Listener = (...args: unknown[]) => void
type Row = Record<string, unknown>
const row = (value: unknown): Row => (value && typeof value === 'object' ? (value as Row) : {})

/** Implements the existing connection controller using authenticated REST events and message POSTs. */
export function createJavaGroupSocket(
  transport: Transport = async (config) => (await import('../../lib/api')).api.request(config),
): RealtimeSocket {
  const listeners = new Map<string, Set<Listener>>()
  const requests = new Set<AbortController>()
  let wanted = false
  let generation = 0
  let bookId = ''
  let cursor: string | null = null
  let timer: ReturnType<typeof setTimeout> | null = null
  const emit = (event: string, value?: unknown) => {
    for (const listener of listeners.get(event) ?? []) listener(value)
  }
  const clearTimer = () => {
    if (timer !== null) clearTimeout(timer)
    timer = null
  }
  const current = (expected: number) => wanted && generation === expected
  const path = () => `/study-groups/${encodeURIComponent(bookId)}`

  function failure(error: unknown) {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    const detail = row(axios.isAxiosError(error) ? row(error.response?.data).error : null)
    const code =
      status === 401
        ? 'UNAUTHORIZED'
        : status === 404
          ? 'MEMBERSHIP_REQUIRED'
          : typeof detail.code === 'string'
            ? detail.code
            : status === 403
              ? 'MEMBERSHIP_REQUIRED'
              : 'NETWORK_ERROR'
    return {
      code,
      message:
        typeof detail.message === 'string'
          ? detail.message
          : 'Group connection could not complete the request',
    }
  }
  async function request(config: AxiosRequestConfig, milliseconds: number) {
    const controller = new AbortController()
    requests.add(controller)
    try {
      return await transport({ ...config, timeout: milliseconds, signal: controller.signal })
    } finally {
      requests.delete(controller)
    }
  }
  function accept(value: unknown, expected: number) {
    const data = row(value)
    if (typeof data.cursor !== 'string' || !Array.isArray(data.events))
      throw new Error('Invalid group event response')
    if (!current(expected)) return
    for (const event of data.events) {
      if (!current(expected)) return
      const item = row(event)
      if (item.type === 'message') emit('study-group:message:new', { message: item.message })
    }
    cursor = data.cursor
  }
  async function poll(expected: number) {
    if (!current(expected) || !socket.connected) return
    try {
      const result = await request(
        { url: `${path()}/events`, method: 'get', params: { after: cursor } },
        8000,
      )
      accept(result.data, expected)
      if (!current(expected)) return
      clearTimer()
      timer = setTimeout(
        () => {
          timer = null
          void poll(expected)
        },
        row(result.data).hasMore ? 0 : 1000,
      )
    } catch (error) {
      if (!current(expected)) return
      const problem = failure(error)
      if (problem.code === 'MEMBERSHIP_REQUIRED') {
        emit('study-group:membership-required')
        return
      }
      if (problem.code === 'UNAUTHORIZED') {
        emit('study-group:auth-expired')
        return
      }
      socket.connected = false
      emit('disconnect')
      emit('connect_error', { error: problem })
      if (current(expected))
        timer = setTimeout(() => {
          timer = null
          socket.connect()
        }, 1000)
    }
  }
  async function invoke(event: string, input: unknown, milliseconds: number) {
    const payload = row(input)
    let expected = generation
    if (!wanted || !socket.connected) throw new Error('Group connection is paused')
    try {
      if (event === 'study-group:join') {
        if (typeof payload.bookId !== 'string' || !payload.bookId)
          throw new Error('A group is required')
        if (payload.bookId !== bookId) {
          generation++
          expected = generation
          clearTimer()
          for (const controller of requests) controller.abort()
          bookId = payload.bookId
          cursor = null
        }
        const result = await request(
          {
            url: `${path()}/events`,
            method: 'get',
            params: cursor === null ? undefined : { after: cursor },
          },
          milliseconds,
        )
        accept(result.data, expected)
        if (!current(expected)) throw new Error('Group connection changed')
        clearTimer()
        timer = setTimeout(
          () => {
            timer = null
            void poll(expected)
          },
          row(result.data).hasMore ? 0 : 1000,
        )
        return { ok: true }
      }
      if (event === 'study-group:message:send' && payload.bookId === bookId) {
        const result = await request(
          {
            url: `${path()}/messages`,
            method: 'post',
            data: { body: payload.body, clientMessageId: payload.clientMessageId },
          },
          milliseconds,
        )
        return { ok: true, created: result.status === 201, message: row(result.data).message }
      }
      throw new Error('Unknown group operation')
    } catch (error) {
      return { ok: false, error: failure(error) }
    }
  }
  const socket: RealtimeSocket = {
    connected: false,
    on(event, listener) {
      if (!listeners.has(event)) listeners.set(event, new Set())
      listeners.get(event)!.add(listener)
      return socket
    },
    off(event, listener) {
      listeners.get(event)?.delete(listener)
      return socket
    },
    connect() {
      wanted = true
      if (socket.connected) return socket
      clearTimer()
      const expected = ++generation
      queueMicrotask(() => {
        if (current(expected)) {
          socket.connected = true
          emit('connect')
        }
      })
      return socket
    },
    disconnect() {
      wanted = false
      generation++
      clearTimer()
      for (const controller of requests) controller.abort()
      requests.clear()
      const connected = socket.connected
      socket.connected = false
      if (connected) emit('disconnect')
      return socket
    },
    timeout(milliseconds) {
      return { emitWithAck: (event, payload) => invoke(event, payload, milliseconds) }
    },
  }
  return socket
}
