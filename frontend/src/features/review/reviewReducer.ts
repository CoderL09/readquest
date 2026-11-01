import type {
  ActiveReviewSession,
  ReviewAnswerResponse,
  ReviewSummary,
  SemanticGrade,
} from './contracts'

export type ReviewExperienceStatus =
  'loading' | 'setup' | 'reviewing' | 'celebrating' | 'complete' | 'error'

export interface ReviewExperienceState {
  status: ReviewExperienceStatus
  summary: ReviewSummary | null
  session: ActiveReviewSession | null
  draft: string
  submissionStatus: 'idle' | 'submitting' | 'graded' | 'failed'
  grade: SemanticGrade | null
  error: string | null
}

export const initialReviewState: ReviewExperienceState = {
  status: 'loading',
  summary: null,
  session: null,
  draft: '',
  submissionStatus: 'idle',
  grade: null,
  error: null,
}

export type ReviewExperienceAction =
  | { type: 'HYDRATED'; summary: ReviewSummary; session: ActiveReviewSession | null }
  | { type: 'SESSION_UPDATED'; session: ActiveReviewSession }
  | { type: 'DRAFT_CHANGED'; value: string }
  | { type: 'SUBMIT_STARTED' }
  | { type: 'SUBMIT_SUCCEEDED'; result: ReviewAnswerResponse }
  | { type: 'SUBMIT_FAILED'; message: string }
  | { type: 'RESET_FOR_STAGE'; session: ActiveReviewSession }
  | { type: 'FATAL_ERROR'; message: string }

function stateFromSession(
  state: ReviewExperienceState,
  summary: ReviewSummary,
  session: ActiveReviewSession | null,
): ReviewExperienceState {
  if (!session) {
    return {
      ...state,
      summary,
      session: null,
      status: summary.dueCount > 0 ? 'setup' : 'complete',
      submissionStatus: 'idle',
      grade: null,
      error: null,
    }
  }
  const submission = session.submission
  return {
    ...state,
    summary,
    session,
    status: session.state === 'celebrating' ? 'celebrating' : 'reviewing',
    draft: submission?.userAnswer ?? '',
    submissionStatus:
      submission?.gradingState === 'completed'
        ? 'graded'
        : submission?.gradingState === 'failed'
          ? 'failed'
          : submission?.gradingState === 'pending'
            ? 'submitting'
            : 'idle',
    grade: submission?.grade ?? null,
    error: submission?.failureCode ?? null,
  }
}

export function reviewExperienceReducer(
  state: ReviewExperienceState,
  action: ReviewExperienceAction,
): ReviewExperienceState {
  switch (action.type) {
    case 'HYDRATED':
      return stateFromSession(state, action.summary, action.session)
    case 'SESSION_UPDATED':
      return stateFromSession(
        state,
        state.summary ?? { dueCount: 0, nextDueAt: null, activeSession: null },
        action.session,
      )
    case 'DRAFT_CHANGED':
      return state.submissionStatus === 'submitting' ? state : { ...state, draft: action.value }
    case 'SUBMIT_STARTED':
      return state.submissionStatus === 'submitting'
        ? state
        : { ...state, submissionStatus: 'submitting', error: null }
    case 'SUBMIT_SUCCEEDED':
      return {
        ...state,
        session: state.session
          ? {
              ...state.session,
              currentStage: action.result.currentStage,
              card:
                state.session.card && action.result.referenceAnswerZh
                  ? {
                      ...state.session.card,
                      referenceAnswerZh: action.result.referenceAnswerZh,
                      ...(state.session.card.kind === 'wordMeaning'
                        ? {
                            contextMeaningZh:
                              action.result.contextMeaningZh ?? action.result.referenceAnswerZh,
                            ...(action.result.dictionary
                              ? { dictionary: action.result.dictionary }
                              : {}),
                          }
                        : {}),
                    }
                  : state.session.card,
            }
          : null,
        draft: action.result.currentStage === 'transferSentence' ? '' : state.draft,
        submissionStatus:
          action.result.currentStage === 'transferSentence'
            ? 'idle'
            : action.result.gradingState === 'completed'
              ? 'graded'
              : 'failed',
        grade: action.result.grade ?? null,
        error: action.result.failureCode ?? null,
      }
    case 'SUBMIT_FAILED':
      return { ...state, submissionStatus: 'failed', error: action.message }
    case 'RESET_FOR_STAGE': {
      const restored = stateFromSession(
        state,
        state.summary ?? { dueCount: 0, nextDueAt: null, activeSession: null },
        action.session,
      )
      const preservePriorGrade =
        action.session.currentStage === 'guidedRevision' ||
        action.session.currentStage === 'transferSentence' ||
        action.session.currentStage === 'referenceShown'
      return {
        ...restored,
        draft: '',
        submissionStatus: 'idle',
        grade: preservePriorGrade ? restored.grade : null,
      }
    }
    case 'FATAL_ERROR':
      return { ...state, status: 'error', error: action.message }
  }
}
