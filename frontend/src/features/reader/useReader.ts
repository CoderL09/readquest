import { computed, onScopeDispose, ref, watch } from 'vue'
import { api, type Book } from '../../lib/api'
import { userStorage } from '../../lib/userStorage'
import { errorMessage } from '../../lib/errors'
import type { ChapterFromAPI, StructuredChapterFromAPI, ReadingProgressFromAPI } from './core/types'
import { sanitizeReaderSettings } from './core/readerPreferences'
export function useReader(bookId: string, chapterId: string) {
  const book = ref<Book | null>(null),
    chapters = ref<ChapterFromAPI[]>([]),
    structure = ref<StructuredChapterFromAPI | null>(null),
    loading = ref(true),
    error = ref(''),
    paragraphIndex = ref(0),
    completed = ref(0),
    progressError = ref('')
  const controller = new AbortController()
  let disposed = false,
    lastTick = Date.now(),
    pendingDuration = 0
  let saveChain = Promise.resolve()
  type ReadingEvent = {
    eventId: string
    currentParagraphIndex: number
    completedParagraphs: number
    totalParagraphs: number
    readingDurationMs: number
  }
  const pending: ReadingEvent[] = []
  const current = computed(() => chapters.value.find((c) => c.id === chapterId)),
    chapterIndex = computed(() => chapters.value.findIndex((c) => c.id === chapterId))
  const paragraphs = computed(
    () =>
      structure.value?.paragraphs ??
      (current.value?.contentText || current.value?.content || '')
        .split(/\n\s*\n/)
        .filter((p) => p.trim())
        .map((text, index) => ({
          id: `p-${index}`,
          paragraphIndex: index,
          text,
          sentenceCount: 1,
          estimatedLexile: 0,
          sentences: [{ id: `local:${index}`, sentenceIndex: 0, text, chunks: [] }],
        })),
  )
  let savedSettings = {}
  try {
    savedSettings = JSON.parse(userStorage.getItem('readquest.reader.settings') ?? '{}')
  } catch {
    /* defaults */
  }
  const settings = ref(sanitizeReaderSettings(savedSettings))
  watch(
    settings,
    (value) => userStorage.setItem('readquest.reader.settings', JSON.stringify(value)),
    { deep: true },
  )
  function tick() {
    const now = Date.now()
    if (document.visibilityState === 'visible') pendingDuration += Math.min(now - lastTick, 15000)
    lastTick = now
  }
  function flush() {
    const operation = saveChain.then(persist)
    saveChain = operation.catch(() => {})
    return operation
  }
  async function persist() {
    if (disposed || !paragraphs.value.length) return
    tick()
    if (pendingDuration > 0 || !pending.length) {
      pending.push({
        eventId: crypto.randomUUID(),
        currentParagraphIndex: paragraphIndex.value,
        completedParagraphs: completed.value,
        totalParagraphs: paragraphs.value.length,
        readingDurationMs: Math.floor(pendingDuration),
      })
      pendingDuration = 0
    }
    try {
      while (pending.length && !disposed) {
        await api.post(`/books/${bookId}/chapters/${chapterId}/reading-events`, pending[0], {
          signal: controller.signal,
        })
        pending.shift()
      }
      progressError.value = ''
    } catch (e) {
      if (!disposed) progressError.value = errorMessage(e, '进度尚未同步，请重试。')
    }
  }
  function select(index: number, markComplete = false) {
    tick()
    paragraphIndex.value = Math.max(0, Math.min(index, paragraphs.value.length - 1))
    if (markComplete)
      completed.value = Math.max(completed.value, Math.min(index, paragraphs.value.length))
    userStorage.setItem(`readquest.reader.paragraph.${chapterId}`, String(paragraphIndex.value))
    void flush()
  }
  function next() {
    const next = paragraphIndex.value + 1
    completed.value = Math.max(completed.value, next)
    select(next, true)
  }
  async function load() {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<{ book: Book; chapters: ChapterFromAPI[] }>(
        `/books/${bookId}/chapters`,
        { signal: controller.signal },
      )
      if (controller.signal.aborted) return
      book.value = data.book
      chapters.value = data.chapters
      if (!current.value) throw new Error('没有找到这一章，请从书架重新打开。')
      userStorage.setItem('readquest.bookId', bookId)
      userStorage.setItem('readquest.stageId', chapterId)
      const [structured, progress] = await Promise.allSettled([
        api.get<StructuredChapterFromAPI>(`/stages/${chapterId}/structure`, {
          signal: controller.signal,
        }),
        api.get<{ progress: ReadingProgressFromAPI | null }>('/reading-progress', {
          params: { bookId, chapterId },
          signal: controller.signal,
        }),
      ])
      if (controller.signal.aborted) return
      if (structured.status === 'fulfilled') structure.value = structured.value.data
      const restored = progress.status === 'fulfilled' ? progress.value.data.progress : null
      paragraphIndex.value = Math.max(
        0,
        Math.min(
          restored?.currentParagraphIndex ??
            Number(userStorage.getItem(`readquest.reader.paragraph.${chapterId}`) ?? 0),
          paragraphs.value.length - 1,
        ),
      )
      completed.value = restored?.completedParagraphs ?? 0
      lastTick = Date.now()
    } catch (e) {
      if (!disposed) error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  const timer = setInterval(() => {
    if (!loading.value && !error.value) void flush()
  }, 15000)
  function visibility() {
    tick()
    if (document.visibilityState === 'hidden') void flush()
  }
  document.addEventListener('visibilitychange', visibility)
  onScopeDispose(() => {
    disposed = true
    clearInterval(timer)
    document.removeEventListener('visibilitychange', visibility)
    controller.abort()
  })
  void load()
  return {
    book,
    chapters,
    current,
    chapterIndex,
    paragraphs,
    paragraphIndex,
    completed,
    structure,
    settings,
    loading,
    error,
    progressError,
    load,
    select,
    next,
    flush,
    signal: controller.signal,
  }
}
