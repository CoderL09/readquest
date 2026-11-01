import assert from 'node:assert/strict'
import test from 'node:test'
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { api } from '../../lib/api.js'
import {
  createCatalogImport,
  createUrlImport,
  getImportJob,
  inspectImportUrl,
} from './importApi.js'

function successfulAdapter(
  onRequest: (config: InternalAxiosRequestConfig) => unknown,
  status = 200,
): AxiosAdapter {
  return async (config) => ({
    data: onRequest(config),
    status,
    statusText: status === 202 ? 'Accepted' : 'OK',
    headers: {},
    config,
  })
}

const inspection = {
  id: 'inspection-1',
  kind: 'web-article',
  displayUrl: 'https://example.com/article',
  originHost: 'example.com',
  detectedFormat: 'html',
  preview: { title: 'Article' },
  expiresAt: '2026-07-27T12:00:00.000Z',
}

const job = {
  id: 'job-1',
  kind: 'web-article',
  status: 'queued',
  progress: { phase: 'queued', percent: 0 },
  error: null,
  bookId: null,
  firstStageId: null,
  createdAt: '2026-07-27T10:00:00.000Z',
  updatedAt: '2026-07-27T10:00:00.000Z',
}

test('URL inspection trims the address and passes request cancellation through', async () => {
  const originalAdapter = api.defaults.adapter
  const controller = new AbortController()
  let observed: InternalAxiosRequestConfig | undefined
  api.defaults.adapter = successfulAdapter((config) => {
    observed = config
    return { inspection }
  })

  try {
    assert.deepEqual(
      await inspectImportUrl('  https://example.com/article  ', controller.signal),
      inspection,
    )
    assert.equal(observed?.url, '/imports/url/inspect')
    assert.equal(observed?.method, 'post')
    assert.equal(observed?.data, JSON.stringify({ url: 'https://example.com/article' }))
    assert.equal(observed?.signal, controller.signal)
  } finally {
    api.defaults.adapter = originalAdapter
  }
})

test('URL import sends an exact rights attestation with the URL and optional title', async () => {
  const originalAdapter = api.defaults.adapter
  const requests: InternalAxiosRequestConfig[] = []
  api.defaults.adapter = successfulAdapter((config) => {
    requests.push(config)
    return { job, deduplicated: false }
  }, 202)

  try {
    await createUrlImport({
      url: 'https://example.com/article',
      title: '  My article  ',
      rightsAttested: true,
    })
    await createUrlImport({
      url: 'https://example.com/second',
      title: '   ',
      rightsAttested: true,
    })
    assert.equal(
      requests[0]?.data,
      JSON.stringify({
        url: 'https://example.com/article',
        title: 'My article',
        rightsAttested: true,
      }),
    )
    assert.equal(
      requests[1]?.data,
      JSON.stringify({
        url: 'https://example.com/second',
        rightsAttested: true,
      }),
    )
  } finally {
    api.defaults.adapter = originalAdapter
  }
})

test('catalog import sends the server-issued provider, external, and offer identifiers', async () => {
  const originalAdapter = api.defaults.adapter
  let observed: InternalAxiosRequestConfig | undefined
  api.defaults.adapter = successfulAdapter((config) => {
    observed = config
    return { job: { ...job, kind: 'catalog-offer' }, deduplicated: false }
  }, 202)

  try {
    await createCatalogImport({
      providerId: 'standardebooks',
      externalId: 'book-1',
      offerId: 'offer-1',
    })
    assert.equal(observed?.url, '/imports/catalog')
    assert.equal(
      observed?.data,
      JSON.stringify({
        providerId: 'standardebooks',
        externalId: 'book-1',
        offerId: 'offer-1',
      }),
    )
  } finally {
    api.defaults.adapter = originalAdapter
  }
})

test('job lookup safely encodes an opaque import identifier', async () => {
  const originalAdapter = api.defaults.adapter
  let observed: InternalAxiosRequestConfig | undefined
  api.defaults.adapter = successfulAdapter((config) => {
    observed = config
    return { job }
  })

  try {
    assert.deepEqual(await getImportJob('job/with spaces'), job)
    assert.equal(observed?.url, '/imports/job%2Fwith%20spaces')
  } finally {
    api.defaults.adapter = originalAdapter
  }
})

test('URL APIs reject empty input before dispatching a request', async () => {
  await assert.rejects(() => inspectImportUrl('   '), /URL is required/)
  await assert.rejects(() => createUrlImport({ url: '', rightsAttested: true }), /URL is required/)
})

test('URL import rejects a missing rights attestation before dispatching a request', async () => {
  await assert.rejects(
    () =>
      createUrlImport({
        url: 'https://example.com/article',
        rightsAttested: false,
      }),
    /Rights attestation is required/,
  )
})
