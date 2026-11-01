<script setup lang="ts">
import { ref, onMounted, onScopeDispose } from 'vue'
import { api } from '../lib/api'
import { inspectImportUrl, createUrlImport, getImportJob } from '../features/imports/importApi'
import type { ImportInspection, ImportJob } from '../features/imports/contracts'
import { errorMessage } from '../lib/errors'
import CatalogExplorer from '../features/catalog/CatalogExplorer.vue'
const url = ref(''),
  title = ref(''),
  rights = ref(false),
  inspection = ref<ImportInspection | null>(null),
  job = ref<ImportJob | null>(null),
  busy = ref(false),
  error = ref(''),
  sources = ref<
    Array<{
      id: string
      name: string
      url?: string
      homepage?: string
      description?: string
      notice?: string
    }>
  >([])
const controller = new AbortController()
let timer: ReturnType<typeof setTimeout> | undefined
onScopeDispose(() => {
  controller.abort()
  clearTimeout(timer)
})
onMounted(async () => {
  try {
    const data = (await api.get('/sources', { signal: controller.signal })).data
    sources.value = Array.isArray(data) ? data : (data.sources ?? [])
  } catch {}
})
async function inspect() {
  busy.value = true
  error.value = ''
  inspection.value = null
  try {
    inspection.value = await inspectImportUrl(url.value, controller.signal)
    title.value = inspection.value.preview.title
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
async function poll(id: string) {
  try {
    job.value = await getImportJob(id, controller.signal)
    if (!['completed', 'failed', 'canceled'].includes(job.value.status))
      timer = setTimeout(() => void poll(id), 2000)
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  }
}
async function start() {
  busy.value = true
  error.value = ''
  try {
    job.value = (
      await createUrlImport(
        { url: url.value, title: title.value, rightsAttested: rights.value },
        controller.signal,
      )
    ).job
    await poll(job.value.id)
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="page">
    <header class="page-heading">
      <div>
        <h1>阅读可以从任何地方开始</h1>
        <p>上传一本书，或导入一篇有权阅读和分享的公开文章。</p>
      </div>
    </header>
    <div class="grid-two">
      <section class="panel">
        <h2>从文件开始</h2>
        <p class="muted">支持 EPUB、PDF 和 TXT，保留章节与原文。</p>
        <RouterLink class="button" to="/app/upload">上传书籍</RouterLink>
      </section>
      <section class="panel stack">
        <h2>从公开链接开始</h2>
        <form @submit.prevent="inspect">
          <label
            >公开网页或文件地址<input
              v-model="url"
              type="url"
              required
              maxlength="4096"
              placeholder="https://…"
              :disabled="busy"
              @input="inspection = null" /></label
          ><button class="button secondary" :disabled="busy">
            {{ busy ? '正在检查…' : '检查链接' }}
          </button>
        </form>
        <p class="muted">不支持登录限制、付费墙、访问验证及私有网络地址。</p>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <form v-if="inspection" @submit.prevent="start">
          <label>标题<input v-model="title" maxlength="300" /></label>
          <p>{{ inspection.preview.excerpt }}</p>
          <label class="checkbox"
            ><input v-model="rights" type="checkbox" />我有权导入并公开分享此内容。</label
          ><button class="button" :disabled="busy || !rights">导入阅读</button>
        </form>
        <div v-if="job" role="status">
          <strong>导入状态：{{ job.status }}</strong>
          <p v-if="job.error" class="error">{{ job.error.message }}</p>
          <div class="progress-track">
            <span :style="{ width: `${job.progress?.percent ?? 0}%` }" />
          </div>
          <RouterLink
            v-if="job.bookId && job.firstStageId"
            class="button"
            :to="{ path: `/app/reader/${job.firstStageId}`, query: { bookId: job.bookId } }"
            >打开阅读</RouterLink
          ><RouterLink v-else-if="job.status === 'completed'" class="button" to="/app/library"
            >到书架查看</RouterLink
          >
        </div>
      </section>
    </div>
    <CatalogExplorer />
    <section v-if="sources.length" class="mt-10">
      <h2>寻找下一本书</h2>
      <div class="grid-three">
        <article v-for="source in sources" :key="source.id" class="panel">
          <h3>{{ source.name }}</h3>
          <p class="muted">{{ source.description || source.notice }}</p>
          <a
            v-if="
              (source.url || source.homepage) &&
              /^https?:\/\//.test(source.url || source.homepage || '')
            "
            :href="source.url || source.homepage"
            target="_blank"
            rel="noopener noreferrer"
            class="button secondary small"
            >浏览来源</a
          >
        </article>
      </div>
    </section>
  </div>
</template>
