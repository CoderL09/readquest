<script setup lang="ts">
import { computed, nextTick, onScopeDispose, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { api, apiBaseUrl } from '../lib/api'
import { userStorage } from '../lib/userStorage'
import { useReader } from '../features/reader/useReader'
import ReaderAssistance from '../features/reader/ReaderAssistance.vue'
import SenseGroupSentence from '../features/reader/SenseGroupSentence.vue'
import type { SentenceGroups } from '../features/reader/core/senseGroups'
import ModalPanel from '../components/ModalPanel.vue'
import ChapterQuiz, { type ChapterQuizData } from '../features/reader/ChapterQuiz.vue'
import {
  THEME_PRESETS,
  FONT_OPTIONS,
  applyThemePreset,
  getReaderCssVars,
} from '../features/reader/core/readerPreferences'
import { getWordSelectionAtPoint } from '../features/reader/core/readerInquiry'
import { errorMessage } from '../lib/errors'
import {
  citationTarget,
  resolveCitation,
  type BookCitation,
} from '../features/reader/core/bookCitation'
const route = useRoute(),
  router = useRouter(),
  bookId = String(route.query.bookId ?? userStorage.getItem('readquest.bookId') ?? ''),
  chapterId = String(route.params.stageId),
  reader = useReader(bookId, chapterId)
const settingsOpen = ref(false),
  contentsOpen = ref(false),
  display = ref<'original' | 'phrase'>(
    reader.settings.value.annotationMode === 'original' ? 'original' : 'phrase',
  ),
  root = ref<HTMLElement | null>(null),
  selection = ref<{ text: string; sentence: string; id?: string } | null>(null),
  speaking = ref(false),
  speechError = ref(''),
  quizOpen = ref(false),
  quizBusy = ref(false),
  quizError = ref(''),
  quiz = ref<ChapterQuizData | null>(null)
const assistantOpen = ref(window.innerWidth > 1100)
watch(selection, (value) => {
  if (value) assistantOpen.value = true
})
const sentenceGroups = ref<Record<string, SentenceGroups>>({})
watch(display, (mode) => {
  reader.settings.value.annotationMode = mode === 'original' ? 'original' : 'rhythm'
})
const sourceTarget = citationTarget(route.query)
const citation = ref<Awaited<ReturnType<typeof resolveCitation>> | null>(null)
const citationError = ref('')
let citationAttempt = 0
watch(
  [() => reader.loading.value, () => reader.error.value],
  async ([loading, error]) => {
    const attempt = ++citationAttempt
    if (loading || error || !('sourceStart' in route.query || 'reviewCard' in route.query)) return
    citation.value = null
    citationError.value = ''
    try {
      let target = sourceTarget
      if ('reviewCard' in route.query) {
        if (
          typeof route.query.reviewCard !== 'string' ||
          !/^[A-Za-z0-9-]{1,36}$/.test(route.query.reviewCard)
        )
          throw new Error('卡片来源链接不完整，请从复习页重新打开。')
        const { data } = await api.get<BookCitation>(
          `/review-cards/${route.query.reviewCard}/source`,
          { signal: reader.signal },
        )
        if (data.bookId !== bookId || data.stageId !== chapterId)
          throw new Error('卡片与当前章节不匹配。')
        target = citationTarget({
          sourceStart: String(data.startOffset),
          sourceEnd: String(data.endOffset),
          sourceHash: data.chapterHash,
        })
      }
      if (!target) throw new Error('引用链接不完整，请重新打开来源。')
      const text = reader.current.value?.contentText ?? reader.current.value?.content ?? ''
      const resolved = await resolveCitation(text, reader.paragraphs.value, target)
      if (attempt !== citationAttempt || reader.signal.aborted) return
      citation.value = resolved
      display.value = 'phrase'
      reader.select(resolved.paragraphIndex)
      await nextTick()
      document
        .getElementById(`paragraph-${resolved.paragraphIndex}`)
        ?.scrollIntoView({ block: 'center' })
    } catch (error) {
      if (attempt === citationAttempt && !reader.signal.aborted)
        citationError.value = errorMessage(error)
    }
  },
  { immediate: true },
)
const vars = computed(() => getReaderCssVars(reader.settings.value)),
  currentParagraph = computed(() => reader.paragraphs.value[reader.paragraphIndex.value])
const safeHtml = computed(() => {
  const html = reader.current.value?.contentHtml ?? ''
  return DOMPurify.sanitize(
    html.replace(/\b(src|href)=(["'])\/api\//g, `$1=$2${apiBaseUrl.replace(/\/api\/?$/, '')}/api/`),
    {
      FORBID_TAGS: ['style', 'form', 'input', 'iframe', 'video', 'audio', 'object', 'embed'],
      FORBID_ATTR: ['style', 'srcset'],
      ADD_ATTR: ['id'],
    },
  )
})
const bookProgress = computed(() =>
  reader.paragraphs.value.length
    ? Math.round((reader.completed.value / reader.paragraphs.value.length) * 100)
    : 0,
)
async function goChapter(id: string) {
  contentsOpen.value = false
  stopSpeech()
  await reader.flush()
  await router.push({ path: `/app/reader/${id}`, query: { bookId } })
}
function stopSpeech() {
  if ('speechSynthesis' in window) window.speechSynthesis.cancel()
  speaking.value = false
}
function speak() {
  if (!('speechSynthesis' in window)) {
    speechError.value = '当前浏览器不支持朗读。'
    return
  }
  if (speaking.value) {
    stopSpeech()
    return
  }
  const text = currentParagraph.value?.text
  if (!text) return
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'en-US'
  utterance.rate = reader.settings.value.ttsSpeed
  utterance.onend = () => {
    speaking.value = false
  }
  utterance.onerror = () => {
    speaking.value = false
  }
  speaking.value = true
  window.speechSynthesis.speak(utterance)
}
function openSentence(text: string, id?: string, selectedText = text) {
  const index = reader.paragraphs.value.findIndex((paragraph) =>
    paragraph.sentences.some((sentence) => sentence.id === id),
  )
  if (index >= 0) reader.select(index)
  selection.value = { text: selectedText, sentence: text, id }
}
function sourceSentence(node: HTMLElement, fallback: string) {
  const id = node.dataset.sentence
  if (id) {
    for (const paragraph of reader.paragraphs.value) {
      const sentence = paragraph.sentences.find((value) => value.id === id)
      if (sentence) return sentence.text.slice(0, 2500)
    }
  }
  return (node.querySelector('[data-sense-text]')?.textContent ?? node.textContent ?? fallback)
    .trim()
    .slice(0, 2500)
}
function selected() {
  const value = window.getSelection()
  if (!value || !value.toString().trim() || !root.value?.contains(value.anchorNode)) return
  const node =
    value.anchorNode?.parentElement?.closest<HTMLElement>('[data-sentence],p,li,blockquote') ??
    root.value
  selection.value = {
    text: value.toString().trim().slice(0, 2500),
    sentence: sourceSentence(node, value.toString()),
    id: node.dataset.sentence,
  }
  value.removeAllRanges()
}
function wordAt(event: MouseEvent) {
  const target = event.target as Element
  if (target.closest('a,button')) return
  if (window.getSelection()?.toString().trim()) return
  const host = event.currentTarget as HTMLElement
  const word = getWordSelectionAtPoint(host, event.clientX, event.clientY)
  if (!word) return
  const context =
    word.node.parentElement?.closest<HTMLElement>('[data-sentence],p,li,blockquote') ?? host
  selection.value = {
    text: word.word,
    sentence: sourceSentence(context, word.word),
    id: context.dataset.sentence,
  }
}
function htmlClick(event: MouseEvent) {
  const anchor = (event.target as Element).closest('a')
  if (!anchor) {
    wordAt(event)
    return
  }
  event.preventDefault()
  const href = anchor.getAttribute('href') ?? ''
  if (href.startsWith('#')) {
    root.value
      ?.querySelector(`[id="${CSS.escape(href.slice(1))}"]`)
      ?.scrollIntoView({ block: 'center' })
    return
  }
  const chapter = reader.chapters.value.find(
    (c) => c.sourceHref && href.split('#')[0] === c.sourceHref.split('#')[0],
  )
  if (chapter) void goChapter(chapter.id)
  else if (/^https?:\/\//i.test(href)) window.open(href, '_blank', 'noopener,noreferrer')
}
async function next() {
  stopSpeech()
  reader.next()
  await nextTick()
  document.getElementById(`paragraph-${reader.paragraphIndex.value}`)?.scrollIntoView({
    block: 'center',
    behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
  })
}
async function loadQuiz(generate = false) {
  quizOpen.value = true
  quizBusy.value = true
  quizError.value = ''
  try {
    const response = generate
      ? await api.post(
          `/chapters/${chapterId}/quizzes`,
          {},
          { signal: reader.signal, timeout: 60000 },
        )
      : await api.get(`/chapters/${chapterId}/quiz`, { signal: reader.signal })
    quiz.value = response.data.summary ?? response.data.quiz ?? response.data
  } catch (e) {
    if (!reader.signal.aborted) quizError.value = errorMessage(e)
  } finally {
    quizBusy.value = false
  }
}
watch(
  () => reader.paragraphIndex.value,
  () => stopSpeech(),
)
onBeforeRouteLeave(async () => {
  stopSpeech()
  await reader.flush()
})
onScopeDispose(stopSpeech)
</script>
<template>
  <section class="reader-shell" :style="vars">
    <header class="reader-toolbar">
      <div class="actions">
        <RouterLink to="/app/library" class="button secondary small">← 书架</RouterLink
        ><button class="button secondary small" @click="contentsOpen = true">目录</button>
      </div>
      <button
        class="button secondary small assistant-toggle"
        :aria-expanded="assistantOpen"
        @click="assistantOpen = !assistantOpen"
      >
        {{ assistantOpen ? '收起助手' : '阅读助手' }}
      </button>
      <span class="reader-title">{{ reader.book.value?.title ?? '阅读空间' }}</span>
      <div class="actions">
        <button
          class="button secondary small"
          @click="display = display === 'phrase' ? 'original' : 'phrase'"
        >
          {{ display === 'phrase' ? '原文模式' : '意群模式' }}</button
        ><button class="button secondary small" @click="settingsOpen = true">Aa 排版</button
        ><button class="button secondary small" @click="loadQuiz()">章节回顾</button>
      </div>
    </header>
    <div class="reader-workspace" :class="{ 'with-assistant': assistantOpen }">
      <main
        class="reader-body"
        :style="{
          fontSize: `${reader.settings.value.fontSize}px`,
          lineHeight: reader.settings.value.lineHeight,
          maxWidth: `${reader.settings.value.contentWidth}px`,
        }"
      >
        <p v-if="reader.loading.value" class="state" role="status">正在打开书页…</p>
        <div v-else-if="reader.error.value" class="error" role="alert">
          {{ reader.error.value
          }}<button class="button secondary" @click="reader.load">重试</button>
        </div>
        <template v-else
          ><p class="eyebrow">CHAPTER {{ reader.chapterIndex.value + 1 }}</p>
          <h1>{{ reader.current.value?.stageTitle }}</h1>
          <p v-if="reader.current.value?.stageSummary" class="muted">
            {{ reader.current.value.stageSummary }}
          </p>
          <p v-if="citationError" class="error" role="alert">{{ citationError }}</p>
          <aside v-if="citation" class="panel" aria-label="引用原文">
            <strong>已定位原文来源</strong>
            <p class="muted">高亮内容是引用的原文片段，请结合上下文核对理解。</p>
            <button class="text-link" @click="citation = null">取消高亮</button>
          </aside>
          <p v-if="reader.progressError.value" class="error" role="alert">
            {{ reader.progressError.value }} <button @click="reader.flush">重试同步</button>
          </p>
          <div ref="root" @mouseup="selected" @touchend="selected">
            <div
              v-if="display === 'original' && safeHtml"
              class="reader-html"
              @click="htmlClick"
              v-html="safeHtml"
            />
            <template v-else
              ><article
                v-for="(paragraph, index) in reader.paragraphs.value"
                :id="`paragraph-${index}`"
                :key="paragraph.id"
                class="reader-paragraph"
                :class="{ current: index === reader.paragraphIndex.value }"
                @click="reader.select(index)"
              >
                <p v-if="citation?.ranges[index]" class="pre-wrap">
                  {{ paragraph.text.slice(0, citation.ranges[index]!.start)
                  }}<mark>{{
                    paragraph.text.slice(citation.ranges[index]!.start, citation.ranges[index]!.end)
                  }}</mark
                  >{{ paragraph.text.slice(citation.ranges[index]!.end) }}
                </p>
                <template v-else>
                  <p
                    v-for="sentence in paragraph.sentences"
                    :key="sentence.id"
                    :data-sentence="sentence.id"
                    @click="wordAt"
                  >
                    <SenseGroupSentence
                      v-if="display === 'phrase'"
                      :book-id="bookId"
                      :chapter-id="chapterId"
                      :sentence-id="sentence.id"
                      :text="sentence.text"
                      :saved="sentenceGroups[sentence.id]"
                      @saved="sentenceGroups[sentence.id] = $event"
                      @help="openSentence(sentence.text, sentence.id, $event)"
                    />
                    <template v-else
                      >{{ sentence.text
                      }}<button
                        class="sentence-help"
                        aria-label="理解这个句子"
                        @click.stop="openSentence(sentence.text, sentence.id)"
                      >
                        ✧
                      </button></template
                    >
                  </p>
                </template>
              </article></template
            >
          </div>
          <p v-if="!reader.paragraphs.value.length" class="state">这一章暂无可显示的文字。</p>
          <footer class="stack section-space">
            <progress :value="bookProgress" max="100" :aria-label="`本章已完成 ${bookProgress}%`" />
            <div class="row">
              <button
                class="button secondary"
                :disabled="reader.chapterIndex.value <= 0"
                @click="goChapter(reader.chapters.value[reader.chapterIndex.value - 1]!.id)"
              >
                上一章</button
              ><button
                v-if="reader.chapterIndex.value < reader.chapters.value.length - 1"
                class="button"
                @click="goChapter(reader.chapters.value[reader.chapterIndex.value + 1]!.id)"
              >
                下一章 →</button
              ><button v-else class="button" @click="loadQuiz()">完成阅读 · 回顾本章</button>
            </div>
          </footer></template
        >
      </main>
      <ReaderAssistance
        v-if="selection && assistantOpen"
        :key="`${selection.id}:${selection.text}`"
        :book-id="bookId"
        :chapter-id="chapterId"
        :paragraph-index="reader.paragraphIndex.value"
        :sentence="selection.sentence"
        :sentence-id="selection.id"
        :selected-text="selection.text"
        @close="assistantOpen = false"
      />
      <aside
        v-if="assistantOpen && !selection"
        class="reading-assistant reading-assistant-empty"
        aria-label="阅读助手"
      >
        <header class="row">
          <div>
            <p class="eyebrow">READING COMPANION</p>
            <h2>阅读助手</h2>
          </div>
          <button class="icon-button" aria-label="收起阅读助手" @click="assistantOpen = false">
            ×
          </button>
        </header>
        <div class="assistant-welcome">
          <span class="assistant-spark" aria-hidden="true">✧</span>
          <h3>从不理解的那一句开始。</h3>
          <p>选中原文、点击句子旁的 ✧，或划分后点击一个意群。原文留在左边，理解在这里展开。</p>
          <div class="assistant-capabilities">
            <span>语境查词</span><span>句子追问</span><span>全书问答</span><span>摘录笔记</span>
          </div>
        </div>
      </aside>
    </div>
    <nav
      v-if="!reader.loading.value && !reader.error.value"
      class="reader-controls"
      aria-label="段落导航"
    >
      <button
        class="icon-button"
        aria-label="上一段"
        :disabled="reader.paragraphIndex.value <= 0"
        @click="reader.select(reader.paragraphIndex.value - 1)"
      >
        ‹</button
      ><span>{{ reader.paragraphIndex.value + 1 }} / {{ reader.paragraphs.value.length }}</span
      ><button class="button secondary small" @click="speak">
        {{ speaking ? '停止' : '朗读' }}</button
      ><button class="button small" @click="next">
        {{
          reader.paragraphIndex.value >= reader.paragraphs.value.length - 1
            ? '标记读完'
            : '读下一段'
        }}
      </button>
    </nav>
    <p v-if="speechError" role="alert">{{ speechError }}</p>
    <ModalPanel v-if="contentsOpen" title="目录" @close="contentsOpen = false"
      ><nav class="stack">
        <button
          v-for="(chapter, index) in reader.chapters.value"
          :key="chapter.id"
          class="chapter-link"
          :aria-current="chapter.id === chapterId ? 'page' : undefined"
          @click="goChapter(chapter.id)"
        >
          <span>{{ String(index + 1).padStart(2, '0') }}</span
          >{{ chapter.stageTitle || `第 ${index + 1} 章` }}
        </button>
      </nav></ModalPanel
    >
    <ModalPanel v-if="settingsOpen" title="让书页适合你" @close="settingsOpen = false"
      ><div class="stack">
        <label
          >阅读主题<select
            :value="reader.settings.value.theme"
            @change="
              reader.settings.value = applyThemePreset(
                reader.settings.value,
                ($event.target as HTMLSelectElement).value as Exclude<
                  typeof reader.settings.value.theme,
                  'custom'
                >,
              )
            "
          >
            <option v-for="theme in THEME_PRESETS" :key="theme.key" :value="theme.key">
              {{ theme.label }}
            </option>
          </select></label
        ><label
          >字体<select v-model="reader.settings.value.fontFamily">
            <option v-for="font in FONT_OPTIONS" :key="font.key" :value="font.key">
              {{ font.label }}
            </option>
          </select></label
        ><label
          >字号 {{ reader.settings.value.fontSize }} px<input
            v-model.number="reader.settings.value.fontSize"
            type="range"
            min="14"
            max="36" /></label
        ><label
          >行高 {{ reader.settings.value.lineHeight
          }}<input
            v-model.number="reader.settings.value.lineHeight"
            type="range"
            min="1.3"
            max="2.5"
            step="0.05" /></label
        ><label
          >内容宽度 {{ reader.settings.value.contentWidth }} px<input
            v-model.number="reader.settings.value.contentWidth"
            type="range"
            min="440"
            max="1100"
            step="20" /></label
        ><label
          >朗读速度 {{ reader.settings.value.ttsSpeed }}×<input
            v-model.number="reader.settings.value.ttsSpeed"
            type="range"
            min="0.5"
            max="2"
            step="0.1" /></label
        ><label
          >亮度<input
            v-model.number="reader.settings.value.readingBrightness"
            type="range"
            min="0.5"
            max="1"
            step="0.05"
        /></label></div
    ></ModalPanel>
    <ModalPanel v-if="quizOpen" title="回顾这一章" @close="quizOpen = false"
      ><p v-if="quizBusy" role="status">正在准备章节回顾…</p>
      <p v-if="quizError" class="error" role="alert">{{ quizError }}</p>
      <ChapterQuiz v-if="quiz && !quizBusy" :quiz="quiz" />
      <button class="button mt-4" :disabled="quizBusy" @click="loadQuiz(true)">
        {{ quiz ? '重新生成回顾' : '生成章节回顾' }}
      </button></ModalPanel
    >
  </section>
</template>
