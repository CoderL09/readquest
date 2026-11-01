import { computed, onScopeDispose, ref } from 'vue'
import * as requests from './groupApi'
import type { StudyGroupSummary, StudyGroupMember, StudyGroupMessage } from './contracts'
import {
  mergeStudyGroupMessages,
  markStudyGroupMessageFailed,
  canModerateStudyGroupMember,
} from './groupState'
import {
  createStudyGroupRealtimeController,
  refreshCurrentRealtimeAuthentication,
  drainStudyGroupCursorCatchUp,
} from './groupRealtime'
import { createJavaGroupSocket } from './javaGroupRealtime'
import { getAuthenticationSnapshot, refreshAccessToken, expireAuthentication } from '../../lib/api'
import { useAuthStore } from '../../stores/auth'
import { errorMessage } from '../../lib/errors'
export function useGroupRoom(bookId: string) {
  const auth = useAuthStore(),
    group = ref<StudyGroupSummary | null>(null),
    members = ref<StudyGroupMember[]>([]),
    messages = ref<StudyGroupMessage[]>([]),
    error = ref(''),
    loading = ref(true),
    status = ref('retrying'),
    busy = ref(false),
    hasOlder = ref(false),
    hasMembers = ref(false)
  const controller = new AbortController()
  let realtime: ReturnType<typeof createStudyGroupRealtimeController> | null = null,
    oldest: string | null = null,
    contiguousCursor: string | null = null,
    memberPage = 1
  const me = computed(() => members.value.find((m) => m.isCurrentUser))
  async function loadMembers(more = false) {
    const page = more ? memberPage + 1 : 1
    const data = await requests.getStudyGroupMembers(bookId, {
      page,
      pageSize: 30,
      signal: controller.signal,
    })
    if (controller.signal.aborted) return
    members.value = more
      ? [
          ...members.value,
          ...data.members.filter((m) => !members.value.some((p) => p.userId === m.userId)),
        ]
      : data.members
    memberPage = page
    hasMembers.value = data.pagination.hasMore
  }
  async function catchUp(signal: AbortSignal) {
    if (contiguousCursor) {
      await drainStudyGroupCursorCatchUp({
        initialCursor: contiguousCursor,
        signal,
        fetchPage: async (query, requestSignal) => {
          const data = await requests.getStudyGroupMessages(bookId, {
            ...query,
            signal: requestSignal,
          })
          return { items: data.messages, ...data.pageInfo }
        },
        acceptPage: (items) => {
          if (!controller.signal.aborted)
            messages.value = mergeStudyGroupMessages(messages.value, items)
        },
        onContiguousCursor: (cursor) => {
          contiguousCursor = cursor
        },
      })
    } else {
      const data = await requests.getStudyGroupMessages(bookId, { signal })
      if (signal.aborted || controller.signal.aborted) return
      messages.value = mergeStudyGroupMessages(messages.value, data.messages)
      oldest = data.pageInfo.oldestCursor
      hasOlder.value = data.pageInfo.hasMore
      contiguousCursor = data.pageInfo.newestCursor
    }
    await loadMembers()
  }
  function connect() {
    realtime?.stop()
    realtime = createStudyGroupRealtimeController({
      socket: createJavaGroupSocket(),
      bookId,
      userId: auth.user!.id,
      catchUp,
      refreshAuthentication: () =>
        refreshCurrentRealtimeAuthentication({
          getSnapshot: getAuthenticationSnapshot,
          refresh: async () => {
            const result = await refreshAccessToken()
            return result ? { userId: result.user.id } : null
          },
        }),
      onAuthenticationExpired: expireAuthentication,
      onMessage: (message) => {
        messages.value = mergeStudyGroupMessages(messages.value, [message])
      },
      onMembershipRequired: () => {
        if (group.value) group.value.joined = false
        realtime?.stop()
        status.value = 'paused'
      },
      onError: (message) => {
        error.value = message
      },
      onStatusChange: (value) => {
        status.value = value
      },
      isOnline: () => navigator.onLine,
    })
    realtime.start()
  }
  async function load() {
    loading.value = true
    error.value = ''
    try {
      const data = await requests.getStudyGroupSummary(bookId, controller.signal)
      if (controller.signal.aborted) return
      group.value = data
      if (data.joined) connect()
    } catch (e) {
      if (!controller.signal.aborted) error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function action(task: () => Promise<void>) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      await task()
    } catch (e) {
      if (!controller.signal.aborted) error.value = errorMessage(e)
    } finally {
      busy.value = false
    }
  }
  const join = () =>
    action(async () => {
      await requests.joinStudyGroup(bookId)
      await load()
    })
  const leave = () =>
    action(async () => {
      await requests.leaveStudyGroup(bookId)
      realtime?.stop()
      messages.value = []
      members.value = []
      await load()
    })
  async function older() {
    await action(async () => {
      if (!oldest) return
      const data = await requests.getStudyGroupMessages(bookId, {
        before: oldest,
        signal: controller.signal,
      })
      messages.value = mergeStudyGroupMessages(messages.value, data.messages)
      oldest = data.pageInfo.oldestCursor
      hasOlder.value = data.pageInfo.hasMore
    })
  }
  async function send(body: string, clientMessageId: string = crypto.randomUUID()) {
    if (!body.trim() || !auth.user || !realtime) return false
    const pending: StudyGroupMessage = {
      id: `pending:${clientMessageId}`,
      bookId,
      body: body.trim(),
      clientMessageId,
      createdAt: new Date().toISOString(),
      isCurrentUser: true,
      deleted: false,
      deletedAt: null,
      delivery: 'sending',
      sender: {
        userId: auth.user.id,
        displayName: auth.user.displayName,
        avatarUrl: auth.user.avatarUrl ?? '',
      },
    }
    messages.value = mergeStudyGroupMessages(messages.value, [pending])
    try {
      const result = await realtime.send({ body: pending.body, clientMessageId })
      if (!controller.signal.aborted)
        messages.value = mergeStudyGroupMessages(messages.value, [result])
      return true
    } catch (e) {
      if (!controller.signal.aborted) {
        messages.value = markStudyGroupMessageFailed(
          messages.value,
          pending.sender.userId,
          clientMessageId,
        )
        error.value = errorMessage(e)
      }
      return false
    }
  }
  const canModerate = (member: StudyGroupMember) =>
    Boolean(me.value && canModerateStudyGroupMember(me.value, member))
  function resume() {
    realtime?.reconnect()
  }
  function pause() {
    realtime?.pause()
  }
  window.addEventListener('online', resume)
  window.addEventListener('offline', pause)
  onScopeDispose(() => {
    controller.abort()
    realtime?.stop()
    window.removeEventListener('online', resume)
    window.removeEventListener('offline', pause)
  })
  void load()
  return {
    group,
    members,
    messages,
    error,
    loading,
    status,
    busy,
    hasOlder,
    hasMembers,
    me,
    load,
    loadMembers,
    join,
    leave,
    older,
    send,
    action,
    canModerate,
    signal: controller.signal,
  }
}
