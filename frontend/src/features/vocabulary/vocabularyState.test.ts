import assert from 'node:assert/strict'
import test from 'node:test'
import {
  filterVocabulary,
  groupVocabularyByBook,
  groupVocabularyByMeaning,
  masterySummary,
  meaningGroupHeadingId,
  toVocabularyCsv,
  toVocabularyMarkdown,
  type VocabularyItem,
  type VocabularyMastery,
} from './vocabularyState'

const unobservedMastery: VocabularyMastery = {
  source: 'unobserved',
  score: null,
  recallProbability: null,
  answerQuality: null,
  confidence: 'none',
  evidenceCount: 0,
  lapseCount: 0,
  due: false,
  dueAt: null,
  lastReviewedAt: null,
  stabilityDays: null,
  difficulty: null,
}

test('mastery summary reports evidence-based progress instead of the legacy level', () => {
  assert.equal(
    masterySummary({
      source: 'fsrs',
      score: 84.2,
      recallProbability: 91,
      answerQuality: 75,
      confidence: 'growing',
      evidenceCount: 4,
      lapseCount: 1,
      due: false,
      dueAt: '2026-07-24T00:00:00.000Z',
      lastReviewedAt: '2026-07-22T00:00:00.000Z',
      stabilityDays: 12,
      difficulty: 5,
    }),
    'Mastery 84.2% · growing confidence',
  )
  assert.equal(
    masterySummary({
      source: 'unobserved',
      score: null,
      recallProbability: null,
      answerQuality: null,
      confidence: 'none',
      evidenceCount: 0,
      lapseCount: 0,
      due: false,
      dueAt: null,
      lastReviewedAt: null,
      stabilityDays: null,
      difficulty: null,
    }),
    'No review evidence yet',
  )
})

const items: VocabularyItem[] = [
  {
    id: '1',
    type: 'word',
    text: 'bank',
    normalizedText: 'bank',
    explanation: 'the land beside a river',
    translation: '河岸',
    originalSentence: 'We sat on the bank.',
    book: { id: 'book-a', title: 'River Story', author: 'A. Writer' },
    reviewStatus: 'learning',
    masteryLevel: 2,
    mastery: unobservedMastery,
    lookupTime: '2026-07-15T08:00:00.000Z',
  },
  {
    id: '2',
    type: 'word',
    text: 'Bank',
    normalizedText: 'bank',
    explanation: 'the land beside a river',
    translation: '河岸',
    originalSentence: 'Trees lined the bank.',
    book: { id: 'book-b', title: 'Forest Notes', author: 'B. Writer' },
    reviewStatus: 'new',
    masteryLevel: 0,
    mastery: unobservedMastery,
    lookupTime: '2026-07-14T08:00:00.000Z',
  },
  {
    id: '3',
    type: 'word',
    text: 'bank',
    normalizedText: 'bank',
    explanation: 'a financial institution',
    translation: '银行',
    originalSentence: 'The bank closes at five.',
    book: { id: 'book-a', title: 'River Story', author: 'A. Writer' },
    reviewStatus: 'mastered',
    masteryLevel: 6,
    mastery: {
      ...unobservedMastery,
      source: 'legacy',
      score: 100,
      confidence: 'limited',
      evidenceCount: 1,
    },
    lookupTime: '2026-07-13T08:00:00.000Z',
  },
]

test('book organization keeps source books separate and newest first', () => {
  const groups = groupVocabularyByBook(items)
  assert.deepEqual(
    groups.map((group) => [group.book.title, group.items.length]),
    [
      ['River Story', 2],
      ['Forest Notes', 1],
    ],
  )
  assert.deepEqual(
    groups[0]?.items.map((item) => item.id),
    ['1', '3'],
  )
})

test('meaning organization combines matching senses but preserves distinct senses', () => {
  const groups = groupVocabularyByMeaning(items)
  assert.equal(groups.length, 1)
  assert.equal(groups[0]?.term, 'bank')
  assert.deepEqual(
    groups[0]?.senses.map((sense) => [sense.meaning, sense.occurrences.length]),
    [
      ['the land beside a river', 2],
      ['a financial institution', 1],
    ],
  )
})

test('meaning group heading ids remain valid ID references for phrases', () => {
  const headingId = meaningGroupHeadingId('phrase:take off')

  assert.doesNotMatch(headingId, /\s/)
  assert.equal(headingId, 'term-phrase%3Atake%20off')
})

test('filtering searches context and combines book and status filters', () => {
  assert.deepEqual(
    filterVocabulary(items, { query: 'closes', bookId: 'book-a', status: 'mastered' }).map(
      (item) => item.id,
    ),
    ['3'],
  )
  assert.deepEqual(
    filterVocabulary(items, { query: 'forest', bookId: 'all', status: 'all' }).map(
      (item) => item.id,
    ),
    ['2'],
  )
})

test('CSV export quotes structured text and neutralizes spreadsheet formulas', () => {
  const csv = toVocabularyCsv([
    {
      ...items[0]!,
      text: '=IMPORTXML("https://example.test")',
      originalSentence: 'First line, with comma\nSecond line',
    },
  ])

  assert.ok(csv.startsWith('\uFEFF"Term","Type"'))
  assert.match(csv, /"'=IMPORTXML\(""https:\/\/example\.test""\)"/)
  assert.match(csv, /"First line, with comma\nSecond line"/)
})

test('Markdown export uses term headings and contextual source details', () => {
  const markdown = toVocabularyMarkdown(items)
  assert.match(markdown, /## bank/)
  assert.match(markdown, /### the land beside a river/)
  assert.match(markdown, /River Story/)
  assert.match(markdown, /We sat on the bank\./)
})
