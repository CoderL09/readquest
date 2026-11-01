<script setup lang="ts">
import { onMounted } from 'vue'
import { api, type ReadingPassportResponse } from '../lib/api'
import { useRequest } from '../composables/useRequest'
import RequestState from '../components/RequestState.vue'
const request = useRequest<ReadingPassportResponse>()
const load = () =>
  request.execute(
    async (signal) => (await api.get<ReadingPassportResponse>('/passport', { signal })).data,
  )
onMounted(load)
</script>
<template>
  <div class="page narrow">
    <header class="page-heading">
      <div>
        <h1>阅读护照</h1>
        <p>这不是分数，而是一张帮助你选择下一本书的地图。</p>
      </div>
      <RouterLink class="button secondary" to="/app/calibration">重新校准</RouterLink>
    </header>
    <RequestState :loading="request.loading.value" :error="request.error.value" @retry="load"
      ><template v-if="request.data.value"
        ><div class="grid-three">
          <div class="stat">
            <strong>{{ request.data.value.profile.currentLexile }}L</strong
            ><span>当前阅读水平</span>
          </div>
          <div class="stat">
            <strong
              >{{ request.data.value.profile.comfortZoneMin }}–{{
                request.data.value.profile.comfortZoneMax
              }}L</strong
            ><span>舒适阅读区间</span>
          </div>
          <div class="stat">
            <strong
              >{{ request.data.value.profile.iPlusOneMin }}–{{
                request.data.value.profile.iPlusOneMax
              }}L</strong
            ><span>推荐挑战区间</span>
          </div>
        </div>
        <section class="panel mt-8">
          <h2>让阅读成为日常</h2>
          <p>{{ request.data.value.estimatedJourney.milestone }}</p>
          <p class="muted">
            每天 {{ request.data.value.estimatedJourney.dailyMinutes }} 分钟 ·
            {{ request.data.value.estimatedJourney.timeframe }}
          </p>
          <RouterLink class="button" to="/app">选择下一本书</RouterLink>
        </section>
        <div class="grid-two mt-8">
          <section>
            <h2>舒适阅读参考</h2>
            <article
              v-for="book in request.data.value.comfortableBooks"
              :key="book.id || book._id"
              class="list-row"
            >
              <strong>{{ book.title }}</strong>
              <p class="muted">{{ book.author }} · {{ book.lexile }}L</p>
            </article>
          </section>
          <section>
            <h2>适度挑战参考</h2>
            <article
              v-for="book in request.data.value.challengeBooks"
              :key="book.id || book._id"
              class="list-row"
            >
              <strong>{{ book.title }}</strong>
              <p class="muted">{{ book.author }} · {{ book.lexile }}L</p>
            </article>
          </section>
        </div></template
      ></RequestState
    >
  </div>
</template>
