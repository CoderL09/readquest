import type { BookNote } from './noteState'

export type BookNotePage = {
  items: BookNote[]
  page: number
  pageSize: number
  total: number
  hasMore: boolean
}

export type CreateBookNoteInput = {
  bookId: string
  chapterId?: string | null
  paragraphIndex?: number | null
  quote: string
  note?: string
}

export async function collectBookNotePages(
  fetchPage: (page: number) => Promise<BookNotePage>,
  onProgress?: (loaded: number, total: number) => void,
): Promise<BookNote[]> {
  const items: BookNote[] = []
  let page = 1

  while (true) {
    const response = await fetchPage(page)
    if (response.page !== page) throw new Error('Book-note pagination returned an unexpected page')
    items.push(...response.items)
    onProgress?.(items.length, response.total)
    if (!response.hasMore) return items
    if (response.items.length === 0) throw new Error('Book-note pagination did not make progress')
    page += 1
  }
}

export function fetchAllBookNotes(
  signal: AbortSignal,
  onProgress?: (loaded: number, total: number) => void,
): Promise<BookNote[]> {
  return collectBookNotePages(async (page) => {
    const { api } = await import('../../lib/api')
    const { data } = await api.get<BookNotePage>('/book-notes', {
      params: { page, pageSize: 100 },
      signal,
    })
    return data
  }, onProgress)
}

export async function createBookNote(input: CreateBookNoteInput): Promise<BookNote> {
  const { api } = await import('../../lib/api')
  const { data } = await api.post<{ item: BookNote }>('/book-notes', input)
  return data.item
}

export async function updateBookNote(id: string, note: string): Promise<BookNote> {
  const { api } = await import('../../lib/api')
  const { data } = await api.patch<{ item: BookNote }>(`/book-notes/${id}`, { note })
  return data.item
}

export async function deleteBookNote(id: string): Promise<void> {
  const { api } = await import('../../lib/api')
  await api.delete(`/book-notes/${id}`)
}
