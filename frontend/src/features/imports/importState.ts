import type { ImportError, ImportInspection, ImportJob } from './contracts'

export type ImportPhase =
  'idle' | 'inspecting' | 'preview' | 'creating' | 'tracking' | 'completed' | 'failed'

export type ImportState = {
  phase: ImportPhase
  submittedUrl: string
  inspection: ImportInspection | null
  job: ImportJob | null
  error: string
  refreshWarning: string
  refreshFailures: number
}

export type ImportAction =
  | { type: 'reset' }
  | { type: 'inspect-started'; submittedUrl: string }
  | { type: 'inspect-succeeded'; inspection: ImportInspection }
  | { type: 'create-started' }
  | { type: 'job-created'; job: ImportJob }
  | { type: 'job-updated'; job: ImportJob }
  | { type: 'refresh-failed'; message: string }
  | { type: 'failed'; message: string }

export function createImportState(): ImportState {
  return {
    phase: 'idle',
    submittedUrl: '',
    inspection: null,
    job: null,
    error: '',
    refreshWarning: '',
    refreshFailures: 0,
  }
}

function phaseForJob(job: ImportJob): ImportPhase {
  if (job.status === 'completed') return 'completed'
  if (job.status === 'failed' || job.status === 'canceled') return 'failed'
  return 'tracking'
}

export function importReducer(state: ImportState, action: ImportAction): ImportState {
  if (action.type === 'reset') return createImportState()
  if (action.type === 'inspect-started') {
    return {
      ...createImportState(),
      phase: 'inspecting',
      submittedUrl: action.submittedUrl,
    }
  }
  if (action.type === 'inspect-succeeded') {
    return {
      ...state,
      phase: 'preview',
      submittedUrl: '',
      inspection: action.inspection,
      error: '',
    }
  }
  if (action.type === 'create-started') {
    return {
      ...state,
      phase: 'creating',
      job: null,
      error: '',
      refreshWarning: '',
      refreshFailures: 0,
    }
  }
  if (action.type === 'job-created' || action.type === 'job-updated') {
    return {
      ...state,
      phase: phaseForJob(action.job),
      submittedUrl: '',
      job: action.job,
      error: action.job.error ? importErrorMessage(action.job.error) : '',
      refreshWarning: '',
      refreshFailures: 0,
    }
  }
  if (action.type === 'refresh-failed') {
    return {
      ...state,
      refreshWarning: action.message,
      refreshFailures: state.refreshFailures + 1,
    }
  }
  return {
    ...state,
    phase: 'failed',
    submittedUrl: '',
    error: action.message,
    refreshWarning: '',
  }
}

export function importProgress(job: ImportJob): number {
  const percent = job.progress.percent
  if (!Number.isFinite(percent)) return 0
  return Math.max(0, Math.min(100, Math.round(percent)))
}

export function isImportTerminal(job: ImportJob): boolean {
  return job.status === 'completed' || job.status === 'failed' || job.status === 'canceled'
}

const ERROR_COPY: Record<string, string> = {
  INVALID_URL: 'Enter a complete public web address beginning with https://.',
  URL_NOT_PUBLIC: 'This address is not on the public internet and cannot be imported.',
  URL_BLOCKED_HOST: 'This website is not available for automatic import.',
  URL_REDIRECT_BLOCKED: 'The page redirected to an address that cannot be imported safely.',
  PAGE_REQUIRES_LOGIN:
    'This page requires a login. Open the original page or paste the text instead.',
  PAGE_ACCESS_CHALLENGE:
    'This page requires an access challenge. Open the original page or paste the text instead.',
  PAGE_NOT_READABLE: 'Readable article text could not be found. You can paste the text instead.',
  CONTENT_TOO_LARGE: 'This content is too large to import.',
  UNSUPPORTED_CONTENT_TYPE: 'This link is not a supported article, EPUB, PDF, or TXT file.',
  DOWNLOAD_NOT_AUTHORIZED: 'This source does not authorize an in-app download.',
  SOURCE_TEMPORARILY_UNAVAILABLE: 'The source is temporarily unavailable. Try again later.',
  IMPORT_ALREADY_EXISTS: 'This content is already in your library.',
  INSPECTION_EXPIRED: 'This preview expired. Inspect the link again before importing.',
  RIGHTS_ATTESTATION_REQUIRED:
    'Confirm that you own this content or have permission to make it readable by other ReadQuest users.',
  RATE_LIMITED: 'Too many import requests were made. Wait a moment and try again.',
}

export function importErrorMessage(error: ImportError | null | undefined): string {
  if (!error) return 'The import could not be completed. Please try again.'
  return ERROR_COPY[error.code] ?? 'The import could not be completed. Please try again.'
}
