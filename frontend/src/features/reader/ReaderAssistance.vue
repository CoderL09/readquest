<script setup lang="ts">
import { onScopeDispose, ref } from 'vue'
import { api, mediaUrl } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
import { createBookNote } from '../notes/noteApi'
import {
  streamReaderConversation,
  type ReaderConversationTurn,
} from './core/readerConversationStream'
import type { ContextualWordMeaning, GrammarAnalysis, QuickAssistance } from './core/types'
import { useNoticeStore } from '../../stores/notices'
import { citationHref, type BookCitation } from './core/bookCitation'
import ConceptDrafts from '../review/ConceptDrafts.vue'
import type { ConceptDraft } from '../review/conceptDrafts'
const props = defineProps<{
    bookId: string
    chapterId: string
    paragraphIndex: number
    sentence: string
    sentenceId?: string
    selectedText: string
  }>(),
  emit = defineEmits<{ close: [] }>()
const mode = ref<'explain' | 'conversation' | 'chat' | 'note'>('explain'),
  busy = ref(false),
  error = ref(''),
  word = ref<ContextualWordMeaning | null>(null),
  analysis = ref<GrammarAnalysis | QuickAssistance | null>(null),
  dictionary = ref<{ headword: string; definition: string; sourceName: string } | null>(null),
  question = ref(''),
  turns = ref<
    Array<ReaderConversationTurn & { sources?: BookCitation[]; concepts?: ConceptDraft[] }>
  >([]),
  manualDrafts = ref<ConceptDraft[]>([]),
  note = ref(''),
  saved = ref(false),
  image = ref(''),
  supplement = ref<unknown>(null),
  conceptNotice = ref(''),
  notices = useNoticeStore()
