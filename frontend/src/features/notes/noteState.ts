export type BookNote = {
  id: string
  quote: string
  note: string
  book: { id: string; title: string; author: string }
  chapter: { id: string | null; title: string }
  paragraphIndex: number | null
  createdAt: string
  updatedAt: string
}

export type BookNoteFilters = {
  query: string
  bookId: string
}

export type BookNoteChapterGroup = {
  id: string
  title: string
  notes: BookNote[]
}

export type BookNoteBookGroup = {
  book: BookNote['book']
  notes: BookNote[]
  chapters: BookNoteChapterGroup[]
}

function normalized(value: string): string {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase()
}

function newestFirst(left: BookNote, right: BookNote): number {
  return Date.parse(right.createdAt) - Date.parse(left.createdAt)
}

export function filterBookNotes(items: BookNote[], filters: BookNoteFilters): BookNote[] {
  const query = normalized(filters.query)
  return items
    .filter((item) => {
      if (filters.bookId !== 'all' && item.book.id !== filters.bookId) return false
      if (!query) return true
      return normalized(
        [item.quote, item.note, item.book.title, item.book.author, item.chapter.title].join(' '),
      ).includes(query)
    })
    .sort(newestFirst)
}

export function groupBookNotes(items: BookNote[]): BookNoteBookGroup[] {
  const groups = new Map<string, BookNoteBookGroup>()

  for (const item of [...items].sort(newestFirst)) {
    let bookGroup = groups.get(item.book.id)
    if (!bookGroup) {
      bookGroup = { book: item.book, notes: [], chapters: [] }
      groups.set(item.book.id, bookGroup)
    }
    bookGroup.notes.push(item)

    const chapterKey = item.chapter.id ?? `book:${item.book.id}`
    let chapterGroup = bookGroup.chapters.find((chapter) => chapter.id === chapterKey)
    if (!chapterGroup) {
      chapterGroup = { id: chapterKey, title: item.chapter.title, notes: [] }
      bookGroup.chapters.push(chapterGroup)
    }
    chapterGroup.notes.push(item)
  }

  return [...groups.values()]
}

function escapeMarkdown(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/</g, '&lt;')
    .replace(/([`*_{}()|>])/g, '\\$1')
    .replace(/\[/g, '\\[')
    .replace(/]/g, '\\]')
    .replace(/^(\s*)#/g, '$1\\#')
    .replace(/^(\s*)[-+]\s/g, (match) => match.replace(/[-+]/, '\\$&'))
    .replace(/^(\s*\d+)\.\s/g, '$1\\. ')
}

function markdownLines(value: string): string[] {
  return value.split(/\r?\n/).map((line) => escapeMarkdown(line))
}

export function toBookNotesMarkdown(
  items: BookNote[],
  generatedOn = new Date().toISOString().slice(0, 10),
): string {
  const lines = [
    '# ReadQuest Book Notes',
    '',
    `_Exported ${escapeMarkdown(generatedOn)} · ${items.length} excerpt${items.length === 1 ? '' : 's'}_`,
    '',
  ]

  for (const bookGroup of groupBookNotes(items)) {
    lines.push(`## ${escapeMarkdown(bookGroup.book.title)}`, '')
    if (bookGroup.book.author) lines.push(`_${escapeMarkdown(bookGroup.book.author)}_`, '')

    for (const chapter of bookGroup.chapters) {
      lines.push(`### ${escapeMarkdown(chapter.title)}`, '')
      for (const item of chapter.notes) {
        lines.push(...markdownLines(item.quote).map((line) => `> ${line}`), '')
        if (item.note) lines.push('**Note**', '', ...markdownLines(item.note), '')
        const location =
          item.paragraphIndex === null ? '' : ` · Paragraph ${item.paragraphIndex + 1}`
        lines.push(
          `_${escapeMarkdown(item.createdAt.slice(0, 10))}${escapeMarkdown(location)}_`,
          '',
          '---',
          '',
        )
      }
    }
  }

  return lines.join('\n').trimEnd() + '\n'
}
