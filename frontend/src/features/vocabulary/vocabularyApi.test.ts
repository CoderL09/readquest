import assert from 'node:assert/strict'
import test from 'node:test'
import { collectVocabularyPages, type VocabularyPage } from './vocabularyApi'
import type { VocabularyItem } from './vocabularyState'

function item(id: string): VocabularyItem {
  return {
    id,
    type: 'word',
    text: id,
    normalizedText: id,
    explanation: '',
    translation: '',
    originalSentence: '',
    book: { id: 'book', title: 'Book', author: 'Author' },
    reviewStatus: 'new',
    masteryLevel: 0,
    mastery: {
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
    },
    lookupTime: '2026-07-15T00:00:00.000Z',
  }
}

test('page collection continues until the complete vocabulary is loaded', async () => {
  const pages: VocabularyPage[] = [
    { items: [item('one'), item('two')], page: 1, pageSize: 2, total: 3, hasMore: true },
    { items: [item('three')], page: 2, pageSize: 2, total: 3, hasMore: false },
  ]
  const requestedPages: number[] = []

  const result = await collectVocabularyPages(async (page) => {
    requestedPages.push(page)
    return pages[page - 1]!
  })

  assert.deepEqual(requestedPages, [1, 2])
  assert.deepEqual(
    result.map((entry) => entry.id),
    ['one', 'two', 'three'],
  )
})

test('page collection rejects an empty continuation page instead of looping forever', async () => {
  await assert.rejects(() =>
    collectVocabularyPages(async () => ({
      items: [],
      page: 1,
      pageSize: 100,
      total: 1,
      hasMore: true,
    })),
  )
})
