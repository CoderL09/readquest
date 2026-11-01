import type { VocabularyItem } from './vocabularyState'

export type VocabularyPage = {
  items: VocabularyItem[]
  page: number
  pageSize: number
  total: number
  hasMore: boolean
}

export type VocabularyPageFetcher = (page: number) => Promise<VocabularyPage>

export async function collectVocabularyPages(
  fetchPage: VocabularyPageFetcher,
  onProgress?: (loaded: number, total: number) => void,
): Promise<VocabularyItem[]> {
  const items: VocabularyItem[] = []
  let page = 1

  while (true) {
    const response = await fetchPage(page)
    if (response.page !== page) throw new Error('Vocabulary pagination returned an unexpected page')

    items.push(...response.items)
    onProgress?.(items.length, response.total)

    if (!response.hasMore) return items
    if (response.items.length === 0) throw new Error('Vocabulary pagination did not make progress')
    page += 1
  }
}

export function fetchAllVocabulary(
  signal: AbortSignal,
  onProgress?: (loaded: number, total: number) => void,
): Promise<VocabularyItem[]> {
  return collectVocabularyPages(async (page) => {
    const { api } = await import('../../lib/api')
    const { data } = await api.get<VocabularyPage>('/vocabulary', {
      params: { page, pageSize: 100 },
      signal,
    })
    return data
  }, onProgress)
}
