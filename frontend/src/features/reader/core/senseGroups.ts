export type SenseGroup = { text: string; start: number; end: number }
export type SentenceGroups = { groups: SenseGroup[]; source: 'model' | 'cache' }

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('意群结果无效，已保留原文。')
  return value as Record<string, unknown>
}

export function readSenseGroups(text: string, input: unknown): SentenceGroups {
  const result = object(input)
  if (result.status !== 'ready') {
    const messages: Record<string, string> = {
      provider_not_configured: '意群服务尚未配置，当前显示原文。',
      quota_exceeded: '本次意群分析额度不足，当前显示原文。',
      sentence_too_long: '本句较长，暂时保留原文。',
    }
    throw new Error(messages[String(result.reason)] ?? '暂时无法划分意群，已保留原文，可以重试。')
  }
  if (result.source !== 'model' && result.source !== 'cache')
    throw new Error('意群结果来源无效，已保留原文。')
  if (!Array.isArray(result.sentences) || result.sentences.length !== 1)
    throw new Error('意群结果不匹配，已保留原文。')
  const sentence = object(result.sentences[0])
  if (
    sentence.text !== text ||
    !Array.isArray(sentence.chunks) ||
    !sentence.chunks.length ||
    sentence.chunks.length > 200
  )
    throw new Error('意群结果不匹配，已保留原文。')
  let cursor = 0
  const groups = sentence.chunks.map((value) => {
    const group = object(value)
    const { start, end } = group
    if (
      typeof start !== 'number' ||
      typeof end !== 'number' ||
      !Number.isSafeInteger(start) ||
      !Number.isSafeInteger(end) ||
      start < cursor ||
      end <= start ||
      end > text.length ||
      text.slice(cursor, start).trim() ||
      typeof group.text !== 'string' ||
      text.slice(start, end) !== group.text
    )
      throw new Error('意群未能完整保留原文，已恢复原句。')
    cursor = end
    return { start, end, text: group.text }
  })
  if (text.slice(cursor).trim()) throw new Error('意群遗漏了原文，已恢复原句。')
  return { groups, source: result.source }
}

export function senseGroupPieces(text: string, groups: SenseGroup[]) {
  const pieces: Array<{ text: string; kind: 'group' | 'gap'; start: number }> = []
  let cursor = 0
  for (const group of groups) {
    if (group.start > cursor)
      pieces.push({ kind: 'gap', text: text.slice(cursor, group.start), start: cursor })
    pieces.push({ kind: 'group', text: group.text, start: group.start })
    cursor = group.end
  }
  if (cursor < text.length) pieces.push({ kind: 'gap', text: text.slice(cursor), start: cursor })
  return pieces
}
