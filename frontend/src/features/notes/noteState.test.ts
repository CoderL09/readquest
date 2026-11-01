import assert from 'node:assert/strict'
import test from 'node:test'
import { filterBookNotes, groupBookNotes, toBookNotesMarkdown, type BookNote } from './noteState'

const notes: BookNote[] = [
  {
    id: 'note-1',
    quote: 'It was a bright cold day in April.',
    note: 'A sharp opening contrast.',
    book: { id: 'book-a', title: 'Nineteen Eighty-Four', author: 'George Orwell' },
    chapter: { id: 'chapter-a', title: 'Chapter 1' },
    paragraphIndex: 0,
    createdAt: '2026-07-15T08:00:00.000Z',
    updatedAt: '2026-07-15T08:00:00.000Z',
  },
  {
    id: 'note-2',
    quote: 'All animals are equal.',
    note: 'Watch how this principle changes.',
    book: { id: 'book-b', title: 'Animal Farm', author: 'George Orwell' },
    chapter: { id: 'chapter-b', title: 'Chapter 10' },
    paragraphIndex: 4,
    createdAt: '2026-07-14T08:00:00.000Z',
    updatedAt: '2026-07-14T08:00:00.000Z',
  },
  {
    id: 'note-3',
    quote: 'Big Brother is watching you.',
    note: '',
    book: { id: 'book-a', title: 'Nineteen Eighty-Four', author: 'George Orwell' },
    chapter: { id: 'chapter-a', title: 'Chapter 1' },
    paragraphIndex: 2,
    createdAt: '2026-07-13T08:00:00.000Z',
    updatedAt: '2026-07-13T08:00:00.000Z',
  },
]

test('book-note filtering searches quote, note, chapter, and source metadata', () => {
  assert.deepEqual(
    filterBookNotes(notes, { query: 'contrast', bookId: 'all' }).map((item) => item.id),
    ['note-1'],
  )
  assert.deepEqual(
    filterBookNotes(notes, { query: 'animal farm', bookId: 'book-b' }).map((item) => item.id),
    ['note-2'],
  )
  assert.deepEqual(
    filterBookNotes(notes, { query: 'chapter 1', bookId: 'book-a' }).map((item) => item.id),
    ['note-1', 'note-3'],
  )
})

test('book-note grouping keeps books and chapters ordered by newest note', () => {
  const groups = groupBookNotes(notes)
  assert.deepEqual(
    groups.map((group) => [group.book.title, group.notes.length]),
    [
      ['Nineteen Eighty-Four', 2],
      ['Animal Farm', 1],
    ],
  )
  assert.deepEqual(
    groups[0]?.chapters.map((chapter) => [chapter.title, chapter.notes.length]),
    [['Chapter 1', 2]],
  )
})

test('Markdown export uses book/chapter hierarchy and quote blocks', () => {
  const markdown = toBookNotesMarkdown(notes, '2026-07-15')
  assert.match(markdown, /^# ReadQuest Book Notes/m)
  assert.match(markdown, /## Nineteen Eighty-Four/)
  assert.match(markdown, /### Chapter 1/)
  assert.match(markdown, /> It was a bright cold day in April\./)
  assert.match(markdown, /\*\*Note\*\*\n\nA sharp opening contrast\./)
})

test('Markdown export escapes note content that could become markup', () => {
  const markdown = toBookNotesMarkdown(
    [
      {
        ...notes[0]!,
        quote: '# heading\n<script>alert(1)</script>',
        note: '[link](https://example.test)\n> forged quote',
      },
    ],
    '2026-07-15',
  )

  assert.match(markdown, /> \\# heading/)
  assert.doesNotMatch(markdown, /<script>/)
  assert.match(markdown, /\\\[link\\\]\\\(https:\/\/example\.test\\\)/)
  assert.match(markdown, /\\> forged quote/)
})
