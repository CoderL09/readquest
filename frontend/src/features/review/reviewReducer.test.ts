import assert from 'node:assert/strict'
import type { ActiveReviewSession, ReviewSummary } from './contracts'
import { initialReviewState, reviewExperienceReducer } from './reviewReducer'

const summary: ReviewSummary = { dueCount: 8, nextDueAt: null, activeSession: null }
const session: ActiveReviewSession = {
  id: 'session-1',
  userId: 'reader-1',
  groupSize: 5,
  currentIndex: 0,
  total: 5,
  state: 'active',
  currentStage: 'hintShown',
  card: {
    id: 'card-1',
    kind: 'wordMeaning',
    originalSentence: 'The bank was covered in flowers.',
    targetText: 'bank',
    highlightRanges: [{ start: 4, end: 8 }],
    legacyHighlightFallback: false,
  },
  submission: {
    answerKind: 'wordMeaning',
    stage: 'initial',
    userAnswer: '银行',
    gradingState: 'completed',
    idempotencyKey: 'answer-key-1',
    ratingApplied: true,
    derivedRating: 'Again',
    grade: {
      verdict: 'incorrect',
      score: 0.2,
      feedbackZh: '语境义不符。',
      contextHintZh: '关注野花。',
    },
  },
}

const restored = reviewExperienceReducer(initialReviewState, { type: 'HYDRATED', summary, session })
assert.equal(restored.status, 'reviewing')
assert.equal(restored.draft, '银行')
assert.equal(restored.submissionStatus, 'graded')
assert.equal(restored.grade?.verdict, 'incorrect')

const submitting = reviewExperienceReducer(
  { ...restored, submissionStatus: 'idle' },
  { type: 'SUBMIT_STARTED' },
)
assert.equal(submitting.submissionStatus, 'submitting')
assert.equal(
  reviewExperienceReducer(submitting, { type: 'DRAFT_CHANGED', value: 'should be ignored' }).draft,
  '银行',
)

const advancedSession = { ...session, currentStage: 'guidedRevision' as const, submission: null }
const advanced = reviewExperienceReducer(restored, {
  type: 'RESET_FOR_STAGE',
  session: advancedSession,
})
assert.equal(advanced.draft, '')
assert.equal(advanced.submissionStatus, 'idle')

const revealed = reviewExperienceReducer(restored, {
  type: 'SUBMIT_SUCCEEDED',
  result: {
    gradingState: 'completed',
    retryable: false,
    currentStage: 'feedback',
    ratingApplied: true,
    referenceAnswerZh: '在此处指河岸',
    contextMeaningZh: '在此处指河岸',
    dictionary: {
      headword: 'bank',
      definition: '河岸；河堤。The land beside a river.',
      sourceName: 'Collins',
    },
  },
})
assert.equal(revealed.session?.card?.referenceAnswerZh, '在此处指河岸')
assert.equal(
  revealed.session?.card?.kind === 'wordMeaning' && revealed.session.card.contextMeaningZh,
  '在此处指河岸',
)
assert.equal(
  revealed.session?.card?.kind === 'wordMeaning' && revealed.session.card.dictionary?.sourceName,
  'Collins',
)

const noCards = reviewExperienceReducer(initialReviewState, {
  type: 'HYDRATED',
  summary: { dueCount: 0, nextDueAt: '2026-07-14T08:00:00.000Z', activeSession: null },
  session: null,
})
assert.equal(noCards.status, 'complete')

console.log('review reducer tests passed')
