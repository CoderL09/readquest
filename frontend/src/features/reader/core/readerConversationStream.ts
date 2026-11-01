import { apiBaseUrl, authenticatedFetch } from '../../../lib/api'
import { parseReaderStreamBuffer } from './readerInquiry'
import type { ReviewConceptResult } from '../../review/conceptDrafts'

export type ReaderConversationTurn = {
  role: 'user' | 'assistant'
  content: string
}

export type ReaderConversationMetrics = {
  firstTokenMs: number
  totalMs: number
}

export async function streamReaderConversation(input: {
  bookId: string
  sentence: string
  sentenceId?: string | null
  chapterId?: string | null
  sourceSentenceKey?: string | null
  question: string
  history: ReaderConversationTurn[]
  signal: AbortSignal
  onToken: (delta: string) => void
}): Promise<{
  answer: string
  metrics: ReaderConversationMetrics
  reviewConcepts: ReviewConceptResult | null
}> {
  const startedAt = performance.now()
  let firstTokenAt = 0
  let answer = ''
  let reviewConcepts: ReviewConceptResult | null = null
  const response = await authenticatedFetch(
    `${apiBaseUrl}/books/${input.bookId}/reading-conversations`,
    {
      method: 'POST',
      headers: { Accept: 'text/event-stream', 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sentence: input.sentence,
        chapterId: input.chapterId,
        sourceSentenceKey: input.sourceSentenceKey?.trim() || input.sentenceId?.trim() || undefined,
        question: input.question,
        history: input.history,
      }),
      signal: input.signal,
    },
  )

  if (!response.ok || !response.body) {
    throw new Error(`Reader conversation returned ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const parsed = parseReaderStreamBuffer(buffer)
      buffer = parsed.remainder

      for (const event of parsed.events) {
        if (event.type === 'error') throw new Error(event.message)
        if (event.type === 'review_concepts') {
          reviewConcepts = event.result
          continue
        }
        if (event.type !== 'token' || !event.delta) continue
        if (!firstTokenAt) firstTokenAt = performance.now()
        answer += event.delta
        input.onToken(event.delta)
      }
      if (done) break
    }
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }

  const finishedAt = performance.now()
  return {
    answer: answer.trim(),
    metrics: {
      firstTokenMs: Math.round((firstTokenAt || finishedAt) - startedAt),
      totalMs: Math.round(finishedAt - startedAt),
    },
    reviewConcepts,
  }
}
