import assert from 'node:assert/strict'
import test from 'node:test'
import { resolveApiBaseUrl } from './runtimeEndpoints'

test('the default API endpoint stays on the current browser origin', () => {
  assert.equal(resolveApiBaseUrl(undefined), '/api')
  assert.equal(resolveApiBaseUrl('   '), '/api')
})

test('an explicit API endpoint remains authoritative', () => {
  assert.equal(resolveApiBaseUrl(' https://api.example.test/api/ '), 'https://api.example.test/api')
})
