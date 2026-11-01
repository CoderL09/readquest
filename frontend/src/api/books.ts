import { api, type Book } from '../lib/api'
export type Chapter = {
  id: string
  chapterIndex: number
  stageTitle: string
  stageSummary: string
  content: string
  contentText?: string
  contentHtml?: string
  sourceHref?: string
}
export type TocItem = {
  label?: string
  title?: string
  stageId?: string
  href?: string
  children?: TocItem[]
}
export type BookContents = { book: Book; chapters: Chapter[]; toc?: TocItem[] }
export async function catalog(signal?: AbortSignal) {
  return (await api.get<Book[]>('/books', { signal })).data
}
export async function contents(bookId: string, signal?: AbortSignal) {
  return (await api.get<BookContents>(`/books/${encodeURIComponent(bookId)}/chapters`, { signal }))
    .data
}
export async function saveBook(bookId: string) {
  await api.post(`/library/${encodeURIComponent(bookId)}`)
}
export async function removeBook(bookId: string) {
  await api.delete(`/books/${encodeURIComponent(bookId)}`)
}
