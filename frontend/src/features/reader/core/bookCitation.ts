export type BookCitation = {
  citation: number
  bookId: string
  stageId: string
  stageTitle: string
  content: string
  startOffset: number
  endOffset: number
  chapterHash: string
}

export type CitationTarget = Pick<BookCitation, 'startOffset' | 'endOffset' | 'chapterHash'>

export function citationTarget(query: Record<string, unknown>): CitationTarget | null {
  if (typeof query.sourceStart !== 'string' || typeof query.sourceEnd !== 'string') return null
  const startOffset = Number(query.sourceStart)
  const endOffset = Number(query.sourceEnd)
  if (
    !Number.isSafeInteger(startOffset) ||
    startOffset < 0 ||
    !Number.isSafeInteger(endOffset) ||
    endOffset <= startOffset ||
    endOffset - startOffset > 5000 ||
    typeof query.sourceHash !== 'string' ||
    !/^[a-f0-9]{64}$/.test(query.sourceHash)
  )
    return null
  return { startOffset, endOffset, chapterHash: query.sourceHash }
}

export function citationHref(source: BookCitation, bookId: string): string | null {
  const query = {
    sourceStart: String(source.startOffset),
    sourceEnd: String(source.endOffset),
    sourceHash: source.chapterHash,
  }
  if (source.bookId !== bookId || !source.stageId || !citationTarget(query)) return null
  return `/app/reader/${encodeURIComponent(source.stageId)}?${new URLSearchParams({ bookId, ...query })}`
}

export async function resolveCitation(
  text: string,
  paragraphs: Array<{ text: string }>,
  target: CitationTarget,
) {
  if (target.endOffset > text.length) throw new Error('引用位置已失效，请重新提问获取来源。')
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  const hash = Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('')
  if (hash !== target.chapterHash) throw new Error('章节内容已变化，请重新提问获取来源。')
  let cursor = 0
  const ranges = paragraphs.map((paragraph) => {
    const start = text.indexOf(paragraph.text, cursor)
    if (start < 0) throw new Error('当前排版无法定位引用，请查看参考原文。')
    cursor = start + paragraph.text.length
    const from = Math.max(start, target.startOffset)
    const to = Math.min(cursor, target.endOffset)
    return from < to ? { start: from - start, end: to - start } : null
  })
  const paragraphIndex = ranges.findIndex(Boolean)
  if (paragraphIndex < 0) throw new Error('未找到引用段落，请重新提问获取来源。')
  return { ranges, paragraphIndex, quote: text.slice(target.startOffset, target.endOffset) }
}
