import assert from 'node:assert/strict'
import { createHash, webcrypto } from 'node:crypto'
import { test } from 'node:test'
import { citationHref, citationTarget, resolveCitation } from './bookCitation'

Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
const text = '  Same 😀 line.\n\nSame 😀 line.\n\nLast paragraph.'
const chapterHash = createHash('sha256').update(text).digest('hex')
const startOffset = text.lastIndexOf('Same')
const target = { startOffset, endOffset: text.length - 2, chapterHash }

test('locates a repeated quote by its offset and highlights across paragraphs', async () => {
  const result = await resolveCitation(
    text,
    text.split(/\n\s*\n/).map((text) => ({ text: text.trim() })),
    target,
  )
  assert.equal(result.paragraphIndex, 1)
  assert.equal(result.ranges[0], null)
  assert.deepEqual(result.ranges[1], { start: 0, end: 'Same 😀 line.'.length })
  assert.deepEqual(result.ranges[2], { start: 0, end: 'Last paragrap'.length })
  assert.equal(result.quote, text.slice(target.startOffset, target.endOffset))
})

test('rejects changed content and malformed citation links', async () => {
  await assert.rejects(resolveCitation(text + 'changed', [{ text }], target), /章节内容已变化/)
  for (const sourceStart of ['-1', 'NaN', '1.5', ['0']]) {
    assert.equal(citationTarget({ sourceStart, sourceEnd: '10', sourceHash: chapterHash }), null)
  }
  assert.equal(
    citationTarget({ sourceStart: '0', sourceEnd: '999999', sourceHash: chapterHash }),
    null,
  )
  assert.equal(citationTarget({ sourceStart: '0', sourceEnd: '10', sourceHash: 'invalid' }), null)
})

test('citation links include only location metadata and stay within the current book', () => {
  const source = {
    ...target,
    citation: 1,
    bookId: 'book',
    stageId: 'chapter',
    stageTitle: 'One',
    content: 'private quote',
  }
  const href = citationHref(source, 'book')!
  assert.ok(href.startsWith('/app/reader/chapter?'))
  assert.ok(!href.includes('private'))
  assert.deepEqual(
    citationTarget(Object.fromEntries(new URL(href, 'http://localhost').searchParams)),
    target,
  )
  assert.equal(citationHref(source, 'another-book'), null)
})
