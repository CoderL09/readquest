import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import ReaderAssistance from '../../src/features/reader/ReaderAssistance.vue'
import { api } from '../../src/lib/api'
import { createBookNote } from '../../src/features/notes/noteApi'
import { streamReaderConversation } from '../../src/features/reader/core/readerConversationStream'
vi.mock('../../src/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn() },
  mediaUrl: (url: string) => url,
}))
vi.mock('../../src/features/notes/noteApi', () => ({ createBookNote: vi.fn() }))
vi.mock('../../src/features/reader/core/readerConversationStream', () => ({
  streamReaderConversation: vi.fn(),
}))
const props = {
  bookId: 'book',
  chapterId: 'chapter',
  paragraphIndex: 1,
  sentence: 'The river was still.',
  sentenceId: 'chapter:p1:s0',
  selectedText: 'still',
}
const mountHelp = () =>
  mount(ReaderAssistance, {
    props,
    global: {
      plugins: [createPinia()],
      stubs: { ModalPanel: { template: '<section><slot/></section>' } },
    },
  })
beforeEach(() => vi.clearAllMocks())
describe('reading assistance', () => {
  it('keeps assistance in a labelled non-modal panel and can close with Escape', async () => {
    const wrapper = mountHelp()
    expect(wrapper.get('aside[aria-label="阅读助手"]').exists()).toBe(true)
    expect(wrapper.find('[aria-modal="true"]').exists()).toBe(false)
    await wrapper.get('input, button').trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
  it('allows a manual concept card without a model request and saves only after confirmation', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { dueAt: '2026-11-01T12:00:00Z' } })
    const wrapper = mountHelp()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '制作句子理解卡')!
      .trigger('click')
    expect(api.post).not.toHaveBeenCalled()
    expect(streamReaderConversation).not.toHaveBeenCalled()
    await wrapper.findAll('textarea')[0]!.setValue('still 表示水面平静。')
    await wrapper.findAll('textarea')[1]!.setValue('结合 river 理解为平静')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.post).toHaveBeenCalledWith(
      '/review-cards',
      expect.objectContaining({
        bookId: 'book',
        chapterId: 'chapter',
        sourceSentenceKey: 'chapter:p1:s0',
        originalSentence: props.sentence,
        requiredPointsZh: ['结合 river 理解为平静'],
      }),
      expect.anything(),
    )
    expect(wrapper.text()).toContain('已加入 FSRS 复习')
    wrapper.unmount()
  })
  it('previews conversation concepts without saving and sends only dialogue text on follow-up', async () => {
    const draft = {
      bookId: 'book',
      chapterId: 'chapter',
      sourceSentenceKey: 'chapter:p1:s0',
      originalSentence: props.sentence,
      category: 'meaning',
      reverseQuestionZh: 'still 表示什么？',
      referenceExplanationZh: '平静的',
      requiredPointsZh: ['平静的'],
    }
    vi.mocked(streamReaderConversation).mockResolvedValue({
      answer: '平静的',
      metrics: { firstTokenMs: 1, totalMs: 1 },
      reviewConcepts: { status: 'suggested', retryable: false, concepts: [draft] },
    })
    const wrapper = mountHelp()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '追问句子')!
      .trigger('click')
    await wrapper.get('input').setValue('still 表示什么？')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.post).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('确认加入复习')
    await wrapper.findAll('input')[1]!.setValue('为什么？')
    await wrapper.findAll('form')[1]!.trigger('submit')
    await flushPromises()
    const history = vi.mocked(streamReaderConversation).mock.calls[1]![0].history
    expect(
      history.every((turn) =>
        Object.keys(turn).every((key) => key === 'role' || key === 'content'),
      ),
    ).toBe(true)
    expect(api.post).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('keeps the numbered sources with each answer and opens their exact chapter location', async () => {
    const source = {
      citation: 1,
      bookId: 'book',
      stageId: 'other',
      stageTitle: 'Chapter Two',
      content: 'A quiet river.',
      startOffset: 10,
      endOffset: 24,
      chapterHash: 'a'.repeat(64),
    }
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { answer: 'It was quiet. [1]', sources: [source] } })
      .mockResolvedValueOnce({ data: { answer: 'Not supported by the excerpts.', sources: [] } })
    const wrapper = mountHelp()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '全书问答')!
      .trigger('click')
    await wrapper.get('input').setValue('What was the river like?')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('[1] Chapter Two')
    const link = wrapper.get('a')
    expect(link.attributes('href')).toContain(
      '/app/reader/other?bookId=book&sourceStart=10&sourceEnd=24',
    )
    expect(link.attributes('target')).toBe('_blank')
    await wrapper.get('input').setValue('What happened next?')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('a').attributes('href')).toBe(link.attributes('href'))
    expect(wrapper.text()).toContain('Not supported by the excerpts.')
    wrapper.unmount()
  })
  it('opens locally without sending text and saves an explicitly requested note', async () => {
    vi.mocked(createBookNote).mockResolvedValue({} as Awaited<ReturnType<typeof createBookNote>>)
    const wrapper = mountHelp()
    expect(api.get).not.toHaveBeenCalled()
    expect(api.post).not.toHaveBeenCalled()
    expect(streamReaderConversation).not.toHaveBeenCalled()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '摘录笔记')!
      .trigger('click')
    await wrapper.get('textarea').setValue('A quiet moment.')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(createBookNote).toHaveBeenCalledWith({
      bookId: 'book',
      chapterId: 'chapter',
      paragraphIndex: 1,
      quote: 'still',
      note: 'A quiet moment.',
    })
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
  it('renders streamed answers and cancels the conversation on unmount', async () => {
    let signal: AbortSignal | undefined
    vi.mocked(streamReaderConversation).mockImplementation(async (input) => {
      signal = input.signal
      input.onToken('It describes calm water.')
      return await new Promise(() => {})
    })
    const wrapper = mountHelp()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '追问句子')!
      .trigger('click')
    await wrapper.get('input').setValue('What does still mean?')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('It describes calm water.')
    expect(signal?.aborted).toBe(false)
    wrapper.unmount()
    expect(signal?.aborted).toBe(true)
  })
})
