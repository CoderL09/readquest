import assert from 'node:assert/strict'
import {
  MAX_SENTENCE_QUERIES_PER_PAGE,
  createLookupTarget,
  getWordAtOffset,
  isSingleWordLookup,
  normalizeLookupWord,
  parseReaderStreamBuffer,
  plainReaderText,
  registerSentenceQuery,
  splitWordTokens,
} from './readerInquiry'

assert.equal(normalizeLookupWord('“Purports,”'), 'purports')
assert.equal(isSingleWordLookup('purports'), true)
assert.equal(isSingleWordLookup('purports to be'), false)
assert.equal(getWordAtOffset('an old friend', 4)?.word, 'old')
assert.equal(getWordAtOffset('old friend', 3), null)
assert.deepEqual(createLookupTarget('interested', '  In the corner ran an interested eye.  ', 23), {
  text: 'interested',
  normalized: 'interested',
  start: 21,
  end: 31,
})
assert.equal(createLookupTarget('interested', 'In the corner ran an interested eye.', 4), undefined)
assert.equal(plainReaderText('**重点**\n### 原因\n`word`'), '重点\n原因\nword')

assert.deepEqual(splitWordTokens('He purports to know.'), [
  { text: 'He', normalized: 'he', start: 0, end: 2 },
  { text: ' ', normalized: '', start: 2, end: 3 },
  { text: 'purports', normalized: 'purports', start: 3, end: 11 },
  { text: ' ', normalized: '', start: 11, end: 12 },
  { text: 'to', normalized: 'to', start: 12, end: 14 },
  { text: ' ', normalized: '', start: 14, end: 15 },
  { text: 'know', normalized: 'know', start: 15, end: 19 },
  { text: '.', normalized: '', start: 19, end: 20 },
])

let sentenceKeys: string[] = []
for (let index = 0; index < MAX_SENTENCE_QUERIES_PER_PAGE; index += 1) {
  const result = registerSentenceQuery(sentenceKeys, `Sentence ${index + 1}.`)
  assert.equal(result.allowed, true)
  sentenceKeys = result.keys
}
assert.equal(registerSentenceQuery(sentenceKeys, 'Sentence 11.').allowed, false)
assert.equal(registerSentenceQuery(sentenceKeys, 'Sentence 1.').allowed, true)

const parsed = parseReaderStreamBuffer(
  'data: {"type":"token","delta":"你"}\n\ndata: {"type":"done"}\n\ndata: {"type"',
)
assert.deepEqual(parsed.events, [{ type: 'token', delta: '你' }, { type: 'done' }])
assert.equal(parsed.remainder, 'data: {"type"')

const conceptEvent = parseReaderStreamBuffer(
  'data: {"type":"review_concepts","result":{"status":"suggested","retryable":false,"concepts":[]}}\n\n',
)
assert.equal(conceptEvent.events[0]?.type, 'review_concepts')

console.log('reader inquiry tests passed')
