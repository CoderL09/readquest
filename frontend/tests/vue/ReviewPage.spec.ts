import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import ReviewPage from '../../src/pages/ReviewPage.vue'
import { api } from '../../src/lib/api'
vi.mock('../../src/lib/api', () => ({ api: { get: vi.fn(), post: vi.fn() } }))
const card = {
  id: 'card-1',
  kind: 'wordMeaning',
  originalSentence: 'The river was still.',
  bookId: 'book',
  chapterId: 'chapter',
  dueAt: '2026-11-01T12:00:00Z',
  targetText: 'still',
  highlightRanges: [],
  legacyHighlightFallback: false,
}
const session = {
  id: 'session-1',
  userId: 'reader',
  groupSize: 5,
  currentIndex: 0,
  total: 1,
  state: 'active',
  currentStage: 'initialAnswer',
  card,
}
beforeEach(() => vi.clearAllMocks())
describe('review session', () => {
  it('restores an active card and retains the idempotency key when a request fails', async () => {
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/review/summary'
          ? { dueCount: 1, nextDueAt: null, activeSession: { id: 'session-1' } }
          : { session },
    }))
    vi.mocked(api.post)
      .mockRejectedValueOnce(new Error('network lost'))
      .mockResolvedValueOnce({
        data: {
          gradingState: 'completed',
          retryable: false,
          currentStage: 'feedback',
          ratingApplied: true,
          grade: { verdict: 'correct', score: 1, feedbackZh: '理解正确' },
        },
      })
    const wrapper = mount(ReviewPage, { global: { stubs: { RouterLink: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('The river was still.')
    expect(wrapper.find('a[href*="reviewCard=card-1"]').exists()).toBe(true)
    await wrapper.get('textarea').setValue('平静的')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('network lost')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const calls = vi.mocked(api.post).mock.calls
    expect(calls).toHaveLength(2)
    expect(calls[0]![1]).toEqual(calls[1]![1])
    expect(wrapper.text()).toContain('理解正确')
    expect(wrapper.find('textarea').exists()).toBe(false)
    wrapper.unmount()
  })
  it('shows completion when there is no due or active card', async () => {
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/review/summary'
          ? { dueCount: 0, nextDueAt: null, activeSession: null }
          : { session: null },
    }))
    const wrapper = mount(ReviewPage, { global: { stubs: { RouterLink: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('今天的复习已完成')
    expect(wrapper.find('textarea').exists()).toBe(false)
    wrapper.unmount()
  })
})
