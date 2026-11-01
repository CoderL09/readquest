import { mount, flushPromises } from '@vue/test-utils'
import { createHash, webcrypto } from 'node:crypto'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReaderPage from '../../src/pages/ReaderPage.vue'
import { useReader } from '../../src/features/reader/useReader'
import { api } from '../../src/lib/api'

const route = vi.hoisted(() => ({
  params: { stageId: 'chapter' },
  query: {} as Record<string, string>,
}))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
}))
vi.mock('../../src/features/reader/useReader', () => ({ useReader: vi.fn() }))
vi.mock('../../src/lib/api', () => ({ api: { get: vi.fn(), post: vi.fn() }, apiBaseUrl: '/api' }))
vi.mock('../../src/lib/userStorage', () => ({ userStorage: { getItem: () => null } }))

const text = 'Same sentence.\n\nSame sentence.'
let select: ReturnType<typeof vi.fn>
beforeEach(() => {
  Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
  Element.prototype.scrollIntoView = vi.fn()
  route.query = {
    bookId: 'book',
    sourceStart: '16',
    sourceEnd: String(text.length),
    sourceHash: createHash('sha256').update(text).digest('hex'),
  }
  select = vi.fn()
  vi.mocked(useReader).mockReturnValue({
    book: ref({ title: 'Test' }),
    current: ref({ stageTitle: 'One', contentText: text }),
    paragraphs: ref(
      text.split('\n\n').map((text, index) => ({ id: String(index), text, sentences: [] })),
    ),
    chapters: ref([{ id: 'chapter' }]),
    chapterIndex: ref(0),
    paragraphIndex: ref(0),
    completed: ref(0),
    loading: ref(false),
    error: ref(''),
    progressError: ref(''),
    settings: ref({ fontSize: 20, lineHeight: 1.8, contentWidth: 800 }),
    select,
    flush: vi.fn(),
    signal: new AbortController().signal,
  } as unknown as ReturnType<typeof useReader>)
})
const mountPage = () =>
  mount(ReaderPage, {
    attachTo: document.body,
    global: { stubs: { RouterLink: { template: '<a><slot/></a>' } } },
  })

describe('reader citation navigation', () => {
  it('uses canonical source text when selection includes sentence controls', async () => {
    route.query = { bookId: 'book' }
    const implementation = vi.mocked(useReader).getMockImplementation()!
    const reader = implementation('book', 'chapter')
    reader.paragraphs.value[0]!.sentences = [
      { id: 'chapter:p0:s0', text: 'Same sentence.', sentenceIndex: 0, chunks: [] },
    ]
    vi.mocked(useReader).mockReturnValue(reader)
    const wrapper = mount(ReaderPage, {
      attachTo: document.body,
      global: {
        stubs: {
          RouterLink: { template: '<a><slot/></a>' },
          ReaderAssistance: {
            props: ['sentence'],
            template: '<aside data-context>{{ sentence }}</aside>',
          },
        },
      },
    })
    const sentence = wrapper.get('[data-sentence]')
    const textNode = sentence.get('[data-sense-text]').element.firstChild!
    const range = document.createRange()
    range.setStart(textNode, 5)
    range.setEnd(textNode, 13)
    window.getSelection()!.removeAllRanges()
    window.getSelection()!.addRange(range)
    await sentence.trigger('mouseup')
    expect(wrapper.get('[data-context]').text()).toBe('Same sentence.')
    expect(wrapper.get('[data-context]').text()).not.toContain('划分意群')
    wrapper.unmount()
  })
  it('resolves a review card source through the authorized endpoint', async () => {
    route.query = { bookId: 'book', reviewCard: 'card-1' }
    vi.mocked(api.get).mockResolvedValue({
      data: {
        bookId: 'book',
        stageId: 'chapter',
        startOffset: 16,
        endOffset: text.length,
        chapterHash: createHash('sha256').update(text).digest('hex'),
      },
    })
    const wrapper = mountPage()
    await vi.waitFor(() => expect(wrapper.get('#paragraph-1 mark').text()).toBe('Same sentence.'))
    expect(api.get).toHaveBeenCalledWith('/review-cards/card-1/source', expect.anything())
    wrapper.unmount()
  })
  it('highlights the exact occurrence without marking paragraphs completed', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.get('#paragraph-1 mark').text()).toBe('Same sentence.'))
    expect(wrapper.find('#paragraph-0 mark').exists()).toBe(false)
    expect(select).toHaveBeenCalledWith(1)
    expect(Element.prototype.scrollIntoView).toHaveBeenCalled()
    wrapper.unmount()
  })
  it('shows stale citations instead of highlighting a different text', async () => {
    route.query.sourceHash = '0'.repeat(64)
    const wrapper = mountPage()
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.get('[role="alert"]').text()).toContain('章节内容已变化'))
    expect(wrapper.find('mark').exists()).toBe(false)
    expect(select).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
