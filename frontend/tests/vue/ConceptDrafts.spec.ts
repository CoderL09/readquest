import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ConceptDrafts from '../../src/features/review/ConceptDrafts.vue'
import { api } from '../../src/lib/api'

vi.mock('../../src/lib/api', () => ({ api: { post: vi.fn() } }))
const draft = {
  bookId: 'book',
  chapterId: 'chapter',
  sourceSentenceKey: 'chapter:p0:s0',
  originalSentence: 'We sat by the river.',
  category: 'past-tense',
  reverseQuestionZh: 'sat 表示什么？',
  referenceExplanationZh: '这里表示坐着。',
  requiredPointsZh: ['sit 的过去式'],
  targetFragments: [{ text: 'sat' }],
}
beforeEach(() => vi.clearAllMocks())
describe('review card confirmation', () => {
  it('does not save a suggestion until confirmation and preserves edits', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { dueAt: '2026-11-01T12:00:00Z' } })
    const wrapper = mount(ConceptDrafts, { props: { drafts: [draft] } })
    expect(api.post).not.toHaveBeenCalled()
    await wrapper.get('input').setValue('这里为什么用 sat？')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.post).toHaveBeenCalledWith(
      '/review-cards',
      expect.objectContaining({
        reverseQuestionZh: '这里为什么用 sat？',
        originalSentence: draft.originalSentence,
        sourceSentenceKey: 'chapter:p0:s0',
        requiredPointsZh: ['sit 的过去式'],
      }),
      expect.anything(),
    )
    expect(wrapper.text()).toContain('已加入 FSRS 复习')
    expect(wrapper.text()).toContain('下次复习')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })
  it('dismisses a suggestion without saving or requesting AI', async () => {
    const wrapper = mount(ConceptDrafts, { props: { drafts: [draft] } })
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '暂不加入')!
      .trigger('click')
    expect(api.post).not.toHaveBeenCalled()
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })
  it('retains a failed confirmation for retry with the same payload', async () => {
    vi.mocked(api.post)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ data: {} })
    const wrapper = mount(ConceptDrafts, { props: { drafts: [draft] } })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('offline')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(api.post).mock.calls[0]![1]).toEqual(vi.mocked(api.post).mock.calls[1]![1])
    wrapper.unmount()
  })
})
