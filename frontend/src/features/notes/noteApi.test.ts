import assert from 'node:assert/strict'
import test from 'node:test'
import { collectBookNotePages, type BookNotePage } from './noteApi'
import type { BookNote } from './noteState'

function note(id: string): BookNote {
  return {
    id,
    quote: id,
    note: '',
    book: { id: 'book', title: 'Book', author: 'Author' },
    chapter: { id: null, title: 'Book note' },
    paragraphIndex: null,
    createdAt: '2026-07-15T00:00:00.000Z',
    updatedAt: '2026-07-15T00:00:00.000Z',
  }
}

test('book-note pagination collects every server page in order', async () => {
  const pages: BookNotePage[] = [
    { items: [note('one'), note('two')], page: 1, pageSize: 2, total: 3, hasMore: true },
    { items: [note('three')], page: 2, pageSize: 2, total: 3, hasMore: false },
  ]
  const requested: number[] = []
  const result = await collectBookNotePages(async (page) => {
    requested.push(page)
    return pages[page - 1]!
  })

  assert.deepEqual(requested, [1, 2])
  assert.deepEqual(
    result.map((item) => item.id),
    ['one', 'two', 'three'],
  )
})

test('book-note pagination rejects a continuation page with no progress', async () => {
  await assert.rejects(() =>
    collectBookNotePages(async () => ({
      items: [],
      page: 1,
      pageSize: 100,
      total: 1,
      hasMore: true,
    })),
  )
})
