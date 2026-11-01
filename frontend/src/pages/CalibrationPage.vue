<script setup lang="ts">
import { ref, onMounted, onScopeDispose } from 'vue'
import { useRouter } from 'vue-router'
import {
  api,
  type CalibrationStartResponse,
  type CalibrationSubmitResponse,
  type CalibrationSample,
} from '../lib/api'
import { errorMessage } from '../lib/errors'
const router = useRouter(),
  sample = ref<CalibrationSample | null>(null),
  sessionId = ref(''),
  step = ref(1),
  total = ref(3),
  lookup = ref(0),
  understanding = ref(0.72),
  flow = ref(0.7),
  busy = ref(false),
  error = ref('')
let started = Date.now(),
  eventId = crypto.randomUUID()
const controller = new AbortController()
onScopeDispose(() => controller.abort())
async function start() {
  busy.value = true
  error.value = ''
  try {
    const data = (
      await api.post<CalibrationStartResponse>(
        '/calibration/start',
        {},
        { signal: controller.signal },
      )
    ).data
    if (controller.signal.aborted) return
    sample.value = data.sample
    sessionId.value = data.sessionId
    step.value = data.currentStep
    total.value = data.totalSteps
    started = Date.now()
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
onMounted(start)
async function submit() {
  if (!sample.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const data = (
      await api.post<CalibrationSubmitResponse>(
        `/calibration/${sessionId.value}/submit`,
        {
          eventId,
          readingSeconds: Math.max(3, Math.round((Date.now() - started) / 1000)),
          lookupCount: lookup.value,
          comprehensionScore: understanding.value,
          flowScore: flow.value,
        },
        { signal: controller.signal },
      )
    ).data
    if (controller.signal.aborted) return
    if (data.completed) {
      await router.replace('/app/passport')
      return
    }
    sample.value = data.sample ?? null
    step.value = data.currentStep ?? step.value + 1
    total.value = data.totalSteps ?? total.value
    lookup.value = 0
    started = Date.now()
    eventId = crypto.randomUUID()
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="page narrow">
    <header class="page-heading">
      <div>
        <h1>找到自然的阅读区间</h1>
        <p>读一段短文，再告诉我们理解和流畅程度。无需追求满分。</p>
      </div>
    </header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="busy && !sample" role="status">正在准备阅读样本…</p>
    <template v-if="sample"
      ><div class="row mb-4">
        <span>第 {{ step }} / {{ total }} 段</span
        ><span>{{ Math.round((step / total) * 100) }}%</span>
      </div>
      <div class="progress-track mb-6"><span :style="{ width: `${(step / total) * 100}%` }" /></div>
      <section class="panel">
        <h2>{{ sample.title }}</h2>
        <p class="review-sentence">{{ sample.text }}</p>
      </section>
      <form class="panel mt-6" @submit.prevent="submit">
        <label
          >理解程度<select v-model.number="understanding">
            <option :value="0.9">能清楚想象发生了什么</option>
            <option :value="0.72">大致理解，少数地方稍慢</option>
            <option :value="0.5">需要猜测较多内容</option>
          </select></label
        ><label
          >阅读流畅程度<select v-model.number="flow">
            <option :value="0.9">流畅</option>
            <option :value="0.7">平稳</option>
            <option :value="0.45">经常中断</option>
          </select></label
        ><label>查词次数<input v-model.number="lookup" type="number" min="0" max="200" /></label
        ><button class="button" :disabled="busy">
          {{ busy ? '正在记录…' : step === total ? '生成阅读护照' : '下一段' }}
        </button>
      </form></template
    ><button v-else-if="error" class="button" @click="start">重新准备样本</button>
  </div>
</template>
