import type { ReviewConceptResult } from '../../review/conceptDrafts'

export const MAX_SENTENCE_QUERIES_PER_PAGE = 10

const WORD_PATTERN = /[\p{L}\p{M}]+(?:['’-][\p{L}\p{M}]+)*/gu
const WORD_TOKEN_PATTERN = /^[\p{L}\p{M}]+(?:['’-][\p{L}\p{M}]+)*$/u
const TOKEN_PATTERN = /[\p{L}\p{M}]+(?:['’-][\p{L}\p{M}]+)*|[^\p{L}\p{M}]/gu

export type WordToken = {
  text: string
  normalized: string
  start: number
  end: number
}

export type LookupTarget = {
  text: string
  normalized: string
  start: number
  end: number
}

export type ReaderStreamEvent =
  | { type: 'token'; delta: string }
  | {
      type: 'review_concepts'
      result: ReviewConceptResult
    }
  | { type: 'done' }
  | { type: 'error'; message: string }

export function normalizeLookupWord(value: string): string {
  const match = value.normalize('NFKC').match(WORD_PATTERN)
  return match?.length === 1 ? match[0].toLocaleLowerCase() : ''
}

export function createLookupTarget(
  selectedText: string,
  rawSentenceText: string,
  rawSelectionStart: number,
): LookupTarget | undefined {
  const text = selectedText.trim()
  const normalized = normalizeLookupWord(text)
  if (!normalized || !Number.isInteger(rawSelectionStart)) return undefined

  const leadingSentenceWhitespace = rawSentenceText.length - rawSentenceText.trimStart().length
  const leadingSelectionWhitespace = selectedText.length - selectedText.trimStart().length
  const sentence = rawSentenceText.trim()
  const start = rawSelectionStart + leadingSelectionWhitespace - leadingSentenceWhitespace
  const end = start + text.length
  if (start < 0 || end > sentence.length) return undefined
  if (normalizeLookupWord(sentence.slice(start, end)) !== normalized) return undefined

  return { text, normalized, start, end }
}

export function plainReaderText(value: string): string {
  return value
    .replace(/\*\*/g, '')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/`/g, '')
}

export function isSingleWordLookup(value: string): boolean {
  const normalized = value.normalize('NFKC').trim()
  const match = normalized.match(WORD_PATTERN)
  return Boolean(
    match?.length === 1 &&
    normalizeLookupWord(normalized) &&
    normalized.replace(WORD_PATTERN, '').match(/^[\s“”"'‘’.,!?;:()[\]{}-]*$/),
  )
}

export function getWordAtOffset(
  text: string,
  offset: number,
): { word: string; start: number; end: number } | null {
  for (const match of text.matchAll(WORD_PATTERN)) {
    const start = match.index
    const end = start + match[0].length
    if (offset >= start && offset < end) return { word: match[0], start, end }
  }
  return null
}

export function getWordSelectionAtPoint(root: HTMLElement, x: number, y: number) {
  const doc = root.ownerDocument
  const caret = doc.caretPositionFromPoint?.(x, y)
  const fallback = doc.caretRangeFromPoint?.(x, y)
  const node = caret?.offsetNode ?? fallback?.startContainer
  const offset = caret?.offset ?? fallback?.startOffset
  if (!(node instanceof Text) || typeof offset !== 'number' || !root.contains(node)) return null

  const match = getWordAtOffset(node.data, offset)
  if (!match) return null
  const range = doc.createRange()
  range.setStart(node, match.start)
  range.setEnd(node, match.end)
  return {
    word: match.word,
    rect: range.getBoundingClientRect(),
    node,
    startOffset: match.start,
    endOffset: match.end,
  }
}

export function splitWordTokens(text: string): WordToken[] {
  return [...text.matchAll(TOKEN_PATTERN)].map((match) => {
    const normalized = WORD_TOKEN_PATTERN.test(match[0]) ? normalizeLookupWord(match[0]) : ''
    return {
      text: match[0],
      normalized,
      start: match.index,
      end: match.index + match[0].length,
    }
  })
}

function normalizeSentenceKey(sentence: string) {
  return sentence.normalize('NFKC').trim().replace(/\s+/g, ' ').toLocaleLowerCase()
}

export function registerSentenceQuery(
  keys: readonly string[],
  sentence: string,
  limit = MAX_SENTENCE_QUERIES_PER_PAGE,
) {
  const key = normalizeSentenceKey(sentence)
  if (!key) return { allowed: false, isNew: false, keys: [...keys] }
  if (keys.includes(key)) return { allowed: true, isNew: false, keys: [...keys] }
  if (keys.length >= limit) return { allowed: false, isNew: false, keys: [...keys] }
  return { allowed: true, isNew: true, keys: [...keys, key] }
}

export function parseReaderStreamBuffer(buffer: string): {
  events: ReaderStreamEvent[]
  remainder: string
} {
  const blocks = buffer.split('\n\n')
  const remainder = blocks.pop() ?? ''
  const events = blocks.flatMap((block) => {
    const data = block
      .split('\n')
      .find((line) => line.startsWith('data:'))
      ?.slice(5)
      .trim()
    if (!data) return []
    try {
      return [JSON.parse(data) as ReaderStreamEvent]
    } catch {
      return []
    }
  })
  return { events, remainder }
}
