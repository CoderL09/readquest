<script setup lang="ts">
import { onMounted, onScopeDispose, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
import { createCatalogImport, getImportJob } from '../imports/importApi'
import type { ImportJob } from '../imports/contracts'
type Card = {
  id: string
  title: string
  authors: string[]
  coverUrl: string | null
  description: string
  availability: { kind: string; providerId: string; externalId: string; offerId: string | null }
  links: { preview: string | null; external: string | null }
}
type Results = {
  items: Card[]
  nextCursor?: string | null
  partial?: boolean
  providers?: Array<{ providerId?: string; status?: string; state?: string }>
}
const query = ref(String(useRoute().query.q ?? '')),
  category = ref(''),
  provider = ref('combined'),
  categories = ref<Array<{ id: string; label: string }>>([]),
  cards = ref<Card[]>([]),
  cursor = ref<string | null>(null),
  busy = ref(false),
  error = ref(''),
  searched = ref(false),
  partial = ref(false),
  job = ref<ImportJob | null>(null),
  importing = ref(false)
watch([query, category, provider], () => {
  cursor.value = null
  request?.abort()
  busy.value = false
})
const lifetime = new AbortController()
let request: AbortController | null = null,
  timer: ReturnType<typeof setTimeout> | undefined
onScopeDispose(() => {
  lifetime.abort()
  request?.abort()
  clearTimeout(timer)
})
onMounted(async () => {
  try {
    categories.value = (
      await api.get<{ categories: typeof categories.value }>('/catalog/categories', {
        signal: lifetime.signal,
      })
    ).data.categories
  } catch {
    /* Search remains available without categories. */
  }
})
async function search(more = false, trending = false) {
  request?.abort()
  const controller = new AbortController()
  request = controller
  busy.value = true
  error.value = ''
  try {
    const { data } = await api.get<Results>(trending ? '/catalog/trending' : '/catalog/search', {
      params: {
        ...(trending
          ? { window: 'week' }
          : { q: query.value.trim(), cursor: more ? cursor.value : undefined }),
        provider: trending ? 'openlibrary' : provider.value,
        category: category.value || undefined,
        limit: 20,
      },
      signal: controller.signal,
      timeout: 30000,
    })
    if (controller.signal.aborted) return
    cards.value = more
      ? [
          ...cards.value,
          ...data.items.filter((card) => !cards.value.some((prior) => prior.id === card.id)),
        ]
      : data.items
    cursor.value = data.nextCursor ?? null
    partial.value = !!data.partial
    searched.value = true
  } catch (e) {
    if (!controller.signal.aborted) error.value = errorMessage(e)
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
async function poll(id: string) {
  try {
    job.value = await getImportJob(id, lifetime.signal)
    if (!['completed', 'failed', 'canceled'].includes(job.value.status))
      timer = setTimeout(() => void poll(id), 1500)
    else importing.value = false
  } catch (e) {
    if (!lifetime.signal.aborted) {
      error.value = errorMessage(e)
      importing.value = false
    }
  }
}
async function importBook(card: Card) {
  if (importing.value || !card.availability.offerId) return
  importing.value = true
  error.value = ''
  try {
    job.value = (
      await createCatalogImport(
        {
          providerId: card.availability.providerId,
          externalId: card.availability.externalId,
          offerId: card.availability.offerId,
        },
        lifetime.signal,
      )
    ).job
    await poll(job.value.id)
  } catch (e) {
    if (!lifetime.signal.aborted) {
      error.value = errorMessage(e)
      importing.value = false
    }
  }
}
const safeLink = (link: string | null) => (link && /^https?:\/\//i.test(link) ? link : undefined)
</script>
<template>
  <section class="stack section-space">
    <header>
      <p class="eyebrow">FIND YOUR NEXT READ</p>
      <h2>搜索公开书目</h2>
      <p class="muted">书目来源提供搜索与预览；有完整公版下载授权的书籍可直接导入。</p>
    </header>
    <form @submit.prevent="search()">
      <label
        >书名、作者或关键词<input
          v-model="query"
          maxlength="200"
          placeholder="例如 Alice in Wonderland"
          required
      /></label>
      <div class="field-grid">
        <label
          >来源<select v-model="provider">
            <option value="combined">综合来源</option>
            <option value="googlebooks">Google Books</option>
            <option value="openlibrary">Open Library</option>
          </select></label
        ><label
          >类别<select v-model="category">
            <option value="">全部类别</option>
            <option v-for="item in categories" :key="item.id" :value="item.id">
              {{ item.label }}
            </option>
          </select></label
        >
      </div>
      <div class="actions">
        <button class="button" :disabled="busy || !query.trim()">
          {{ busy ? '正在查找…' : '查找书目' }}</button
        ><button
          class="button secondary"
          type="button"
          :disabled="busy"
          @click="search(false, true)"
        >
          Open Library 本周热门
        </button>
      </div>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="partial" class="muted">部分书目来源暂时不可用，已展示可用结果。</p>
    <div v-if="job" class="panel stack" role="status">
      <p>书籍导入：{{ job.status }} · {{ job.progress.percent }}%</p>
      <p v-if="job.error" class="error">{{ job.error.message }}</p>
      <RouterLink
        v-if="job.bookId && job.firstStageId"
        class="button"
        :to="{ path: `/app/reader/${job.firstStageId}`, query: { bookId: job.bookId } }"
        >打开导入的书籍</RouterLink
      >
    </div>
    <p v-if="searched && !cards.length && !busy" class="state">
      没有找到匹配书目，试试其他关键词。
    </p>
    <div class="card-grid">
      <article v-for="card in cards" :key="card.id" class="panel stack">
        <img
          v-if="card.coverUrl && safeLink(card.coverUrl)"
          :src="card.coverUrl"
          :alt="card.title"
          loading="lazy"
          class="catalog-cover"
        />
        <h3>{{ card.title }}</h3>
        <small>{{ card.authors.join(' / ') }}</small>
        <p class="catalog-description">{{ card.description }}</p>
        <div class="actions">
          <button
            v-if="card.availability.kind === 'download' && card.availability.offerId"
            class="button small"
            :disabled="importing"
            @click="importBook(card)"
          >
            导入公版书籍</button
          ><a
            v-if="safeLink(card.links.preview)"
            :href="safeLink(card.links.preview)"
            target="_blank"
            rel="noopener noreferrer"
            class="button secondary small"
            >预览</a
          ><a
            v-if="safeLink(card.links.external)"
            :href="safeLink(card.links.external)"
            target="_blank"
            rel="noopener noreferrer"
            class="text-link"
            >来源详情 ↗</a
          >
        </div>
      </article>
    </div>
    <button v-if="cursor" class="button secondary" :disabled="busy" @click="search(true)">
      加载更多书目
    </button>
  </section>
</template>
