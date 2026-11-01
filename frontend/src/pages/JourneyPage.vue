<script setup lang="ts">
import { onMounted } from 'vue'
import { api, type GrowthSummaryResponse } from '../lib/api'
import { useRequest } from '../composables/useRequest'
import RequestState from '../components/RequestState.vue'
const request = useRequest<GrowthSummaryResponse>()
const load = () =>
  request.execute(
    async (signal) => (await api.get<GrowthSummaryResponse>('/growth/summary', { signal })).data,
  )
onMounted(load)
</script>
<template>
  <div class="page">
    <header class="page-heading">
      <div>
        <h1>你的阅读旅程</h1>
        <p>用一次次真实阅读，积累可以看见的成长。</p>
      </div>
      <RouterLink class="button secondary" to="/app/passport">阅读护照</RouterLink>
    </header>
    <RequestState :loading="request.loading.value" :error="request.error.value" @retry="load"
      ><template v-if="request.data.value"
        ><div class="grid-three">
          <div class="stat">
            <strong>{{ Math.round(request.data.value.reading.totalReadingMinutes) }}</strong
            ><span>累计阅读分钟</span>
          </div>
          <div class="stat">
            <strong>{{ request.data.value.reading.completedBooks }}</strong
            ><span>读完的书</span>
          </div>
          <div class="stat">
            <strong>{{ request.data.value.lookup.total }}</strong
            ><span>记录的词句</span>
          </div>
        </div>
        <div class="grid-two mt-8">
          <section class="panel">
            <h2>阅读与理解</h2>
            <p>当前阅读范围：{{ request.data.value.profile.currentLexile }}L</p>
            <p>复习正确率：{{ Math.round(request.data.value.review.accuracy) }}%</p>
            <p>
              平均每段辅助：{{ request.data.value.flow.averageSupportPerParagraph.toFixed(1) }} 次
            </p>
            <p class="muted">{{ request.data.value.adjustment.reason }}</p>
          </section>
          <section class="panel">
            <h2>给下一次阅读</h2>
            <p v-for="(line, index) in request.data.value.feedback" :key="index">{{ line }}</p>
            <RouterLink class="button" to="/app/path">继续阅读</RouterLink>
          </section>
        </div>
        <section class="panel mt-8">
          <h2>最近阅读</h2>
          <div
            v-for="item in request.data.value.reading.bookProgress"
            :key="item.bookId"
            class="list-row"
          >
            <RouterLink :to="'/app/library'">{{
              request.data.value.reading.activeBook?.id === item.bookId
                ? request.data.value.reading.activeBook.title
                : '阅读记录'
            }}</RouterLink>
            <p class="muted">
              已读 {{ Math.round(item.progress) }}% · {{ item.completedParagraphs }} /
              {{ item.totalParagraphs }} 段
            </p>
            <div class="progress-track"><span :style="{ width: `${item.progress}%` }" /></div>
          </div>
          <p v-if="!request.data.value.reading.bookProgress.length" class="muted">
            完成第一次阅读后，进度会出现在这里。
          </p>
        </section></template
      ></RequestState
    >
  </div>
</template>