let request: AbortController | null = null
function setMode(value: typeof mode.value) {
  mode.value = value
  turns.value = []
}
const lifetime = new AbortController()
onScopeDispose(() => {
  request?.abort()
  lifetime.abort()
})
async function run(task: (signal: AbortSignal) => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  const controller = new AbortController()
  request = controller
  try {
    await task(controller.signal)
  } catch (e) {
    if (!controller.signal.aborted) error.value = errorMessage(e)
  } finally {
    if (request === controller) {
      busy.value = false
      request = null
    }
  }
}
function explain(action: 'word' | 'meaning' | 'hint' | 'grammar' | 'translation') {
  return run(async (signal) => {
    const text = (action === 'word' ? props.selectedText : props.sentence).slice(0, 2500)
    word.value = null
    analysis.value = null
    dictionary.value = null
    const dictPromise =
      action === 'word'
        ? api
            .get<{ definition: typeof dictionary.value }>('/dictionaries/lookup', {
              params: { word: text },
              signal,
            })
            .then((r) => {
              if (!signal.aborted) dictionary.value = r.data.definition
            })
            .catch(() => {})
        : Promise.resolve()
    const explanation = await api
      .post<{
        analysis: ContextualWordMeaning | GrammarAnalysis | QuickAssistance
      }>(
        `/books/${props.bookId}/reading-explanations`,
        { text, action, contextSentence: props.sentence.slice(0, 2500) },
        { signal, timeout: 60000 },
      )
      .then((response) => ({ ok: true as const, data: response.data }))
      .catch((error) => ({ ok: false as const, error }))
    await dictPromise
    if (!explanation.ok) throw explanation.error
    const { data } = explanation
    if (signal.aborted) return
    if (action === 'word') word.value = data.analysis as ContextualWordMeaning
    else analysis.value = data.analysis as GrammarAnalysis | QuickAssistance
  })
}
async function saveWord() {
  await run(async (signal) => {
    await api.post(
      '/lookup-items',
      {
        bookId: props.bookId,
        chapterId: props.chapterId,
        paragraphIndex: props.paragraphIndex,
        sourceSentenceKey: props.sentenceId,
        type: 'word',
        text: props.selectedText,
        originalSentence: props.sentence,
        explanation: word.value?.meaningInContext || dictionary.value?.definition || '',
        translation: word.value?.sentenceTranslation || '',
        readingStrategy: word.value?.contextClue || '',
      },
      { signal },
    )
    saved.value = true
    notices.notify('已保存生词并加入 FSRS 复习。')
  })
}
async function saveNote() {
  await run(async () => {
    await createBookNote({
      bookId: props.bookId,
      chapterId: props.chapterId,
      paragraphIndex: props.paragraphIndex,
      quote: props.selectedText || props.sentence,
      note: note.value,
    })
    notices.notify('阅读笔记已保存')
    emit('close')
  })
}
async function send() {
  const text = question.value.trim()
  if (!text || busy.value) return
  const history = turns.value.slice(-10).map(({ role, content }) => ({ role, content }))
  question.value = ''
  await run(async (signal) => {
    turns.value.push({ role: 'user', content: text }, { role: 'assistant', content: '' })
    const index = turns.value.length - 1
    if (mode.value === 'conversation') {
      const result = await streamReaderConversation({
        bookId: props.bookId,
        chapterId: props.chapterId,
        sentence: props.sentence,
        sentenceId: props.sentenceId,
        sourceSentenceKey: props.sentenceId,
        question: text,
        history,
        signal,
        onToken: (delta) => {
          if (!signal.aborted) turns.value[index]!.content += delta
        },
      })
      turns.value[index]!.concepts =
        result.reviewConcepts?.status === 'suggested' ? (result.reviewConcepts.concepts ?? []) : []
      conceptNotice.value =
        result.reviewConcepts?.status === 'failed'
          ? '对话已完成，卡片建议暂未生成；你仍可手动制作理解卡。'
          : ''
    } else {
      const { data } = await api.post<{
        answer: string
        sources: BookCitation[]
      }>(
        `/books/${props.bookId}/chat-messages`,
        { question: text, chapterId: props.chapterId },
        { signal, timeout: 60000 },
      )
      turns.value[index]!.content = data.answer
      turns.value[index]!.sources = data.sources ?? []
    }
  })
}
function prepareConcept() {
  if (manualDrafts.value.length) return
  manualDrafts.value = [
    {
      bookId: props.bookId,
      chapterId: props.chapterId,
      sourceSentenceKey: props.sentenceId || `manual:${crypto.randomUUID()}`,
      originalSentence: props.sentence,
      category: 'reading-comprehension',
      reverseQuestionZh: `这句话中的「${(props.selectedText || props.sentence).slice(0, 80)}」应如何理解？`,
      referenceExplanationZh:
        analysis.value?.naturalTranslation ||
        word.value?.meaningInContext ||
        dictionary.value?.definition ||
        '',
      requiredPointsZh: [],
      targetFragments:
        props.selectedText && props.sentence.includes(props.selectedText)
          ? [{ text: props.selectedText }]
          : [],
    },
  ]
}
function extra(kind: 'context' | 'intervention' | 'image') {
  return run(async (signal) => {
    if (kind === 'image') {
      const { data } = await api.post<{ imageUrl: string }>(
        `/books/${props.bookId}/scene-images`,
        { prompt: props.sentence.slice(0, 1800) },
        { signal, timeout: 120000 },
      )
      image.value = data.imageUrl
    } else {
      const { data } = await api.post<unknown>(
        kind === 'context' ? `/books/${props.bookId}/context-analyses` : '/reading-interventions',
        kind === 'context'
          ? { currentSentence: props.sentence.slice(0, 5000) }
          : {
              surroundingText: props.sentence.slice(0, 10000),
              stuckChunk: (props.selectedText || props.sentence).slice(0, 5000),
            },
        { signal, timeout: 60000 },
      )
      supplement.value = data
    }
  })
}
function stop() {
  request?.abort()
  request = null
  busy.value = false
}
function describe(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return value.map(describe).filter(Boolean).join('\n')
  if (typeof value === 'object')
    return Object.entries(value)
      .map(([key, text]) => `${key}: ${describe(text)}`)
      .join('\n')
  return String(value)
}
</script>
<template>
  <aside class="reading-assistant" aria-label="阅读助手" @keydown.esc.stop="emit('close')">
    <header class="row">
      <div>
        <p class="eyebrow">READING COMPANION</p>
        <h2>阅读助手</h2>
      </div>
      <button class="icon-button" aria-label="收起阅读助手" @click="emit('close')">×</button>
    </header>
    <blockquote class="quote">{{ selectedText || sentence }}</blockquote>
    <div class="tabs">
      <button
        v-for="tab in [
          { id: 'explain', label: '阅读帮助' },
          { id: 'conversation', label: '追问句子' },
          { id: 'chat', label: '全书问答' },
          { id: 'note', label: '摘录笔记' },
        ] as const"
        :key="tab.id"
        :class="{ active: mode === tab.id }"
        :disabled="busy"
        @click="setMode(tab.id)"
      >
        {{ tab.label }}
      </button>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="mode === 'explain'" class="stack">
      <div class="actions">
        <button
          v-for="action in [
            { id: 'word', label: '词义' },
            { id: 'hint', label: '提示' },
            { id: 'meaning', label: '理解' },
            { id: 'grammar', label: '语法' },
            { id: 'translation', label: '翻译' },
          ] as const"
          :key="action.id"
          class="button secondary small"
          :disabled="busy"
          @click="explain(action.id)"
        >
          {{ action.label }}
        </button>
      </div>
      <section v-if="dictionary" class="panel">
        <h3>{{ dictionary.headword }}</h3>
        <p class="pre-wrap">{{ dictionary.definition }}</p>
        <small>{{ dictionary.sourceName }}</small>
      </section>
      <section v-if="word" class="stack">
        <h3>{{ word.meaningInContext }}</h3>
        <p>{{ word.contextClue }}</p>
        <p>{{ word.sentenceTranslation }}</p>
      </section>
      <section v-if="analysis" class="stack">
        <template v-if="'sentenceSkeleton' in analysis"
          ><h3>{{ analysis.sentenceSkeleton }}</h3>
          <p v-for="flow in analysis.meaningFlow" :key="flow">{{ flow }}</p>
          <div v-for="item in analysis.chunkRelationships" :key="item.chunk">
            <strong>{{ item.chunk }}</strong> · {{ item.role }}
          </div>
          <p v-for="item in analysis.grammar" :key="item.topic">
            <strong>{{ item.topic }}</strong
            >：{{ item.explanation }}
          </p></template
        ><template v-else
          ><h3>{{ analysis.meaningFirst }}</h3>
          <p>{{ analysis.readingMove }}</p>
          <p>{{ analysis.whyReadThisWay }}</p>
          <p>{{ analysis.nextTimeNotice }}</p></template
        >
        <p>{{ analysis.naturalTranslation }}</p>
        <p class="muted">{{ analysis.readingStrategy }}</p>
      </section>
      <button
        class="button secondary"
        :disabled="
          busy || saved || !selectedText || !(word?.meaningInContext || dictionary?.definition)
        "
        @click="saveWord"
      >
        {{ saved ? '已加入生词复习' : '保存生词并加入复习' }}
      </button>
      <a v-if="saved" href="/app/review" class="text-link" target="_blank" rel="noopener noreferrer"
        >前往复习</a
      >
      <button
        class="button secondary"
        :disabled="busy || !!manualDrafts.length"
        @click="prepareConcept"
      >
        制作句子理解卡
      </button>
      <ConceptDrafts v-if="manualDrafts.length" :drafts="manualDrafts" />
      <details>
        <summary>更多阅读帮助</summary>
        <div class="actions mt-4">
          <button class="button secondary small" :disabled="busy" @click="extra('context')">
            上下文与情感</button
          ><button class="button secondary small" :disabled="busy" @click="extra('intervention')">
            卡住了，换个提示</button
          ><button class="button secondary small" :disabled="busy" @click="extra('image')">
            生成场景图
          </button>
        </div>
        <p v-if="supplement" class="pre-wrap mt-4">{{ describe(supplement) }}</p>
        <img
          v-if="image"
          :src="mediaUrl(image)"
          alt="根据所选原文生成的场景"
          class="w-full rounded-xl mt-4"
        />
      </details>
    </div>
    <div v-else-if="mode === 'conversation' || mode === 'chat'" class="stack">
      <p class="muted">
        {{
          mode === 'conversation'
            ? '围绕当前句子追问，卡片建议核对确认后才会加入复习。'
            : '结合本章与全书内容回答，引用来源显示在下方。'
        }}
      </p>
      <div class="conversation" role="log" aria-live="polite">
        <article v-for="(turn, index) in turns" :key="index" class="chat-bubble" :class="turn.role">
          <p class="pre-wrap">
            {{ turn.content || (busy ? '正在思考…' : '本次回答中断，请重新提问。') }}
          </p>
          <details v-if="turn.sources?.length">
            <summary>参考原文 · {{ turn.sources.length }} 处</summary>
            <section v-for="(source, sourceIndex) in turn.sources" :key="sourceIndex" class="mt-4">
              <strong
                >[{{ source.citation ?? sourceIndex + 1 }}]
                {{ source.stageTitle || '原文章节' }}</strong
              >
              <blockquote class="quote">{{ source.content }}</blockquote>
              <a
                v-if="citationHref(source, bookId)"
                :href="citationHref(source, bookId)!"
                target="_blank"
                rel="noopener noreferrer"
                class="text-link"
                >定位原文（新标签页）</a
              >
            </section>
          </details>
          <ConceptDrafts v-if="turn.concepts?.length" :drafts="turn.concepts" />
        </article>
      </div>
      <p v-if="conceptNotice" class="success">{{ conceptNotice }}</p>
      <form @submit.prevent="send">
        <label
          >你的问题<input
            v-model="question"
            maxlength="2500"
            :disabled="busy"
            placeholder="为什么作者在这里这样写？" /></label
        ><button class="button" :disabled="busy || !question.trim()">发送问题</button>
      </form>
    </div>
    <form v-else class="stack" @submit.prevent="saveNote">
      <label
        >我的感想<textarea
          v-model="note"
          rows="5"
          maxlength="10000"
          placeholder="写下这一段让你想到的事…"
        /></label
      ><button class="button" :disabled="busy">保存摘录与笔记</button>
    </form>
    <p v-if="busy" class="muted mt-4" role="status">
      正在处理… <button class="text-link" @click="stop">取消</button>
    </p>
  </aside>
</template>
