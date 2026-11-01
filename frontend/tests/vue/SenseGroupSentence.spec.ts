import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SenseGroupSentence from '../../src/features/reader/SenseGroupSentence.vue'
import { api } from '../../src/lib/api'

vi.mock('../../src/lib/api', () => ({ api: { post: vi.fn() } }))
const text = 'We can read and/or listen.'
const groups = [
  { text: 'We can read', start: 0, end: 11 },
  { text: 'and/or listen.', start: 12, end: 26 },
]
const props = { bookId: 'book', chapterId: 'chapter', sentenceId: 'chapter:p0:s0', text }
const data = { status: 'ready', source: 'model', sentences: [{ text, chunks: groups }] }
beforeEach(() => vi.clearAllMocks())

describe('sentence sense groups', () => {
  it('analyzes only on request, preserves original text and supplies full context for help', async () => {
    vi.mocked(api.post).mockResolvedValue({ data })
    const wrapper = mount(SenseGroupSentence, { props })
    expect(api.post).not.toHaveBeenCalled()
    await wrapper.findAll('button')[0]!.trigger('click')
    await flushPromises()
    expect(api.post).toHaveBeenCalledWith(
      '/books/book/chapters/chapter/sentence-chunks',
      { sentenceId: 'chapter:p0:s0' },
      expect.objectContaining({ timeout: 60000 }),
    )
    expect(wrapper.get('[data-sense-text]').text()).toBe(text)
    expect(wrapper.findAll('.sense-group')).toHaveLength(2)
    await wrapper.findAll('.sense-group')[1]!.trigger('click')
    expect(wrapper.emitted('help')![0]).toEqual(['and/or listen.'])
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '显示原句')!
      .trigger('click')
    expect(wrapper.findAll('.sense-group')).toHaveLength(0)
    expect(wrapper.get('[data-sense-text]').text()).toBe(text)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '显示意群')!
      .trigger('click')
    expect(api.post).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('keeps original text after a bad response and allows retry', async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { ...data, sentences: [{ text, chunks: [groups[0]] }] } })
      .mockResolvedValueOnce({ data })
    const wrapper = mount(SenseGroupSentence, { props })
    await wrapper.findAll('button')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.sense-group')).toHaveLength(0)
    expect(wrapper.get('[role="status"]').text()).toContain('遗漏')
    expect(wrapper.get('[data-sense-text]').text()).toBe(text)
    await wrapper.findAll('button')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.sense-group')).toHaveLength(2)
    wrapper.unmount()
  })
  it('aborts in-flight analysis on disposal', async () => {
    let signal: AbortSignal | undefined
    vi.mocked(api.post).mockImplementation(async (_url, _body, config) => {
      signal = config?.signal as AbortSignal
      return await new Promise(() => {})
    })
    const wrapper = mount(SenseGroupSentence, { props })
    await wrapper.findAll('button')[0]!.trigger('click')
    expect(signal?.aborted).toBe(false)
    wrapper.unmount()
    expect(signal?.aborted).toBe(true)
  })
})
