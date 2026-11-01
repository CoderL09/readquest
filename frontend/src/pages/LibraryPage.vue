<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Search, Plus, BookOpen } from '@lucide/vue'
import { useRouter } from 'vue-router'
import { api, type Book, type GrowthSummaryResponse } from '../lib/api'
import { contents, removeBook } from '../api/books'
import { userStorage } from '../lib/userStorage'
import { useRequest } from '../composables/useRequest'
import { useNoticeStore } from '../stores/notices'
import { errorMessage } from '../lib/errors'
import RequestState from '../components/RequestState.vue'
import BookCard from '../components/BookCard.vue'
import ModalPanel from '../components/ModalPanel.vue'
const view = ref<'library' | 'uploads'>('library'),
  search = ref(''),
  shelf = ref<'all' | 'saved' | 'reading' | 'completed'>('all'),
  selected = ref<Book | null>(null),
  busy = ref(false),
  request = useRequest<Book[]>(),
  router = useRouter(),
  notices = useNoticeStore()
const visible = computed(() =>
  (request.data.value ?? []).filter(
    (book) =>
      (shelf.value === 'all' || book.shelfState === shelf.value) &&
      `${book.title} ${book.author}`.toLowerCase().includes(search.value.toLowerCase()),
  ),
)
function load() {
  return request.execute(
    async (signal) =>
      (await api.get<{ books: Book[] }>('/library/books', { params: { view: view.value }, signal }))
        .data.books,
  )
}
function change(next: 'library' | 'uploads') {
  shelf.value = 'all'
  view.value = next
  void load()
}
onMounted(load)
async function read(book: Book) {
  busy.value = true
  try {
    const value = await contents(book.id)
    const growth = await api.get<GrowthSummaryResponse>('/growth/summary').catch(() => null)
    const recent = growth?.data.reading.recentProgress.find((item) => item.bookId === book.id)
    const chapter =
      value.chapters.find((item) => item.id === recent?.chapterId) ?? value.chapters[0]
    if (!chapter) throw new Error('书籍尚未准备完成。')
    userStorage.setItem('readquest.bookId', book.id)
    await router.push({ path: `/app/reader/${chapter.id}`, query: { bookId: book.id } })
  } catch (error) {
    notices.notify(errorMessage(error))
  } finally {
    busy.value = false
  }
}
async function remove() {
  if (
    !selected.value ||
    !confirm(view.value === 'uploads' ? '确定删除这份上传吗？' : '确定从书架移除这本书吗？')
  )
    return
  busy.value = true
  try {
    if (view.value === 'uploads') await removeBook(selected.value.id)
    else await api.delete(`/library/${selected.value.id}`)
    selected.value = null
    await load()
  } catch (error) {
    notices.notify(errorMessage(error))
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="page library-page">
    <header class="page-heading">
      <div>
        <h1>我的书架</h1>
        <p>读过的故事，和下一次出发。</p>
      </div>
      <RouterLink to="/app/upload" class="button"><Plus :size="18" />添加书籍</RouterLink>
    </header>
    <div class="tabs">
      <button :class="{ active: view === 'library' }" @click="change('library')">已收藏</button
      ><button :class="{ active: view === 'uploads' }" @click="change('uploads')">我的上传</button>
    </div>
    <div class="collection-toolbar">
      <label class="search-box"
        ><Search :size="19" /><span class="sr-only">搜索书架</span
        ><input v-model="search" type="search" placeholder="搜索书名或作者…" /></label
      ><label v-if="view === 'library'" class="compact-filter"
        ><span class="sr-only">阅读状态</span
        ><select v-model="shelf">
          <option value="all">全部阅读状态</option>
          <option value="reading">在读</option>
          <option value="saved">想读</option>
          <option value="completed">已读</option>
        </select></label
      ><span class="collection-count">{{ visible.length }} 本书</span>
    </div>
    <RequestState
      :loading="request.loading.value"
      :error="request.error.value"
      :empty="!visible.length"
      message="这里还没有书，去发现页收藏一本，或上传自己的书籍。"
      @retry="load"
      ><template #empty
        ><RouterLink to="/app" class="button secondary">发现阅读</RouterLink></template
      >
      <div class="library-intro">
        <BookOpen :size="20" /><span>{{
          view === 'uploads' ? '自己带来的故事，也值得慢慢读。' : '选一本，回到故事里。'
        }}</span>
      </div>
      <div class="book-grid">
        <BookCard
          v-for="book in visible"
          :key="book.id"
          :book="book"
          @open="selected = $event"
        /></div></RequestState
    ><ModalPanel v-if="selected" :title="selected.title" @close="selected = null"
      ><p>{{ selected.author }}</p>
      <p class="muted">
        {{ selected.status === 'completed' ? `${selected.totalChapters} 个章节` : selected.status }}
      </p>
      <div class="actions">
        <button
          class="button"
          :disabled="busy || selected.status !== 'completed'"
          @click="read(selected)"
        >
          打开阅读</button
        ><RouterLink class="button secondary" :to="`/app/groups/${selected.id}`"
          >读书小组</RouterLink
        ><button class="button secondary" :disabled="busy" @click="remove">
          {{ view === 'uploads' ? '删除上传' : '移出书架' }}
        </button>
      </div></ModalPanel
    >
  </div>
</template>
