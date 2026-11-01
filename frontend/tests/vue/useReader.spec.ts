import { effectScope } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import { api } from '../../src/lib/api'
import { useReader } from '../../src/features/reader/useReader'
vi.mock('../../src/lib/api', () => ({ api: { get: vi.fn(), post: vi.fn() } }))
vi.mock('../../src/lib/userStorage', () => ({
  userStorage: { getItem: () => null, setItem: vi.fn() },
}))
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.get).mockImplementation(async (url) => ({
    data: url.includes('/chapters')
      ? {
          book: { id: 'book', title: 'Test' },
          chapters: [{ id: 'chapter', contentText: 'First paragraph.\n\nSecond paragraph.' }],
        }
      : url.includes('structure')
        ? {
            paragraphs: [
              { id: 'p1', text: 'First paragraph.', sentences: [] },
              { id: 'p2', text: 'Second paragraph.', sentences: [] },
            ],
          }
        : { progress: { currentParagraphIndex: 1, completedParagraphs: 1 } },
  }))
  vi.mocked(api.post).mockResolvedValue({ data: {} })
})
describe('reading state', () => {
  it('restores server progress and retries the same reading event after a network failure', async () => {
    const scope = effectScope()
    const reader = scope.run(() => useReader('book', 'chapter'))!
    await flushPromises()
    expect(reader.paragraphIndex.value).toBe(1)
    vi.mocked(api.post).mockRejectedValueOnce(new Error('offline'))
    await reader.flush()
    expect(reader.progressError.value).toBe('offline')
    const first = vi.mocked(api.post).mock.calls[0]![1]
    await reader.flush()
    expect(vi.mocked(api.post).mock.calls[1]![1]).toEqual(first)
    expect(reader.progressError.value).toBe('')
    scope.stop()
  })
  it('aborts private API requests when the reader is disposed', async () => {
    const scope = effectScope()
    const reader = scope.run(() => useReader('book', 'chapter'))!
    await flushPromises()
    scope.stop()
    expect(reader.signal.aborted).toBe(true)
  })
})
