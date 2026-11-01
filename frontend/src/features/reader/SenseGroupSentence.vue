<script setup lang="ts">
import { computed, onScopeDispose, ref } from 'vue'
import { api } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
import { readSenseGroups, senseGroupPieces, type SentenceGroups } from './core/senseGroups'

const props = defineProps<{
  bookId: string
  chapterId: string
  sentenceId: string
  text: string
  saved?: SentenceGroups
}>()
const emit = defineEmits<{
  help: [selectedText: string]
  saved: [result: SentenceGroups]
}>()
const result = ref<SentenceGroups | null>(null)
const busy = ref(false)
const error = ref('')
const expanded = ref(true)
const analysis = computed(() => props.saved ?? result.value)
const pieces = computed(() =>
  analysis.value ? senseGroupPieces(props.text, analysis.value.groups) : [],
)
const controller = new AbortController()
onScopeDispose(() => controller.abort())
async function analyze() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const { data } = await api.post(
      `/books/${props.bookId}/chapters/${props.chapterId}/sentence-chunks`,
      { sentenceId: props.sentenceId },
      { signal: controller.signal, timeout: 60000 },
    )
    if (controller.signal.aborted) return
    result.value = readSenseGroups(props.text, data)
    expanded.value = true
    emit('saved', result.value)
  } catch (cause) {
    if (!controller.signal.aborted) error.value = errorMessage(cause)
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
</script>

<template>
  <span class="sense-sentence">
    <span v-if="analysis && expanded" class="sense-text" data-sense-text
      ><template v-for="piece in pieces" :key="piece.start"
        ><button
          v-if="piece.kind === 'group'"
          class="sense-group"
          title="理解这个意群"
          @click.stop="emit('help', piece.text)"
        >
          {{ piece.text }}</button
        ><span v-else>{{ piece.text }}</span></template
      ></span
    >
    <span v-else class="sense-text" data-sense-text>{{ text }}</span>
    <span class="sense-actions">
      <button v-if="!analysis" class="sentence-help" :disabled="busy" @click.stop="analyze">
        {{ busy ? '划分中…' : error ? '重试意群' : '划分意群' }}
      </button>
      <button
        v-else
        class="sentence-help"
        :aria-pressed="expanded"
        @click.stop="expanded = !expanded"
      >
        {{ expanded ? '显示原句' : '显示意群' }}
      </button>
      <button class="sentence-help" aria-label="理解这个句子" @click.stop="emit('help', text)">
        ✧
      </button>
    </span>
    <small v-if="error" class="error" role="status">{{ error }}</small>
  </span>
</template>

<style scoped>
.sense-text {
  white-space: pre-wrap;
}
.sense-group {
  color: inherit;
  font: inherit;
  line-height: inherit;
  text-align: inherit;
  padding: 0;
  border: 0;
  border-bottom: 2px solid currentColor;
  border-radius: 2px;
  background: transparent;
  cursor: pointer;
}
.sense-group:hover,
.sense-group:focus-visible {
  background: color-mix(in srgb, currentColor 12%, transparent);
}
.sense-group:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 3px;
}
.sense-actions {
  display: inline-flex;
  gap: 0.25rem;
  margin-inline-start: 0.5rem;
}
</style>
