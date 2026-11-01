<script setup lang="ts">
import { onScopeDispose, ref } from 'vue'
import { api } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
import type { ConceptDraft } from './conceptDrafts'

const props = defineProps<{ drafts: ConceptDraft[] }>()
const entries = ref(
  props.drafts.map((draft) => ({
    draft: { ...draft },
    points: draft.requiredPointsZh.join('\n'),
    busy: false,
    saved: false,
    dismissed: false,
    error: '',
    dueAt: '',
  })),
)
const controller = new AbortController()
onScopeDispose(() => controller.abort())
async function confirm(index: number) {
  const entry = entries.value[index]!
  if (entry.busy || entry.saved) return
  entry.busy = true
  entry.error = ''
  try {
    const { data } = await api.post<{ dueAt?: string }>(
      '/review-cards',
      {
        ...entry.draft,
        reverseQuestionZh: entry.draft.reverseQuestionZh.trim(),
        referenceExplanationZh: entry.draft.referenceExplanationZh.trim(),
        requiredPointsZh: entry.points
          .split('\n')
          .map((p) => p.trim())
          .filter(Boolean),
      },
      { signal: controller.signal },
    )
    if (controller.signal.aborted) return
    entry.saved = true
    entry.dueAt = data.dueAt ?? ''
  } catch (cause) {
    if (!controller.signal.aborted) entry.error = errorMessage(cause)
  } finally {
    if (!controller.signal.aborted) entry.busy = false
  }
}
</script>

<template>
  <section class="stack" aria-label="待确认复习卡片">
    <p class="muted">预览并核对后再加入复习。未确认的内容不会创建卡片。</p>
    <template v-for="(entry, index) in entries" :key="index">
      <article v-if="!entry.dismissed" class="panel stack">
        <blockquote class="quote">{{ entry.draft.originalSentence }}</blockquote>
        <template v-if="entry.saved">
          <p class="success" role="status">已加入 FSRS 复习。</p>
          <p v-if="entry.dueAt" class="muted">
            下次复习：{{ new Date(entry.dueAt).toLocaleString() }}
          </p>
          <a href="/app/review" class="text-link" target="_blank" rel="noopener noreferrer"
            >前往复习</a
          >
        </template>
        <form v-else class="stack" @submit.prevent="confirm(index)">
          <label
            >回忆问题<input
              v-model="entry.draft.reverseQuestionZh"
              required
              maxlength="1000"
              :disabled="entry.busy"
          /></label>
          <label
            >参考理解<textarea
              v-model="entry.draft.referenceExplanationZh"
              required
              maxlength="4000"
              :disabled="entry.busy"
            />
          </label>
          <label
            >回忆要点（每行一项，最多 20 项）<textarea
              v-model="entry.points"
              required
              :disabled="entry.busy"
            />
          </label>
          <p v-if="entry.error" class="error" role="alert">{{ entry.error }}</p>
          <div class="actions">
            <button class="button small" :disabled="entry.busy">
              {{ entry.busy ? '正在保存…' : '确认加入复习' }}
            </button>
            <button
              type="button"
              class="button secondary small"
              :disabled="entry.busy"
              @click="entry.dismissed = true"
            >
              暂不加入
            </button>
          </div>
        </form>
      </article>
    </template>
  </section>
</template>
