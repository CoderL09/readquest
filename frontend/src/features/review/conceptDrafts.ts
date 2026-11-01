export type ConceptDraft = {
  bookId: string
  chapterId?: string
  sourceSentenceKey: string
  originalSentence: string
  category: string
  reverseQuestionZh: string
  referenceExplanationZh: string
  requiredPointsZh: string[]
  targetFragments?: Array<{ text: string; start?: number; end?: number }>
}

export type ReviewConceptResult = {
  status: 'suggested' | 'failed'
  retryable: boolean
  concepts?: ConceptDraft[]
  errorCode?: string
}

export function reviewSourceHref(card: { id: string; bookId?: string; chapterId?: string | null }) {
  if (!card.bookId || !card.chapterId) return null
  return `/app/reader/${encodeURIComponent(card.chapterId)}?${new URLSearchParams({ bookId: card.bookId, reviewCard: card.id })}`
}
