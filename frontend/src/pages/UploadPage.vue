<script setup lang="ts">
import { computed, ref, onScopeDispose } from 'vue'
import { api, type Book } from '../lib/api'
import { contents } from '../api/books'
import { errorMessage } from '../lib/errors'
import { userStorage } from '../lib/userStorage'
import { activeImport, importPhase, type BookImportJob } from '../features/imports/bookImport'
import DictionaryManager from '../features/dictionary/DictionaryManager.vue'
const file = ref<File | null>(null),
  rights = ref(false),
  mode = ref('quest'),
  words = ref(500),
  busy = ref(false),
  error = ref(''),
  notice = ref(''),
  book = ref<Book | null>(null),
  job = ref<BookImportJob | null>(null),
  stage = ref('')
const active = computed(() => activeImport(job.value))
const controller = new AbortController()
let timer: ReturnType<typeof setTimeout> | undefined
let generation = 0
onScopeDispose(() => {
  generation++
  controller.abort()
  clearTimeout(timer)
})
function choose(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
  error.value = ''
}
function remember(value: BookImportJob) {
  userStorage.setItem(
    'readquest.import.active',
    JSON.stringify({ id: value.id, bookId: value.bookId }),
  )
}
async function poll(id: string, version: number) {
  try {
    const { data } = await api.get<BookImportJob>('/book-imports/' + id, {
      signal: controller.signal,
    })
    if (controller.signal.aborted || version !== generation) return
    job.value = data
    remember(data)
    if (!book.value || book.value.id !== data.bookId)
      book.value = (
        await api.get<Book>('/books/' + data.bookId, { signal: controller.signal })
      ).data
    if (controller.signal.aborted || version !== generation) return
    error.value =
      data.status === 'failed' ? data.errorMessage || '书籍准备失败，可以重试解析。' : ''
    if (data.status === 'completed')
      stage.value = (await contents(data.bookId, controller.signal)).chapters[0]?.id ?? ''
    else if (activeImport(data)) timer = setTimeout(() => void poll(id, version), 2000)
  } catch (failure) {
    if (!controller.signal.aborted && version === generation)
      error.value = errorMessage(failure, '连接中断，任务仍保留在服务端。请重新连接查看状态。')
  }
}
function monitor(value: BookImportJob) {
  clearTimeout(timer)
  job.value = value
  remember(value)
  void poll(value.id, ++generation)
}
async function upload() {
  if (!file.value || busy.value || active.value) return
  if (!rights.value) {
    error.value = '请确认你拥有公开分享权限。'
    return
  }
  if (!/\.(txt|pdf|epub)$/i.test(file.value.name) || file.value.size > 50 * 1024 * 1024) {
    error.value = '请选择 50 MB 以内的 TXT、PDF 或 EPUB 文件。'
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  stage.value = ''
  try {
    const data = new FormData()
    data.append('file', file.value)
    data.append('rightsAttested', 'true')
    data.append('mode', mode.value)
    data.append('wordsPerQuest', String(words.value))
    const response = await api.post<{ book: Book; job: BookImportJob; duplicate: boolean }>(
      '/books',
      data,
      { signal: controller.signal },
    )
    if (controller.signal.aborted) return
    book.value = response.data.book
    notice.value = response.data.duplicate ? '检测到相同文件，已复用已有书籍和任务。' : ''
    monitor(response.data.job)
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
async function retry() {
  if (!book.value || busy.value) return
  busy.value = true
  error.value = ''
  stage.value = ''
  try {
    const { data } = await api.post<BookImportJob>(
      '/books/' + book.value.id + '/imports',
      undefined,
      { signal: controller.signal },
    )
    if (!controller.signal.aborted) monitor(data)
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
async function cancel() {
  if (!job.value || busy.value || !active.value) return
  busy.value = true
  error.value = ''
  clearTimeout(timer)
  generation++
  try {
    const { data } = await api.post<BookImportJob>(
      '/book-imports/' + job.value.id + '/cancellations',
      {},
      { signal: controller.signal },
    )
    if (!controller.signal.aborted) monitor(data)
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
function reconnect() {
  if (job.value) monitor(job.value)
}
try {
  const saved = JSON.parse(userStorage.getItem('readquest.import.active') || 'null')
  if (saved && typeof saved.id === 'string' && /^[A-Za-z0-9-]{1,36}$/.test(saved.id))
    void poll(saved.id, ++generation)
} catch {
  userStorage.removeItem('readquest.import.active')
}
</script>
<template>
  <div class="page narrow">
    <header class="page-heading">
      <div>
        <h1>添加自己的书</h1>
        <p>保留原文，让阅读从你感兴趣的内容开始。</p>
      </div>
    </header>
    <section class="panel stack">
      <form @submit.prevent="upload">
        <label
          >选择 TXT、PDF 或 EPUB<input
            type="file"
            accept=".txt,.pdf,.epub"
            :disabled="busy || active"
            @change="choose"
        /></label>
        <div class="field-grid">
          <label
            >阅读模式<select v-model="mode" :disabled="busy || active">
              <option value="quest">分段阅读</option>
              <option value="continuous">连续章节</option>
            </select></label
          ><label v-if="mode === 'quest'"
            >每段目标词数<input
              v-model.number="words"
              type="number"
              min="200"
              max="5000"
              :disabled="busy || active"
          /></label>
        </div>
        <label class="checkbox"
          ><input
            v-model="rights"
            type="checkbox"
            :disabled="busy || active"
          />我拥有此内容，或已获得向其他读者公开分享的许可。</label
        ><button class="button" :disabled="busy || active || !file || !rights">
          {{ busy ? '正在提交…' : active ? '任务处理中' : '上传并准备阅读' }}
        </button>
      </form>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="notice" class="notice">{{ notice }}</p>
      <div v-if="book" class="stack">
        <strong>{{ book.title }}</strong>
        <p v-if="job" role="status">{{ importPhase(job) }} · 阶段进度 {{ job.progress }}%</p>
        <progress v-if="job" :value="job.progress" max="100" aria-label="书籍导入阶段进度" />
        <p v-if="active" class="muted">
          任务在服务端继续处理，离开或刷新页面后可恢复查看。进度表示处理阶段，不代表已解析字数。
        </p>
        <p v-if="job && job.attempts > 1" class="muted">
          当前为第 {{ job.attempts }} 次执行。中断恢复会重新解析原文件。
        </p>
        <button v-if="active" class="button secondary" :disabled="busy" @click="cancel">
          取消导入
        </button>
        <button v-if="error && job" class="text-link" :disabled="busy" @click="reconnect">
          重新连接查看状态
        </button>
        <RouterLink
          v-if="stage"
          class="button"
          :to="{ path: `/app/reader/${stage}`, query: { bookId: book.id } }"
          >打开阅读器</RouterLink
        ><button
          v-if="job && ['failed', 'cancelled'].includes(job.status)"
          class="button secondary"
          :disabled="busy"
          @click="retry"
        >
          重试解析
        </button>
      </div>
    </section>
    <div class="mt-6"><DictionaryManager /></div>
  </div>
</template>
