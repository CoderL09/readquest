<script setup lang="ts">
import { computed, onScopeDispose, ref } from 'vue'
import ReadingAssistant from '../features/review/ReadingAssistant.vue'
import { api } from '../lib/api'
import { errorMessage } from '../lib/errors'
import { reviewSourceHref } from '../features/review/conceptDrafts'
import {
  initialReviewState,
  reviewExperienceReducer,
  type ReviewExperienceAction,
} from '../features/review/reviewReducer'
import type {
  ActiveReviewSession,
  ReviewSummary,
  ReviewAnswerResponse,
  ReviewLibraryResponse,
  ReviewGroupSize,
} from '../features/review/contracts'
const state = ref({ ...initialReviewState }),
  busy = ref(false),
  size = ref<ReviewGroupSize>(5),
  library = ref<ReviewLibraryResponse | null>(null)
const controller = new AbortController()
onScopeDispose(() => controller.abort())
const dispatch = (action: ReviewExperienceAction) => {
  state.value = reviewExperienceReducer(state.value, action)
}
function changeDraft(event: Event) {
  dispatch({ type: 'DRAFT_CHANGED', value: (event.target as HTMLTextAreaElement).value })
  answerKey = ''
}
const card = computed(() => state.value.session?.card),
  stage = computed(() => state.value.session?.currentStage)
const canAnswer = computed(() =>
  ['initialAnswer', 'guidedRevision', 'transferSentence'].includes(stage.value ?? ''),
)
let startedAt = Date.now(),
  answerKey = '',
  advanceKey = '',
  answerTime = 0
