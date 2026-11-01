export const ENTITLEMENT_REQUIRED_EVENT = 'readquest:entitlement-required'
export const AI_QUOTA_EXCEEDED_EVENT = 'readquest:ai-quota-exceeded'

export type EntitlementNotice = {
  message: string
  feature?: string
}

export type QuotaNotice = {
  message: string
}

type ErrorEnvelope = {
  error?: {
    code?: unknown
    message?: unknown
    feature?: unknown
  }
}

export function entitlementNoticeFromResponse(
  status: number | undefined,
  body: unknown,
): EntitlementNotice | null {
  if (status !== 402 || typeof body !== 'object' || body === null) return null
  const error = (body as ErrorEnvelope).error
  if (!error || error.code !== 'ENTITLEMENT_REQUIRED') return null

  const message =
    typeof error.message === 'string' && error.message.trim().length > 0
      ? error.message.trim().slice(0, 240)
      : 'This AI feature requires ReadQuest Pro.'
  const feature =
    typeof error.feature === 'string' && /^[a-z0-9_]{1,64}$/.test(error.feature)
      ? error.feature
      : undefined
  return feature ? { message, feature } : { message }
}

export function quotaNoticeFromResponse(
  status: number | undefined,
  body: unknown,
): QuotaNotice | null {
  if (status !== 429 || typeof body !== 'object' || body === null) return null
  const error = (body as ErrorEnvelope).error
  if (!error || error.code !== 'QUOTA_EXCEEDED') return null

  const message =
    typeof error.message === 'string' && error.message.trim().length > 0
      ? error.message.trim().slice(0, 240)
      : 'Your daily AI limit has been reached. Try again after the UTC-day reset.'
  return { message }
}
