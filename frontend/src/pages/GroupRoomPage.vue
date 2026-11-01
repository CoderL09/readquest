<script setup lang="ts">
import { computed, ref } from 'vue'
import { BookOpen, Users, ArrowUpRight } from '@lucide/vue'
import { getJoinedStudyGroups } from '../features/study-groups/groupApi'
import { useRequest } from '../composables/useRequest'
import type { StudyGroupSummary } from '../features/study-groups/contracts'
import { useRoute } from 'vue-router'
import { useGroupRoom } from '../features/study-groups/useGroupRoom'
import * as requests from '../features/study-groups/groupApi'
import type {
  StudyGroupMember,
  StudyGroupEnglishCoachResponse,
  StudyGroupEnglishCoachLevel,
} from '../features/study-groups/contracts'
import ModalPanel from '../components/ModalPanel.vue'
const joined = useRequest<StudyGroupSummary[]>()
void joined.execute(getJoinedStudyGroups)
const bookId = String(useRoute().params.bookId),
  room = useGroupRoom(bookId),
  draft = ref(''),
  selected = ref<StudyGroupMember | null>(null),
  reason = ref(''),
  coach = ref<StudyGroupEnglishCoachResponse | null>(null),
  level = ref<StudyGroupEnglishCoachLevel>('B1')