async function hydrate() {
  try {
    const [summary, active] = await Promise.all([
      api.get<ReviewSummary>('/review/summary', { signal: controller.signal }),
      api.get<{ session: ActiveReviewSession | null }>('/review/sessions/active', {
        signal: controller.signal,
      }),
    ])
    dispatch({ type: 'HYDRATED', summary: summary.data, session: active.data.session })
    startedAt = Date.now()
  } catch (e) {
    if (!controller.signal.aborted) dispatch({ type: 'FATAL_ERROR', message: errorMessage(e) })
  }
}
async function start() {
  if (busy.value) return
  busy.value = true
  try {
    const { data } = await api.post<{ session: ActiveReviewSession }>(
      '/review/sessions',
      { groupSize: size.value },
      { signal: controller.signal },
    )
    dispatch({ type: 'SESSION_UPDATED', session: data.session })
    startedAt = Date.now()
  } catch (e) {
    dispatch({ type: 'FATAL_ERROR', message: errorMessage(e) })
  } finally {
    busy.value = false
  }
}
async function answer() {
  const session = state.value.session
  if (!session?.card || busy.value || !state.value.draft.trim()) return
  busy.value = true
  if (!answerKey) {
    answerKey = crypto.randomUUID()
    answerTime = Date.now() - startedAt
  }
  dispatch({ type: 'SUBMIT_STARTED' })
  try {
    const { data } = await api.post<ReviewAnswerResponse>(
      `/review/sessions/${session.id}/cards/${session.card.id}/answers`,
      {
        answerKind:
          stage.value === 'transferSentence'
            ? 'transferSentence'
            : session.card.kind === 'wordMeaning'
              ? 'wordMeaning'
              : 'conceptExplanation',
        stage: stage.value === 'guidedRevision' ? 'guidedRevision' : 'initial',
        answer: state.value.draft.trim(),
        responseTimeMs: answerTime,
        idempotencyKey: answerKey,
      },
      { signal: controller.signal },
    )
    dispatch({ type: 'SUBMIT_SUCCEEDED', result: data })
    answerKey = ''
    startedAt = Date.now()
  } catch (e) {
    if (!controller.signal.aborted) dispatch({ type: 'SUBMIT_FAILED', message: errorMessage(e) })
  } finally {
    busy.value = false
  }
}
async function advance() {
  if (!state.value.session || busy.value) return
  busy.value = true
  advanceKey ||= crypto.randomUUID()
  try {
    const { data } = await api.post<{ session: ActiveReviewSession }>(
      `/review/sessions/${state.value.session.id}/advance`,
      { eventId: advanceKey },
      { signal: controller.signal },
    )
    dispatch({ type: 'RESET_FOR_STAGE', session: data.session })
    advanceKey = ''
    answerKey = ''
    startedAt = Date.now()
  } catch (e) {
    state.value.error = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function complete() {
  if (!state.value.session || busy.value) return
  busy.value = true
  try {
    const { data } = await api.post<{
      nextSession: ActiveReviewSession | null
      summary: ReviewSummary
    }>(
      `/review/sessions/${state.value.session.id}/celebration-complete`,
      {},
      { signal: controller.signal },
    )
    dispatch({ type: 'HYDRATED', summary: data.summary, session: data.nextSession })
  } catch (e) {
    state.value.error = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function loadLibrary(page = 1) {
  try {
    const { data } = await api.get<ReviewLibraryResponse>('/review/library', {
      params: { page, pageSize: 24 },
      signal: controller.signal,
    })
    library.value = data
  } catch (e) {
    state.value.error = errorMessage(e)
  }
}
void hydrate()
</script>
<template>
  <section class="page review-workspace">
    <header class="page-heading">
      <div>
        <h1>让记忆，多停留一会儿。</h1>
        <p class="muted">今天的复习，从读过的句子开始。</p>
      </div>
      <button class="button secondary" @click="loadLibrary()">复习卡片库</button>
    </header>
    <details class="assistant-disclosure">
      <summary>阅读复习助手<span>结合阅读记录，整理练习建议</span></summary>
      <ReadingAssistant @confirmed="hydrate" />
    </details>
    <p v-if="state.error" class="error" role="alert">
      {{ state.error }} <button v-if="state.status === 'error'" @click="hydrate">重试</button>
    </p>
    <div v-if="state.status === 'loading'" class="empty" role="status">正在恢复复习进度…</div>
    <div v-else-if="state.status === 'setup'" class="panel stack">
      <h2>今天有 {{ state.summary?.dueCount }} 张卡片待复习</h2>
      <label
        >每组数量<select v-model="size">
          <option :value="5">5 张</option>
          <option :value="10">10 张</option>
          <option :value="20">20 张</option>
        </select></label
      ><button class="button" :disabled="busy" @click="start">开始复习</button>
    </div>
    <div v-else-if="state.status === 'complete'" class="panel empty">
      <h2>今天的复习已完成</h2>
      <p>继续阅读，遇见新的表达。</p>
      <p v-if="state.summary?.nextDueAt">
        下次复习：{{ new Date(state.summary.nextDueAt).toLocaleString() }}
      </p>
      <RouterLink to="/app/library" class="button">回到书架</RouterLink>
    </div>
    <div v-else-if="state.status === 'celebrating'" class="panel empty">
      <p class="eyebrow">WELL READ</p>
      <h2>这一组完成了</h2>
      <p>给理解一点时间，也给自己一点掌声。</p>
      <button class="button" :disabled="busy" @click="complete">完成本组</button>
    </div>
    <article v-else-if="card" class="panel stack recall-card">
      <div class="row between">
        <span class="badge">{{ card.kind === 'wordMeaning' ? '语境词义' : '句子理解' }}</span
        ><span>{{ (state.session?.currentIndex ?? 0) + 1 }} / {{ state.session?.total }}</span>
      </div>
      <div v-if="card.kind === 'wordMeaning'" class="recall-term">{{ card.targetText }}</div>
      <progress
        class="session-progress"
        :value="state.session?.currentIndex ?? 0"
        :max="state.session?.total || 1"
        aria-label="本次复习进度"
      />
      <blockquote class="reading-quote">{{ card.originalSentence }}</blockquote>
      <a
        v-if="reviewSourceHref(card)"
        :href="reviewSourceHref(card)!"
        class="text-link"
        target="_blank"
        rel="noopener noreferrer"
        >回到原文核对（新标签页）</a
      >
      <p v-if="card.dueAt" class="muted">
        本次计划复习时间：{{ new Date(card.dueAt).toLocaleString() }}
      </p>
      <h2 v-if="card.kind === 'wordMeaning'">「{{ card.targetText }}」在这里是什么意思？</h2>
      <h2 v-else>{{ card.reverseQuestionZh }}</h2>
      <p v-if="stage === 'transferSentence'">请用刚才理解的表达写一个新句子。</p>
      <p v-if="state.grade" class="notice">
        {{ state.grade.feedbackZh }}
        <span v-if="stage === 'hintShown'">{{ state.grade.contextHintZh }}</span>
      </p>
      <div v-if="card.referenceAnswerZh" class="panel subtle">
        <h3>参考理解</h3>
        <p>{{ card.referenceAnswerZh }}</p>
      </div>
      <form v-if="canAnswer" class="stack" @submit.prevent="answer">
        <label
          >你的回答<textarea
            :value="state.draft"
            :disabled="busy"
            maxlength="5000"
            rows="4"
            @input="changeDraft"
          /></label
        ><button class="button" :disabled="busy || !state.draft.trim()">
          {{ busy ? '正在评估…' : '提交回答' }}
        </button>
      </form>
      <button
        v-else-if="['hintShown', 'referenceShown', 'feedback'].includes(stage ?? '')"
        class="button"
        :disabled="busy"
        @click="advance"
      >
        {{
          stage === 'hintShown'
            ? '根据提示再试一次'
            : stage === 'referenceShown'
              ? '继续练习'
              : '下一张'
        }}</button
      ><button v-else-if="stage === 'gradingPending'" class="button secondary" @click="hydrate">
        评估处理中，刷新结果
      </button>
    </article>
    <section v-if="library" class="stack section-space">
      <h2>卡片库 · {{ library.total }}</h2>
      <article v-for="item in library.items" :key="item.id" class="panel">
        <span class="badge">{{ item.isDue ? '待复习' : '已安排' }}</span>
        <p>{{ item.originalSentence }}</p>
        <p class="muted">
          {{ item.kind === 'wordMeaning' ? item.targetText : item.reverseQuestionZh }}
        </p>
        <p class="muted">下次复习：{{ new Date(item.dueAt).toLocaleString() }}</p>
        <a
          v-if="reviewSourceHref(item)"
          :href="reviewSourceHref(item)!"
          class="text-link"
          target="_blank"
          rel="noopener noreferrer"
          >回看原文</a
        >
      </article>
      <div class="row">
        <button
          class="button secondary"
          :disabled="library.page <= 1"
          @click="loadLibrary(library.page - 1)"
        >
          上一页</button
        ><button
          class="button secondary"
          :disabled="!library.hasMore"
          @click="loadLibrary(library.page + 1)"
        >
          下一页
        </button>
      </div>
    </section>
  </section>
</template>
