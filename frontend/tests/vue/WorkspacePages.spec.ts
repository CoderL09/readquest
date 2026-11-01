import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HomePage from '../../src/pages/HomePage.vue'
import VocabularyPage from '../../src/pages/VocabularyPage.vue'
import { api } from '../../src/lib/api'
import { catalog, contents } from '../../src/api/books'
import { fetchAllVocabulary } from '../../src/features/vocabulary/vocabularyApi'
import type { VocabularyItem } from '../../src/features/vocabulary/vocabularyState'
const push = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('../../src/lib/api', () => ({
  api: { get: vi.fn(), delete: vi.fn() },
  mediaUrl: (x: string) => x,
}))
vi.mock('../../src/api/books', () => ({ catalog: vi.fn(), contents: vi.fn(), saveBook: vi.fn() }))
vi.mock('../../src/lib/userStorage', () => ({ userStorage: { setItem: vi.fn() } }))
vi.mock('../../src/stores/notices', () => ({ useNoticeStore: () => ({ notify: vi.fn() }) }))
vi.mock('../../src/features/vocabulary/vocabularyApi', () => ({ fetchAllVocabulary: vi.fn() }))
const global = {
  stubs: { RouterLink: { template: '<a><slot /></a>' }, ReadingRecommendations: true },
}
const book = {
  id: 'book',
  title: 'A real uploaded book',
  author: 'Author',
  coverUrl: '/signed-cover',
  status: 'completed' as const,
  progress: 100,
  wordCount: 30,
  totalChapters: 2,
}
let activeBook: object | null
beforeEach(() => {
  vi.clearAllMocks()
  activeBook = { ...book, progress: 42, coverUrl: '' }
  vi.mocked(catalog).mockResolvedValue([book])
  vi.mocked(contents).mockResolvedValue({
    book,
    chapters: [{ id: 'first' }, { id: 'last-read' }],
  } as never)
  vi.mocked(api.get).mockImplementation(
    async (url) =>
      ({
        data:
          url === '/growth/summary'
            ? {
                lookup: { total: 7 },
                reading: {
                  activeBook,
                  totalReadingMinutes: 23,
                  recentProgress: [{ bookId: 'book', chapterId: 'last-read' }],
                },
              }
            : { dueCount: 8, activeSession: null },
      }) as never,
  )
})
describe('redesigned reading workspace', () => {
  it('uses the signed catalog cover and resumes the actual last-read chapter', async () => {
    const wrapper = mount(HomePage, { global })
    await flushPromises()
    expect(wrapper.get('.feature-cover img').attributes('src')).toBe('/signed-cover')
    expect(wrapper.get('.feature-progress').text()).toContain('42%')
    expect(wrapper.find('canvas').exists()).toBe(false)
    await wrapper.get('.feature-copy button').trigger('click')
    await flushPromises()
    expect(push).toHaveBeenCalledWith({ path: '/app/reader/last-read', query: { bookId: 'book' } })
    wrapper.unmount()
  })
  it('does not display import completion as reading progress', async () => {
    activeBook = null
    const wrapper = mount(HomePage, { global })
    await flushPromises()
    expect(wrapper.find('.feature-progress').exists()).toBe(false)
    expect(wrapper.get('.feature-copy button').text()).toContain('开始阅读')
    wrapper.unmount()
  })
  it('guides a new user to add a book instead of showing a decorative empty canvas', async () => {
    activeBook = null
    vi.mocked(catalog).mockResolvedValue([])
    const wrapper = mount(HomePage, { global })
    await flushPromises()
    expect(wrapper.get('.feature-empty').text()).toContain('添加第一本书')
    expect(wrapper.find('.feature-book').exists()).toBe(false)
    wrapper.unmount()
  })
  it('keeps the vocabulary detail synchronized when search excludes the selected word', async () => {
    const item = (id: string, text: string) =>
      ({
        id,
        text,
        normalizedText: text,
        originalSentence: `A ${text} sentence.`,
        explanation: `${text} meaning`,
        translation: '',
        type: 'word',
        book: { id: 'book', title: 'Source', author: 'Author' },
        reviewStatus: 'new',
        masteryLevel: 0,
        lookupTime: '2026-11-02T00:00:00Z',
        mastery: {
          source: 'unobserved',
          score: null,
          recallProbability: null,
          answerQuality: null,
          confidence: 'none',
          evidenceCount: 0,
          lapseCount: 0,
          due: false,
          dueAt: null,
          lastReviewedAt: null,
          stabilityDays: null,
          difficulty: null,
        },
      }) as VocabularyItem
    vi.mocked(fetchAllVocabulary).mockResolvedValue([item('one', 'curious'), item('two', 'hidden')])
    const wrapper = mount(VocabularyPage, { global })
    await flushPromises()
    expect(wrapper.get('.word-detail h2').text()).toBe('curious')
    await wrapper.get('input[type="search"]').setValue('hidden')
    expect(wrapper.get('.word-detail h2').text()).toBe('hidden')
    await wrapper.get('input[type="search"]').setValue('unmatched')
    expect(wrapper.find('.word-detail').exists()).toBe(false)
    wrapper.unmount()
  })
})
