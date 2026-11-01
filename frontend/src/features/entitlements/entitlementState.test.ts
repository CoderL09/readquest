import assert from 'node:assert/strict'
import test from 'node:test'
import { entitlementNoticeFromResponse, quotaNoticeFromResponse } from './entitlementState.js'

test('only the stable 402 entitlement contract creates an upgrade notice', () => {
  assert.deepEqual(
    entitlementNoticeFromResponse(402, {
      error: {
        code: 'ENTITLEMENT_REQUIRED',
        message: 'Pro access required',
        feature: 'reader_rag_chat',
      },
    }),
    {
      feature: 'reader_rag_chat',
      message: 'Pro access required',
    },
  )

  assert.equal(
    entitlementNoticeFromResponse(403, {
      error: { code: 'ENTITLEMENT_REQUIRED', message: 'wrong status' },
    }),
    null,
  )
  assert.equal(
    entitlementNoticeFromResponse(402, {
      error: { code: 'UNRELATED', message: 'wrong code' },
    }),
    null,
  )
})

test('malformed entitlement errors use safe copy and do not trust arbitrary fields', () => {
  assert.deepEqual(
    entitlementNoticeFromResponse(402, {
      error: { code: 'ENTITLEMENT_REQUIRED', message: 42, feature: { forged: true } },
    }),
    {
      message: 'This AI feature requires ReadQuest Pro.',
    },
  )
})

test('only stable QUOTA_EXCEEDED 429 responses create an AI quota notice', () => {
  assert.deepEqual(
    quotaNoticeFromResponse(429, {
      error: {
        code: 'QUOTA_EXCEEDED',
        message: 'Daily AI request limit reached',
      },
    }),
    {
      message: 'Daily AI request limit reached',
    },
  )

  assert.equal(
    quotaNoticeFromResponse(429, {
      error: { code: 'RATE_LIMITED', message: 'Too many requests' },
    }),
    null,
  )
  assert.equal(
    quotaNoticeFromResponse(402, {
      error: { code: 'QUOTA_EXCEEDED', message: 'wrong status' },
    }),
    null,
  )
})

test('malformed quota responses use actionable safe copy', () => {
  assert.deepEqual(
    quotaNoticeFromResponse(429, {
      error: { code: 'QUOTA_EXCEEDED', message: { forged: true } },
    }),
    {
      message: 'Your daily AI limit has been reached. Try again after the UTC-day reset.',
    },
  )
})
