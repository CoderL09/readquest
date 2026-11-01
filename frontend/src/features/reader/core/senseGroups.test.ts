import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readSenseGroups, senseGroupPieces } from './senseGroups'

const text = '  We 😀 read\tand/or listen. '
const response = (chunks: unknown[]) => ({
  status: 'ready',
  source: 'model',
  sentences: [{ text, chunks }],
})
const chunks = [
  { text: 'We 😀 read', start: 2, end: 12 },
  { text: 'and/or listen.', start: 13, end: 27 },
]

test('sense groups reconstruct exact whitespace, slashes and UTF-16 source text', () => {
  const result = readSenseGroups(text, response(chunks))
  assert.equal(result.groups.length, 2)
  assert.equal(
    senseGroupPieces(text, result.groups)
      .map((piece) => piece.text)
      .join(''),
    text,
  )
})

test('rejects omitted, rewritten, reordered and overlapping groups', () => {
  for (const invalid of [
    [chunks[0]],
    [{ ...chunks[0], text: 'We read' }, chunks[1]],
    [chunks[1], chunks[0]],
    [chunks[0], chunks[0], chunks[1]],
    [{ ...chunks[0], start: -1 }, chunks[1]],
    [{ ...chunks[0], end: 12.5 }, chunks[1]],
  ])
    assert.throws(() => readSenseGroups(text, response(invalid)))
})

test('reports unavailable analysis rather than presenting the original as analyzed', () => {
  assert.throws(
    () => readSenseGroups(text, { status: 'unavailable', reason: 'quota_exceeded' }),
    /额度不足/,
  )
  assert.throws(() => readSenseGroups(text, response([])))
})
