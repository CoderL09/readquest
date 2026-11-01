<script setup lang="ts">
import { onMounted } from 'vue'
import { api, type LibraryMetadataResponse } from '../../lib/api'
import { useRequest } from '../../composables/useRequest'
const request = useRequest<LibraryMetadataResponse>()
const load = () =>
  request.execute(
    async (signal) =>
      (await api.get<LibraryMetadataResponse>('/library/metadata', { signal })).data,
  )
onMounted(load)
</script>
<template>
  <section class="section-space">
    <div class="row">
      <div>
        <p class="eyebrow">YOUR READING RANGE</p>
        <h2>适合你的阅读区间</h2>
      </div>
      <RouterLink class="text-link" to="/app/calibration">重新校准 →</RouterLink>
    </div>
    <p v-if="request.loading.value" class="muted" role="status">正在整理阅读建议…</p>
    <p v-if="request.error.value" class="muted">
      阅读建议暂不可用。<button class="text-link" @click="load">重试</button>
    </p>
    <template v-if="request.data.value"
      ><p class="muted">
        舒适区 {{ request.data.value.profile.comfortZoneMin }}–{{
          request.data.value.profile.comfortZoneMax
        }}
        L · 挑战区 {{ request.data.value.profile.iPlusOneMin }}–{{
          request.data.value.profile.iPlusOneMax
        }}
        L
      </p>
      <div v-for="band in request.data.value.bands" :key="band.label" class="section-space">
        <h3>{{ band.label }}</h3>
        <div class="card-grid">
          <article v-for="book in band.books.slice(0, 4)" :key="book._id" class="panel stack">
            <h3>{{ book.title }}</h3>
            <p class="muted">{{ book.author }} · {{ book.lexile }} L</p>
            <p class="catalog-description">{{ book.description }}</p>
            <RouterLink v-if="book.linkedBookId" class="button secondary small" to="/app/library"
              >前往书架</RouterLink
            ><RouterLink
              v-else
              class="text-link"
              :to="{ path: '/app/sources', query: { q: book.title } }"
              >查找可用书目 →</RouterLink
            >
          </article>
        </div>
      </div>
      <RouterLink class="text-link" to="/app/passport">查看完整阅读护照 →</RouterLink></template
    >
  </section>
</template>
