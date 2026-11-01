import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createImportState,
  importErrorMessage,
  importProgress,
  importReducer,
  isImportTerminal,
} from './importState.js'
import type { ImportInspection, ImportJob } from './contracts.js'

const inspection: ImportInspection = {
  id: 'inspection-1',
  kind: 'web-article',
  displayUrl: 'https://example.com/article',
  originHost: 'example.com',
  detectedFormat: 'html',
  preview: {
    title: 'An article',
    byline: 'A Writer',
    siteName: 'Example',
    excerpt: 'A useful article preview.',
    wordCount: 780,
  },
  expiresAt: '2026-07-27T12:00:00.000Z',
}

function job(status: ImportJob['status'], percent: number): ImportJob {
  return {
    id: 'job-1',
    kind: 'web-article',
    status,
    progress: { phase: status, percent },
    error: null,
    bookId: null,
    firstStageId: null,
    createdAt: '2026-07-27T10:00:00.000Z',
    updatedAt: '2026-07-27T10:01:00.000Z',
  }
}

test('URL import moves from inspection to preview without retaining the submitted URL', () => {
  const inspecting = importReducer(createImportState(), {
    type: 'inspect-started',
    submittedUrl: 'https://example.com/article?utm_source=secret',
  })
  const preview = importReducer(inspecting, { type: 'inspect-succeeded', inspection })

  assert.equal(inspecting.phase, 'inspecting')
  assert.equal(preview.phase, 'preview')
  assert.equal(preview.submittedUrl, '')
  assert.equal(preview.inspection?.displayUrl, 'https://example.com/article')
})

test('a created import job enters tracking and terminal updates stop polling', () => {
  const tracking = importReducer(createImportState(), {
    type: 'job-created',
    job: job('queued', 0),
  })
  const completedJob = {
    ...job('completed', 100),
    bookId: 'book-1',
    firstStageId: 'stage-1',
  }
  const completed = importReducer(tracking, {
    type: 'job-updated',
    job: completedJob,
  })

  assert.equal(tracking.phase, 'tracking')
  assert.equal(completed.phase, 'completed')
  assert.equal(isImportTerminal(completedJob), true)
})

test('import progress clamps malformed server percentages', () => {
  assert.equal(importProgress(job('fetching', -10)), 0)
  assert.equal(importProgress(job('ingesting', 180)), 100)
  assert.equal(importProgress(job('extracting', Number.NaN)), 0)
})

test('failed jobs expose stable, user-actionable error copy', () => {
  const failed: ImportJob = {
    ...job('failed', 45),
    error: {
      code: 'PAGE_ACCESS_CHALLENGE',
      message: 'Internal provider response',
      retryable: false,
    },
  }

  const next = importReducer(createImportState(), { type: 'job-updated', job: failed })

  assert.equal(next.phase, 'failed')
  assert.equal(
    importErrorMessage(failed.error),
    'This page requires an access challenge. Open the original page or paste the text instead.',
  )
})

test('rights attestation failures explain the public-sharing requirement', () => {
  assert.equal(
    importErrorMessage({
      code: 'RIGHTS_ATTESTATION_REQUIRED',
      message: 'Internal validation detail',
      retryable: false,
    }),
    'Confirm that you own this content or have permission to make it readable by other ReadQuest users.',
  )
})

test('starting a new inspection clears a previous failure', () => {
  const failed = importReducer(createImportState(), {
    type: 'failed',
    message: 'Previous failure',
  })
  const next = importReducer(failed, {
    type: 'inspect-started',
    submittedUrl: 'https://example.com/new',
  })

  assert.equal(next.phase, 'inspecting')
  assert.equal(next.error, '')
  assert.equal(next.job, null)
})

test('a temporary polling failure keeps the active job tracking and can recover', () => {
  const tracking = importReducer(createImportState(), {
    type: 'job-created',
    job: job('fetching', 20),
  })
  const warning = importReducer(tracking, {
    type: 'refresh-failed',
    message: 'Status refresh failed.',
  })
  const recovered = importReducer(warning, {
    type: 'job-updated',
    job: job('ingesting', 70),
  })

  assert.equal(warning.phase, 'tracking')
  assert.equal(warning.job?.status, 'fetching')
  assert.equal(warning.refreshFailures, 1)
  assert.equal(warning.refreshWarning, 'Status refresh failed.')
  assert.equal(recovered.phase, 'tracking')
  assert.equal(recovered.refreshFailures, 0)
  assert.equal(recovered.refreshWarning, '')
})

test('retrying a failed job clears the terminal job but keeps its inspected preview', () => {
  const preview = importReducer(createImportState(), {
    type: 'inspect-succeeded',
    inspection,
  })
  const failed = importReducer(preview, {
    type: 'job-updated',
    job: {
      ...job('failed', 40),
      error: {
        code: 'SOURCE_TEMPORARILY_UNAVAILABLE',
        message: 'Unavailable',
        retryable: true,
      },
    },
  })
  const retrying = importReducer(failed, { type: 'create-started' })

  assert.equal(retrying.phase, 'creating')
  assert.equal(retrying.job, null)
  assert.equal(retrying.inspection, inspection)
  assert.equal(retrying.error, '')
})
