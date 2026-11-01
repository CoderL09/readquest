<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search, ArrowUpRight, BookOpen, Trash2 } from '@lucide/vue'
import { useRequest } from '../composables/useRequest'
import { fetchAllVocabulary } from '../features/vocabulary/vocabularyApi'
import {
  filterVocabulary,
  type VocabularyItem,
  type VocabularyStatus,
} from '../features/vocabulary/vocabularyState'
import { api } from '../lib/api'
import { useNoticeStore } from '../stores/notices'
import { errorMessage } from '../lib/errors'
import RequestState from '../components/RequestState.vue'
const request = useRequest<VocabularyItem[]>(),
  query = ref(''),
  bookId = ref('all'),
  status = ref<VocabularyStatus | 'all'>('all'),
  selectedId = ref(''),
  notices = useNoticeStore()
const labels: Record<VocabularyStatus, string> = {
  new: '新记录',
  learning: '学习中',
  reviewing: '复习中',
  mastered: '已掌握',
}
const books = computed(() => [
  ...new Map((request.data.value ?? []).map((item) => [item.book.id, item.book])).values(),
])
const visible = computed(() =>
  filterVocabulary(request.data.value ?? [], {
    query: query.value,
    bookId: bookId.value,
    status: status.value,
  }),
)
const selected = computed(
  () => visible.value.find((item) => item.id === selectedId.value) ?? visible.value[0] ?? null,
)
const load = () => request.execute((signal) => fetchAllVocabulary(signal))
onMounted(load)
async function remove(id: string) {
  if (!confirm('确定移除这条语境记录吗？')) return
  try {
    await api.delete(`/lookup-items/${id}`)
    await load()
  } catch (error) {
    notices.notify(errorMessage(error))
  }
}
function due(item: VocabularyItem) {
  if (item.mastery?.due) return '待复习'
  return item.mastery?.dueAt ? new Date(item.mastery.dueAt).toLocaleDateString() : '待安排'
}
</script>
<template>
  <div class="page vocabulary-page">
    <header class="page-heading">
      <div>
        <h1>把遇见的词，<br class="mobile-break" />变成熟悉的表达。</h1>
        <p>在原句里理解，在复习中记住。</p>
      </div>
      <RouterLink class="button" to="/app/review">开始复习<ArrowUpRight :size="18" /></RouterLink>
    </header>
    <div class="collection-toolbar">
      <label class="search-box"
        ><Search :size="19" /><span class="sr-only">搜索生词</span
        ><input v-model="query" type="search" placeholder="搜索单词、释义或原句…" /></label
      ><label class="compact-filter"
        ><span class="sr-only">筛选书籍</span
        ><select v-model="bookId">
          <option value="all">全部书籍</option>
          <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
        </select></label
      ><label class="compact-filter"
        ><span class="sr-only">学习状态</span
        ><select v-model="status">
          <option value="all">全部状态</option>
          <option v-for="(label, value) in labels" :key="value" :value="value">{{ label }}</option>
        </select></label
      >
    </div>
    <RequestState
      :loading="request.loading.value"
      :error="request.error.value"
      :empty="!visible.length"
      message="这里还没有匹配的词句。阅读时选中一个词，就能留下它的语境。"
      @retry="load"
    >
      <div class="vocabulary-workspace">
        <section class="vocabulary-table-panel" aria-label="生词列表">
          <div class="section-heading">
            <h2>
              我的词句 <span class="count-pill">{{ visible.length }}</span>
            </h2>
            <span class="muted">点击单词查看原句</span>
          </div>
          <div class="vocabulary-table-scroll">
            <table class="vocabulary-table">
              <thead>
                <tr>
                  <th scope="col">单词 / 短语</th>
                  <th scope="col">释义</th>
                  <th scope="col">状态</th>
                  <th scope="col">下次复习</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in visible"
                  :key="item.id"
                  :class="{ selected: item.id === selected?.id }"
                >
                  <td>
                    <button
                      class="vocabulary-select"
                      :aria-pressed="item.id === selected?.id"
                      @click="selectedId = item.id"
                    >
                      {{ item.text }}</button
                    ><small>{{ item.book.title }}</small>
                  </td>
                  <td>{{ item.explanation || item.translation || '尚未补充释义' }}</td>
                  <td>
                    <span class="badge">{{ labels[item.reviewStatus] }}</span>
                  </td>
                  <td>{{ due(item) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <aside v-if="selected" class="word-detail" aria-label="词句详情">
          <span class="badge">{{ selected.type === 'phrase' ? '语境短语' : '语境单词' }}</span>
          <h2>{{ selected.text }}</h2>
          <p class="word-meaning">
            {{ selected.explanation || selected.translation || '尚未补充释义' }}
          </p>
          <div class="word-source">
            <h3>原句</h3>
            <blockquote>{{ selected.originalSentence || '此条记录暂未保存原句。' }}</blockquote>
          </div>
          <div class="word-source">
            <h3>来自这本书</h3>
            <div class="source-book-line">
              <span class="source-book-icon"><BookOpen :size="25" /></span>
              <div>
                <strong>{{ selected.book.title }}</strong>
                <p>{{ selected.book.author }}</p>
              </div>
            </div>
            <RouterLink class="text-link" to="/app/library"
              >在书架中查看<ArrowUpRight :size="16"
            /></RouterLink>
          </div>
          <p class="muted">
            {{
              selected.mastery?.score != null
                ? `掌握度 ${selected.mastery.score}%`
                : '完成复习后，这里会积累你的掌握情况。'
            }}
          </p>
          <button class="text-link remove-link" @click="remove(selected.id)">
            <Trash2 :size="16" />移除这条记录
          </button>
        </aside>
      </div>
    </RequestState>
  </div>
</template>
