export type VocabularyStatus = 'new' | 'learning' | 'reviewing' | 'mastered'
export type VocabularyType = 'word' | 'phrase'

export type VocabularyBook = {
  id: string
  title: string
  author: string
}

export type VocabularyMastery = {
  source: 'fsrs' | 'legacy' | 'unobserved'
  score: number | null
  recallProbability: number | null
  answerQuality: number | null
  confidence: 'none' | 'limited' | 'growing' | 'reliable'
  evidenceCount: number
  lapseCount: number
  due: boolean
  dueAt: string | null
  lastReviewedAt: string | null
  stabilityDays: number | null
  difficulty: number | null
}

export type VocabularyItem = {
  id: string
  type: VocabularyType
  text: string
  normalizedText: string
  explanation: string
  translation: string
  originalSentence: string
  book: VocabularyBook
  reviewStatus: VocabularyStatus
  masteryLevel: number
  mastery: VocabularyMastery
  lookupTime: string
}

export type VocabularyFilters = {
  query: string
  bookId: string
  status: VocabularyStatus | 'all'
}

export type VocabularyBookGroup = {
  book: VocabularyBook
  items: VocabularyItem[]
}

export type VocabularySense = {
  key: string
  meaning: string
  occurrences: VocabularyItem[]
}

export type VocabularyMeaningGroup = {
  key: string
  term: string
  type: VocabularyType
  senses: VocabularySense[]
  latestLookupTime: string
}

function normalizeText(value: string): string {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase()
}

function newestFirst(left: VocabularyItem, right: VocabularyItem): number {
  return Date.parse(right.lookupTime) - Date.parse(left.lookupTime)
}

export function masterySummary(mastery: VocabularyMastery): string {
  if (mastery.score === null) return 'No review evidence yet'
  return `Mastery ${mastery.score}% · ${mastery.confidence} confidence`
}

export function meaningGroupHeadingId(groupKey: string): string {
  return `term-${encodeURIComponent(groupKey)}`
}

export function meaningFor(item: VocabularyItem): string {
  return item.explanation.trim() || item.translation.trim() || 'Meaning not recorded'
}

export function filterVocabulary(
  items: VocabularyItem[],
  filters: VocabularyFilters,
): VocabularyItem[] {
  const query = normalizeText(filters.query)

  return items
    .filter((item) => {
      if (filters.bookId !== 'all' && item.book.id !== filters.bookId) return false
      if (filters.status !== 'all' && item.reviewStatus !== filters.status) return false
      if (!query) return true

      const searchable = [
        item.text,
        item.normalizedText,
        item.explanation,
        item.translation,
        item.originalSentence,
        item.book.title,
        item.book.author,
      ].join(' ')
      return normalizeText(searchable).includes(query)
    })
    .sort(newestFirst)
}

export function groupVocabularyByBook(items: VocabularyItem[]): VocabularyBookGroup[] {
  const groups = new Map<string, VocabularyBookGroup>()

  for (const item of [...items].sort(newestFirst)) {
    const existing = groups.get(item.book.id)
    if (existing) {
      existing.items.push(item)
    } else {
      groups.set(item.book.id, { book: item.book, items: [item] })
    }
  }

  return [...groups.values()]
}

export function groupVocabularyByMeaning(items: VocabularyItem[]): VocabularyMeaningGroup[] {
  const groups = new Map<string, VocabularyMeaningGroup>()

  for (const item of [...items].sort(newestFirst)) {
    const normalizedTerm = normalizeText(item.normalizedText || item.text)
    const groupKey = `${item.type}:${normalizedTerm}`
    let group = groups.get(groupKey)
    if (!group) {
      group = {
        key: groupKey,
        term: item.text.trim() || normalizedTerm,
        type: item.type,
        senses: [],
        latestLookupTime: item.lookupTime,
      }
      groups.set(groupKey, group)
    }

    const meaning = meaningFor(item)
    const senseKey = normalizeText(meaning)
    const sense = group.senses.find((candidate) => candidate.key === senseKey)
    if (sense) {
      sense.occurrences.push(item)
    } else {
      group.senses.push({ key: senseKey, meaning, occurrences: [item] })
    }
  }

  return [...groups.values()]
}

function spreadsheetSafe(value: string): string {
  return /^[=+\-@]/.test(value.trimStart()) ? `'${value}` : value
}

function csvCell(value: string | number): string {
  const safeValue = spreadsheetSafe(String(value)).replace(/"/g, '""')
  return `"${safeValue}"`
}

export function toVocabularyCsv(items: VocabularyItem[]): string {
  const rows: Array<Array<string | number>> = [
    [
      'Term',
      'Type',
      'Meaning',
      'Translation',
      'Source sentence',
      'Book',
      'Author',
      'Status',
      'Mastery score',
      'Confidence',
      'Evidence count',
      'Saved at',
    ],
    ...items.map((item) => [
      item.text,
      item.type,
      meaningFor(item),
      item.translation,
      item.originalSentence,
      item.book.title,
      item.book.author,
      item.reviewStatus,
      item.mastery.score ?? '',
      item.mastery.confidence,
      item.mastery.evidenceCount,
      item.lookupTime,
    ]),
  ]

  return `\uFEFF${rows.map((row) => row.map(csvCell).join(',')).join('\r\n')}`
}

function markdownInline(value: string): string {
  return value
    .replace(/\r?\n/g, ' ')
    .replace(/([\\`*_[\]<>])/g, '\\$1')
    .trim()
}

export function toVocabularyMarkdown(items: VocabularyItem[]): string {
  const lines = ['# ReadQuest vocabulary', '']

  for (const group of groupVocabularyByMeaning(items)) {
    lines.push(`## ${markdownInline(group.term)}`, '')
    for (const sense of group.senses) {
      lines.push(`### ${markdownInline(sense.meaning)}`, '')
      for (const item of sense.occurrences) {
        const source = [item.book.title, item.book.author]
          .filter(Boolean)
          .map(markdownInline)
          .join(' — ')
        lines.push(
          `- **${source}** · ${markdownInline(item.reviewStatus)} · ${markdownInline(masterySummary(item.mastery))}`,
        )
        if (item.originalSentence.trim()) lines.push(`  - ${markdownInline(item.originalSentence)}`)
        if (
          item.translation.trim() &&
          normalizeText(item.translation) !== normalizeText(sense.meaning)
        ) {
          lines.push(`  - Translation: ${markdownInline(item.translation)}`)
        }
      }
      lines.push('')
    }
  }

  return lines.join('\n').trimEnd() + '\n'
}