const visible = computed(() =>
  room.messages.value.filter(
    (m) => !room.members.value.some((u) => u.userId === m.sender.userId && u.blockedByCurrentUser),
  ),
)
async function send() {
  const text = draft.value.trim()
  if (!text) return
  draft.value = ''
  await room.send(text)
}
async function memberAction(kind: 'block' | 'mute' | 'unmute' | 'remove' | 'role' | 'report') {
  const member = selected.value
  if (!member) return
  await room.action(async () => {
    if (kind === 'block') {
      await (
        member.blockedByCurrentUser
          ? requests.unblockStudyGroupMember
          : requests.blockStudyGroupMember
      )(bookId, member.userId)
    } else if (kind === 'report') {
      await requests.createStudyGroupReport(bookId, {
        targetType: 'user',
        targetUserId: member.userId,
        reason: 'other',
        details: reason.value,
      })
    } else if (kind === 'remove') {
      await requests.removeStudyGroupMember(bookId, member.userId, reason.value)
    } else {
      await requests.moderateStudyGroupMember(bookId, member.userId, {
        reason: reason.value,
        ...(kind === 'role'
          ? { role: member.role === 'moderator' ? 'member' : 'moderator' }
          : { mutedUntil: kind === 'mute' ? new Date(Date.now() + 3600000).toISOString() : null }),
      })
    }
    await room.loadMembers()
    selected.value = null
    reason.value = ''
  })
}
async function messageAction(id: string, kind: 'delete' | 'report') {
  await room.action(async () => {
    if (kind === 'delete') {
      await requests.deleteStudyGroupMessage(bookId, id, '移除不适当内容')
      room.messages.value = room.messages.value.map((m) =>
        m.id === id ? { ...m, body: '', deleted: true } : m,
      )
    } else
      await requests.createStudyGroupReport(bookId, {
        targetType: 'message',
        targetMessageId: id,
        reason: 'other',
        details: '请求审核此消息',
      })
  })
}
async function askCoach(action: 'express' | 'check' | 'reply_suggestions') {
  await room.action(async () => {
    coach.value = await requests.requestStudyGroupEnglishCoach(
      bookId,
      action === 'reply_suggestions'
        ? { action, level: level.value }
        : { action, level: level.value, text: draft.value },
      room.signal,
    )
  })
}
</script>
<template>
  <section class="page group-room-page">
    <header class="page-heading">
      <div>
        <RouterLink to="/app/groups" class="text-link">← 读书小组</RouterLink>
        <h1>{{ room.group.value?.title ?? '共读空间' }}</h1>
        <p class="muted">
          {{ room.group.value?.memberCount ?? 0 }} 位共读者 ·
          {{
            room.status.value === 'connected'
              ? '消息已连接'
              : room.status.value === 'paused'
                ? '连接已暂停'
                : '正在重新连接…'
          }}
        </p>
      </div>
      <button
        v-if="room.group.value?.joined"
        class="button secondary"
        :disabled="room.busy.value"
        @click="room.leave"
      >
        退出小组
      </button>
    </header>
    <p v-if="room.error.value" class="error" role="alert">{{ room.error.value }}</p>
    <p v-if="room.loading.value" role="status">正在打开共读空间…</p>
    <div v-else-if="room.group.value && !room.group.value.joined" class="panel empty">
      <h2>一起读，读得更远</h2>
      <p>加入后可以讨论书中内容，分享阅读进度。</p>
      <button class="button" :disabled="room.busy.value" @click="room.join">加入小组</button>
    </div>
    <div v-else-if="room.group.value?.joined" class="group-layout">
      <nav class="group-room-nav panel" aria-label="我的小组">
        <h2>我的小组</h2>
        <RouterLink
          v-for="group in joined.data.value ?? []"
          :key="group.bookId"
          :to="`/app/groups/${group.bookId}`"
          :class="{ active: group.bookId === bookId }"
          ><BookOpen :size="22" /><span
            ><strong>{{ group.title }}</strong
            ><small>{{ group.memberCount }} 位共读者</small></span
          ></RouterLink
        ><RouterLink to="/app/groups" class="text-link"
          >全部小组<ArrowUpRight :size="16"
        /></RouterLink>
      </nav>
      <main class="panel stack chat-panel">
        <button
          v-if="room.hasOlder.value"
          class="button secondary"
          :disabled="room.busy.value"
          @click="room.older"
        >
          加载更早消息
        </button>
        <div class="messages" role="log" aria-live="polite" aria-label="小组消息">
          <p v-if="!visible.length" class="empty">还没有讨论，分享你的第一段感想吧。</p>
          <article
            v-for="message in visible"
            :key="message.clientMessageId || message.id"
            class="message"
            :class="{ own: message.isCurrentUser }"
          >
            <strong>{{ message.sender.displayName }}</strong
            ><time class="muted"> {{ new Date(message.createdAt).toLocaleString() }}</time>
            <p>{{ message.deleted ? '此消息已删除' : message.body }}</p>
            <button
              v-if="message.delivery === 'failed'"
              class="text-link"
              @click="room.send(message.body, message.clientMessageId)"
            >
              发送失败，重试</button
            ><span v-else-if="message.delivery === 'sending'" class="muted">正在发送…</span>
            <div
              v-if="!message.deleted && (!message.delivery || message.delivery === 'sent')"
              class="row"
            >
              <button
                v-if="
                  message.isCurrentUser ||
                  room.me.value?.role === 'admin' ||
                  room.me.value?.role === 'moderator'
                "
                class="text-link"
                @click="messageAction(message.id, 'delete')"
              >
                删除</button
              ><button
                v-if="!message.isCurrentUser"
                class="text-link"
                @click="messageAction(message.id, 'report')"
              >
                举报
              </button>
            </div>
          </article>
        </div>
        <form class="stack" @submit.prevent="send">
          <label
            >分享你的想法<textarea
              v-model="draft"
              rows="3"
              maxlength="2000"
              placeholder="聊聊刚读到的那一段…"
            />
          </label>
          <div class="row between">
            <span class="muted">{{ draft.length }} / 2000</span
            ><button class="button" :disabled="!draft.trim() || room.status.value !== 'connected'">
              发送
            </button>
          </div>
        </form>
        <details>
          <summary>英语表达助手</summary>
          <div class="stack">
            <label
              >表达水平<select v-model="level">
                <option v-for="l in ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']" :key="l">{{ l }}</option>
              </select></label
            >
            <div class="row wrap">
              <button
                class="button secondary"
                :disabled="room.busy.value || !draft.trim()"
                @click="askCoach('express')"
              >
                帮我表达</button
              ><button
                class="button secondary"
                :disabled="room.busy.value || !draft.trim()"
                @click="askCoach('check')"
              >
                检查英文</button
              ><button
                class="button secondary"
                :disabled="room.busy.value"
                @click="askCoach('reply_suggestions')"
              >
                回复建议
              </button>
            </div>
            <template v-if="coach"
              ><p>{{ coach.noticeZh }}</p>
              <template v-if="coach.action === 'check'"
                ><button class="panel" @click="draft = coach.corrected">
                  {{ coach.corrected }}
                </button>
                <p v-for="issue in coach.issues" :key="issue.original">
                  {{ issue.original }} → {{ issue.replacement }}：{{ issue.explanationZh }}
                </p></template
              ><button
                v-for="suggestion in coach.action !== 'check' ? coach.suggestions : []"
                :key="suggestion.text"
                class="panel"
                @click="draft = suggestion.text"
              >
                {{ suggestion.text }}
              </button></template
            >
          </div>
        </details>
      </main>
      <aside class="panel stack group-members-panel">
        <div class="group-source-card">
          <BookOpen :size="28" />
          <h2>共读这本书</h2>
          <strong>{{ room.group.value?.title }}</strong
          ><RouterLink to="/app/library" class="button secondary"
            >前往书架<ArrowUpRight :size="16"
          /></RouterLink>
        </div>
        <h2><Users :size="20" />共读者</h2>
        <button
          v-for="member in room.members.value"
          :key="member.userId"
          class="member-row"
          @click="selected = member"
        >
          <span class="member-avatar" aria-hidden="true">{{ member.displayName.slice(0, 1) }}</span
          ><strong>{{ member.displayName }}{{ member.isCurrentUser ? '（你）' : '' }}</strong
          ><span class="muted">{{ member.role }} · {{ member.percent }}%</span
          ><progress :value="member.percent" max="100" /></button
        ><button
          v-if="room.hasMembers.value"
          class="button secondary"
          @click="room.loadMembers(true)"
        >
          更多成员
        </button>
      </aside>
    </div>
    <ModalPanel v-if="selected" :title="selected.displayName" @close="selected = null"
      ><div class="stack">
        <p>
          阅读进度 {{ selected.percent }}% · {{ selected.completedChapters }} /
          {{ selected.totalChapters }} 章节
        </p>
        <template v-if="!selected.isCurrentUser"
          ><label>原因 / 说明<textarea v-model="reason" maxlength="500" /></label
          ><button
            class="button secondary"
            :disabled="room.busy.value"
            @click="memberAction('block')"
          >
            {{ selected.blockedByCurrentUser ? '取消屏蔽' : '屏蔽此人' }}</button
          ><button
            class="button secondary"
            :disabled="room.busy.value"
            @click="memberAction('report')"
          >
            举报此人</button
          ><template v-if="room.canModerate(selected)"
            ><button
              class="button secondary"
              :disabled="room.busy.value || !reason.trim()"
              @click="memberAction(selected.mutedUntil ? 'unmute' : 'mute')"
            >
              {{ selected.mutedUntil ? '解除禁言' : '禁言一小时' }}</button
            ><button
              v-if="room.me.value?.role === 'admin'"
              class="button secondary"
              :disabled="room.busy.value || !reason.trim()"
              @click="memberAction('role')"
            >
              {{ selected.role === 'moderator' ? '取消管理员' : '设为管理员' }}</button
            ><button
              class="button danger"
              :disabled="room.busy.value || !reason.trim()"
              @click="memberAction('remove')"
            >
              移出小组
            </button></template
          ></template
        >
      </div></ModalPanel
    >
  </section>
</template>
