export type ReviewGroupSize = 5 | 10 | 20
export type ReviewCardKind = 'wordMeaning' | 'sentenceConcept'
export type ReviewStage =
  | 'initialAnswer'
  | 'hintShown'
  | 'guidedRevision'
  | 'referenceShown'
  | 'transferSentence'
  | 'gradingPending'
  | 'feedback'

export type SemanticVerdict = 'incorrect' | 'partial' | 'correct'

export interface SemanticGrade {
  verdict: SemanticVerdict
  score: number
  feedbackZh: string
  acknowledgedPointsZh?: string[]
  missingPointsZh?: string[]
  criticalMisconception?: boolean
  contextHintZh?: string
}

interface SharedReviewCard {
  id: string
  originalSentence: string
  bookId?: string
  chapterId?: string | null
  sourceSentenceKey?: string
  dueAt?: string
}

export interface WordMeaningReviewCard extends SharedReviewCard {
  kind: 'wordMeaning'
  targetText: string
  highlightRanges: Array<{ start: number; end: number }>
  legacyHighlightFallback: boolean
  referenceAnswerZh?: string
  contextMeaningZh?: string
  dictionary?: {
    headword: string
    definition: string
    sourceName: string
  }
}

export interface SentenceConceptReviewCard extends SharedReviewCard {
  kind: 'sentenceConcept'
  reverseQuestionZh: string
  category: string
  targetFragments: Array<{ text: string; start?: number; end?: number }>
  referenceAnswerZh?: string
}

export type ReviewCardPresentation = WordMeaningReviewCard | SentenceConceptReviewCard

export type ReviewLibraryItem = ReviewCardPresentation & {
  dueAt: string
  createdAt: string
  isDue: boolean
}

export interface ReviewLibraryResponse {
  total: number
  page: number
  pageSize: number
  hasMore: boolean
  counts: {
    wordMeaning: number
    sentenceConcept: number
  }
  items: ReviewLibraryItem[]
}

export interface SavedSubmission {
  answerKind: 'wordMeaning' | 'conceptExplanation' | 'transferSentence'
  stage: 'initial' | 'guidedRevision'
  userAnswer: string
  gradingState: 'pending' | 'completed' | 'failed'
  grade?: SemanticGrade
  failureCode?: string
  idempotencyKey: string
  ratingApplied: boolean
  derivedRating?: 'Again' | 'Hard' | 'Good'
}

export interface ActiveReviewSession {
  id: string
  userId: string
  groupSize: ReviewGroupSize
  currentIndex: number
  total: number
  state: 'active' | 'celebrating'
  currentStage: ReviewStage
  card: ReviewCardPresentation | null
  submission?: SavedSubmission | null
}

export interface ReviewSummary {
  dueCount: number
  nextDueAt: string | null
  activeSession: {
    id: string
    state: ActiveReviewSession['state']
    currentIndex: number
    total: number
    groupSize: ReviewGroupSize
  } | null
}

export interface ReviewAnswerResponse {
  gradingState: 'completed' | 'failed'
  retryable: boolean
  grade?: SemanticGrade
  currentStage: ReviewStage
  ratingApplied: boolean
  derivedRating?: 'Again' | 'Hard' | 'Good'
  referenceAnswerZh?: string
  contextMeaningZh?: string
  dictionary?: WordMeaningReviewCard['dictionary']
  failureCode?: string
}
