<script setup lang="ts">
import { onScopeDispose, ref } from 'vue'
import { api, type Book } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
import { catalog } from '../../api/books'

type Run = {
  id: string
  bookId: string
  state: 'running' | 'draft' | 'confirmed' | 'failed'
  result: { proposal: string } | null
  events: { tool: string; state: string; durationMs: number }[]
  session: { id?: string } | null
  errorCode: string | null
}
const emit = defineEmits<{ confirmed: [] }>()
const opened = ref(false),
  busy = ref(false),
  error = ref('')
const books = ref<Book[]>([]),
  bookId = ref(''),
  goal = ref('根据我的阅读进度和复习词汇，安排一轮复习，并给出有原文依据的练习。')
const groupSize = ref(5),
  run = ref<Run | null>(null)
const recent = ref<Run[]>([])
const controller = new AbortController()
onScopeDispose(() => controller.abort())
let key = '',
  fingerprint = ''
const toolLabels: Record<string, string> = {
  getReadingProgress: '查询阅读进度',
  getReviewWords: '读取复习词汇',
  searchBookPassages: '检索书籍原文',
}
async function open() {
  opened.value = !opened.value
  if (!opened.value || books.value.length) return
  try {
    books.value = await catalog(controller.signal)
    bookId.value = books.value[0]?.id ?? ''
  } catch (e) {
    if (!controller.signal.aborted) error.value = errorMessage(e)
  }
}
async function generate() {
  if (busy.value || !bookId.value || !goal.value.trim()) return
  const payload = { bookId: bookId.value, goal: goal.value.trim(), groupSize: groupSize.value }
  const next = JSON.stringify(payload)
  if (next !== fingerprint) {
    key = crypto.randomUUID()
    fingerprint = next
  }
  busy.value = true
  error.value = ''
  run.value = null
  try {
    const { data } = await api.post<Run>('/agent-runs', payload, {
      headers: { 'Idempotency-Key': key },
      signal: controller.signal,
      timeout: 90000,
    })
    run.value = data
    if (data.state === 'failed') {
      error.value = '这次生成未完成，请重新生成。'
      fingerprint = ''
    }
  } catch (e) {
    if (!controller.signal.aborted) error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function loadRecent() {
  try {
    recent.value = (
      await api.get<{ items: Run[] }>('/agent-runs', { signal: controller.signal })
    ).data.items
  } catch (e) {
    if (!controller.signal.aborted) error.value = errorMessage(e)
  }
}
function selectRun(item: Run) {
  run.value = item
  bookId.value = item.bookId
}
async function refreshRun() {
  if (!run.value) return
  try {
    run.value = (
      await api.get<Run>(`/agent-runs/${encodeURIComponent(run.value.id)}`, {
        signal: controller.signal,
      })
    ).data
  } catch (e) {
    if (!controller.signal.aborted) error.value = errorMessage(e)
  }
}
async function confirm() {
  if (!run.value || busy.value || run.value.state !== 'draft') return
  busy.value = true
  error.value = ''
  try {
    run.value = (
      await api.put<Run>(
        `/agent-runs/${encodeURIComponent(run.value.id)}/confirmation`,
        {},
        { signal: controller.signal },
      )
    ).data
    emit('confirmed')
  } catch (e) {
    if (!controller.signal.aborted) error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="panel stack" aria-label="阅读复习助手">
    <button class="button secondary" :aria-expanded="opened" @click="open">阅读复习助手</button>
    <template v-if="opened">
      <p class="muted">结合阅读进度、复习词汇与书籍原文生成建议。确认前不会启动复习。</p>
      <button class="button secondary" :disabled="busy" @click="loadRecent">查看最近的建议</button>
      <ul v-if="recent.length" aria-label="最近的建议">
        <li v-for="(item, index) in recent" :key="item.id">
          <button class="button secondary" :disabled="busy" @click="selectRun(item)">
            {{ books.find((book) => book.id === item.bookId)?.title ?? '阅读建议' }} ·
            {{ index + 1 }}
          </button>
        </li>
      </ul>
      <label
        >选择书籍<select v-model="bookId" :disabled="busy">
          <option value="" disabled>请选择书籍</option>
          <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
        </select></label
      >
      <label>学习目标<textarea v-model="goal" rows="3" maxlength="1500" :disabled="busy" /></label>
      <label
        >复习组大小<select v-model="groupSize" :disabled="busy">
          <option :value="5">5 张</option>
          <option :value="10">10 张</option>
          <option :value="20">20 张</option>
        </select></label
      >
      <button class="button" :disabled="busy || !bookId || !goal.trim()" @click="generate">
        {{ busy ? '正在处理…' : '生成复习建议' }}
      </button>
      <p v-if="busy" role="status">正在处理，请稍候。</p>
      <template v-if="run">
        <button v-if="run.state === 'running'" class="button secondary" @click="refreshRun">
          生成中，刷新进度
        </button>
        <p v-if="run.state === 'failed'" role="status">这次生成未完成，请重新生成。</p>
        <ul v-if="run.events.length" aria-label="执行记录">
          <li v-for="(event, index) in run.events" :key="index">
            {{ toolLabels[event.tool] ?? event.tool }} ·
            {{ event.state === 'completed' ? '已完成' : '参数已拒绝，等待纠正' }}
          </li>
        </ul>
        <p v-if="run.result" class="assistant-proposal">{{ run.result.proposal }}</p>
        <template v-if="run.state === 'draft'">
          <p class="muted">
            下面的操作将启动或恢复现有 FSRS 待复习卡片。生成的练习仅作为建议，不会自动添加卡片。
          </p>
          <button class="button" :disabled="busy" @click="confirm">确认并进入待复习卡片</button>
        </template>
        <p v-if="run.state === 'confirmed'" role="status">
          {{
            run.session?.id
              ? '已准备好复习，可以在下方继续。'
              : '当前没有到期的复习卡片，可先完成上方建议练习。'
          }}
        </p>
      </template>
    </template>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.assistant-proposal {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
}
textarea {
  width: 100%;
}
</style>
