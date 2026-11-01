<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Search,
  ArrowUpRight,
  ArrowRight,
  Plus,
  BookOpen,
  Layers,
  Clock3,
  Bookmark,
} from '@lucide/vue'
import { catalog, contents, saveBook } from '../api/books'
import { api, mediaUrl, type Book, type GrowthSummaryResponse } from '../lib/api'
import type { ReviewSummary } from '../features/review/contracts'
import { userStorage } from '../lib/userStorage'
import { useRequest } from '../composables/useRequest'
import { useNoticeStore } from '../stores/notices'
import { errorMessage } from '../lib/errors'
import BookCard from '../components/BookCard.vue'
import RequestState from '../components/RequestState.vue'
import ModalPanel from '../components/ModalPanel.vue'
import ReadingRecommendations from '../features/home/ReadingRecommendations.vue'
const request = useRequest<Book[]>(),
  growth = useRequest<GrowthSummaryResponse>(),
  review = useRequest<ReviewSummary>(),
  search = ref(''),
  selected = ref<Book | null>(null),
  opening = ref(false),
  router = useRouter(),
  notices = useNoticeStore()
const visible = computed(() =>
  (request.data.value ?? []).filter((book) =>
    `${book.title} ${book.author}`.toLowerCase().includes(search.value.toLowerCase()),
  ),
)
const featured = computed(() => {
  const active = growth.data.value?.reading.activeBook
  const books = request.data.value ?? []
  if (active)
    return {
      ...active,
      coverUrl: books.find((book) => book.id === active.id)?.coverUrl || active.coverUrl,
    }
  return books.find((book) => book.status === 'completed') ?? null
})
const hasProgress = computed(
  () =>
    !!growth.data.value?.reading.activeBook &&
    featured.value?.id === growth.data.value.reading.activeBook.id,
)
const percentage = computed(() =>
  hasProgress.value ? Math.min(100, Math.max(0, featured.value?.progress ?? 0)) : 0,
)
const load = () => request.execute((signal) => catalog(signal))
onMounted(() => {
  void load()
  void growth.execute(
    async (signal) => (await api.get<GrowthSummaryResponse>('/growth/summary', { signal })).data,
  )
  void review.execute(
    async (signal) => (await api.get<ReviewSummary>('/review/summary', { signal })).data,
  )
})
async function read(book: { id: string }) {
  if (opening.value) return
  opening.value = true
  try {
    const value = await contents(book.id)
    const recent = growth.data.value?.reading.recentProgress.find((p) => p.bookId === book.id)
    const chapter = value.chapters.find((c) => c.id === recent?.chapterId) ?? value.chapters[0]
    if (!chapter) throw new Error('书籍尚未准备完成。')
    userStorage.setItem('readquest.bookId', book.id)
    await router.push({ path: `/app/reader/${chapter.id}`, query: { bookId: book.id } })
  } catch (error) {
    notices.notify(errorMessage(error))
  } finally {
    opening.value = false
  }
}
async function save(book: Book) {
  try {
    await saveBook(book.id)
    notices.notify('已加入书架。')
  } catch (error) {
    notices.notify(errorMessage(error))
  }
}
</script>
<template>
  <div class="page discovery-page">
    <div class="discovery-toolbar">
      <label class="search-box"
        ><Search :size="20" /><span class="sr-only">查找书籍</span
        ><input v-model="search" type="search" placeholder="搜索书名、作者或关键词…" /></label
      ><RouterLink to="/app/sources" class="button secondary"
        ><Plus :size="18" />添加阅读</RouterLink
      >
    </div>
    <div v-if="!search" class="discovery-top">
      <section class="reading-feature">
        <h1>留一点时间，<br />给下一章。</h1>
        <p class="feature-intro">从读懂一句，到走进一个新世界。</p>
        <div v-if="featured" class="feature-book">
          <div class="feature-cover">
            <img
              v-if="featured.coverUrl"
              :src="mediaUrl(featured.coverUrl)"
              :alt="`${featured.title} 封面`"
              @error="($event.target as HTMLImageElement).style.display = 'none'"
            /><span>{{ featured.title }}</span>
          </div>
          <div class="feature-copy">
            <span class="feature-label">{{ hasProgress ? '接着上次读' : '从这本书开始' }}</span>
            <h2>{{ featured.title }}</h2>
            <p>{{ featured.author }}</p>
            <div v-if="hasProgress" class="feature-progress">
              <progress :value="percentage" max="100" /><span>{{ Math.round(percentage) }}%</span>
            </div>
            <p v-else class="muted">选一个安静的片刻，打开第一章。</p>
            <button class="button ink-button" :disabled="opening" @click="read(featured)">
              {{ opening ? '正在打开…' : hasProgress ? '继续阅读' : '开始阅读'
              }}<ArrowRight :size="18" />
            </button>
          </div>
        </div>
        <div v-else class="feature-empty">
          <BookOpen :size="44" :stroke-width="1.3" />
          <p>你的下一段故事，从这里开始。</p>
          <RouterLink to="/app/upload" class="button ink-button"
            ><Plus :size="18" />添加第一本书</RouterLink
          >
        </div>
      </section>
      <aside class="discovery-rail">
        <section class="review-invitation">
          <div class="section-heading">
            <h2>今日复习</h2>
            <Layers :size="22" />
          </div>
          <p>回顾读过的词句，让理解留下来。</p>
          <div class="review-paper">
            <span>给记忆一点时间</span
            ><strong v-if="review.data.value"
              >{{ review.data.value.dueCount }}<small> 张卡片待复习</small></strong
            ><strong v-else class="review-paper-title">温故，知新。</strong>
            <p>
              {{
                review.error.value
                  ? '打开复习页查看最新安排。'
                  : review.data.value?.dueCount === 0
                    ? '暂时没有到期卡片，去阅读中积累新的表达。'
                    : '从原句出发，再认出熟悉的表达。'
              }}
            </p>
          </div>
          <RouterLink class="button ink-button" to="/app/review"
            >{{ review.data.value?.activeSession ? '继续复习' : '前往复习'
            }}<ArrowUpRight :size="18"
          /></RouterLink>
        </section>
        <section class="reading-glance">
          <div class="section-heading">
            <h2>阅读积累</h2>
            <RouterLink to="/app/journey" aria-label="查看阅读成长"
              ><ArrowUpRight :size="20"
            /></RouterLink>
          </div>
          <template v-if="growth.data.value"
            ><div>
              <Clock3 :size="19" /><span>累计阅读</span
              ><strong
                >{{ Math.round(growth.data.value.reading.totalReadingMinutes)
                }}<small> 分钟</small></strong
              >
            </div>
            <div>
              <Bookmark :size="19" /><span>记录词句</span
              ><strong>{{ growth.data.value.lookup.total }}<small> 条</small></strong>
            </div></template
          >
          <p v-else>
            每一次阅读，都从这里慢慢积累。<RouterLink to="/app/journey" class="text-link"
              >查看阅读成长</RouterLink
            >
          </p>
        </section>
      </aside>
    </div>
    <section class="books-section">
      <header class="section-heading">
        <div>
          <h2>{{ search ? '搜索结果' : '为你发现' }}</h2>
          <p>{{ search ? `找到 ${visible.length} 本书` : '选一本感兴趣的书，开始今天的阅读。' }}</p>
        </div>
        <RouterLink class="text-link" to="/app/library"
          >我的书架<ArrowUpRight :size="17"
        /></RouterLink>
      </header>
      <RequestState
        :loading="request.loading.value"
        :error="request.error.value"
        :empty="!visible.length"
        :message="
          search
            ? '没有找到匹配的书籍，试试其他书名或作者。'
            : '书架还空着，添加一本书就可以开始阅读。'
        "
        @retry="load"
        ><template #empty
          ><RouterLink to="/app/sources" class="button secondary">添加一本书</RouterLink></template
        >
        <div class="book-grid">
          <BookCard
            v-for="book in visible"
            :key="book.id"
            :book="book"
            @open="selected = $event"
          /></div
      ></RequestState>
    </section>
    <details class="recommendation-disclosure">
      <summary>探索适合我的阅读区间</summary>
      <ReadingRecommendations />
    </details>
    <ModalPanel v-if="selected" :title="selected.title" @close="selected = null"
      ><p class="muted">{{ selected.author }}</p>
      <p>{{ selected.description || '打开这本书，开始你的阅读。' }}</p>
      <div class="actions">
        <button
          class="button"
          :disabled="opening || selected.status !== 'completed'"
          @click="read(selected)"
        >
          {{ opening ? '正在打开…' : '开始阅读' }}</button
        ><button class="button secondary" @click="save(selected)">加入书架</button
        ><RouterLink :to="`/app/groups/${selected.id}`" class="button secondary"
          >读书小组</RouterLink
        >
      </div></ModalPanel
    >
  </div>
</template>
