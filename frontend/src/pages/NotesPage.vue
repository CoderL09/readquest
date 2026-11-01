<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search, Quote, Download } from '@lucide/vue'
import { useRequest } from '../composables/useRequest'
import { fetchAllBookNotes, updateBookNote, deleteBookNote } from '../features/notes/noteApi'
import { filterBookNotes, toBookNotesMarkdown, type BookNote } from '../features/notes/noteState'
import { errorMessage } from '../lib/errors'
import { useNoticeStore } from '../stores/notices'
import RequestState from '../components/RequestState.vue'
import ModalPanel from '../components/ModalPanel.vue'
const request = useRequest<BookNote[]>(),
  query = ref(''),
  bookId = ref('all'),
  editing = ref<BookNote | null>(null),
  text = ref(''),
  saving = ref(false),
  notices = useNoticeStore()
const books = computed(() => [
  ...new Map((request.data.value ?? []).map((item) => [item.book.id, item.book])).values(),
])
const visible = computed(() =>
  filterBookNotes(request.data.value ?? [], { query: query.value, bookId: bookId.value }),
)
const load = () => request.execute((signal) => fetchAllBookNotes(signal))
onMounted(load)
function edit(note: BookNote) {
  editing.value = note
  text.value = note.note
}
async function save() {
  if (!editing.value || saving.value) return
  saving.value = true
  try {
    await updateBookNote(editing.value.id, text.value)
    editing.value = null
    await load()
  } catch (error) {
    notices.notify(errorMessage(error))
  } finally {
    saving.value = false
  }
}
async function remove(id: string) {
  if (!confirm('确定删除这条笔记吗？')) return
  try {
    await deleteBookNote(id)
    await load()
  } catch (error) {
    notices.notify(errorMessage(error))
  }
}
function download() {
  const blob = new Blob([toBookNotesMarkdown(visible.value)], {
      type: 'text/markdown;charset=utf-8',
    }),
    url = URL.createObjectURL(blob),
    anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'readquest-notes.md'
  anchor.click()
  URL.revokeObjectURL(url)
}
function print() {
  window.print()
}
</script>
<template>
  <div class="page notes-page">
    <header class="page-heading">
      <div>
        <h1>留住让你停下来的句子。</h1>
        <p>原文与想法，都值得再读一次。</p>
      </div>
      <div class="actions">
        <button class="button secondary" :disabled="!visible.length" @click="download">
          <Download :size="16" />导出 Markdown</button
        ><button class="button secondary" :disabled="!visible.length" @click="print">
          打印 / PDF
        </button>
      </div>
    </header>
    <div class="collection-toolbar">
      <label class="search-box"
        ><Search :size="19" /><span class="sr-only">搜索笔记</span
        ><input v-model="query" type="search" placeholder="搜索摘录或感想…" /></label
      ><label
        ><span class="sr-only">筛选书籍</span
        ><select v-model="bookId">
          <option value="all">全部书籍</option>
          <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
        </select></label
      >
    </div>
    <RequestState
      :loading="request.loading.value"
      :error="request.error.value"
      :empty="!visible.length"
      message="阅读时选中一段原文，就能保存第一条笔记。"
      @retry="load"
      ><div class="notes-grid">
        <article v-for="item in visible" :key="item.id" class="panel note-card">
          <Quote :size="26" class="note-quote-icon" /><small
            >{{ item.book.title }} · {{ item.chapter.title }}</small
          >
          <blockquote class="quote">{{ item.quote }}</blockquote>
          <p class="pre-wrap note-thought">
            {{ item.note || '这段原文已经留下，随时可以补充你的理解。' }}
          </p>
          <time class="muted" :datetime="item.createdAt">{{
            new Date(item.createdAt).toLocaleDateString()
          }}</time>
          <div class="actions note-actions">
            <RouterLink
              v-if="item.chapter.id"
              class="text-link"
              :to="{ path: `/app/reader/${item.chapter.id}`, query: { bookId: item.book.id } }"
              >打开章节 ↗</RouterLink
            >
            <button class="button secondary small" @click="edit(item)">编辑感想</button
            ><button class="button secondary small" @click="remove(item.id)">删除笔记</button>
          </div>
        </article>
      </div></RequestState
    ><ModalPanel v-if="editing" title="编辑阅读笔记" @close="editing = null"
      ><form @submit.prevent="save">
        <blockquote class="quote">{{ editing.quote }}</blockquote>
        <label>我的感想<textarea v-model="text" maxlength="10000" /></label
        ><button class="button" :disabled="saving">{{ saving ? '保存中…' : '保存笔记' }}</button>
      </form></ModalPanel
    >
  </div>
</template>
