import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../src/lib/api'
import { fetchAllVocabulary } from '../../src/features/vocabulary/vocabularyApi'
import { fetchAllBookNotes } from '../../src/features/notes/noteApi'
vi.mock('../../src/lib/api', () => ({ api: { get: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
describe('collection requests against the REST pagination contract', () => {
  for (const [name, load] of [
    ['vocabulary', fetchAllVocabulary],
    ['notes', fetchAllBookNotes],
  ] as const) {
    it(`${name} respects the server page-size limit and carries cancellation`, async () => {
      vi.mocked(api.get).mockResolvedValue({
        data: { items: [], page: 1, total: 0, hasMore: false },
      })
      const controller = new AbortController()
      await load(controller.signal)
      expect(api.get).toHaveBeenCalledWith(expect.any(String), {
        params: { page: 1, pageSize: 100 },
        signal: controller.signal,
      })
    })
  }
})
