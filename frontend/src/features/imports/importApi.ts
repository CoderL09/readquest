import axios from 'axios'
import { api } from '../../lib/api'
import type {
  CreateCatalogImportInput,
  CreateImportResponse,
  ImportError,
  ImportInspection,
  ImportJob,
  ImportJobResponse,
  InspectUrlResponse,
} from './contracts'
import { importErrorMessage } from './importState'

const MAX_URL_LENGTH = 4_096
const MAX_TITLE_LENGTH = 300
const MAX_IDENTIFIER_LENGTH = 256

function requiredValue(value: string, label: string, maxLength: number): string {
  const normalized = value.trim()
  if (!normalized) throw new Error(`${label} is required`)
  if (normalized.length > maxLength) throw new Error(`${label} is too long`)
  return normalized
}

export async function inspectImportUrl(
  url: string,
  signal?: AbortSignal,
): Promise<ImportInspection> {
  const normalizedUrl = requiredValue(url, 'URL', MAX_URL_LENGTH)
  return (
    await api.post<InspectUrlResponse>('/imports/url/inspect', { url: normalizedUrl }, { signal })
  ).data.inspection
}

export async function createUrlImport(
  input: { url: string; title?: string; rightsAttested: boolean },
  signal?: AbortSignal,
): Promise<CreateImportResponse> {
  if (input.rightsAttested !== true) throw new Error('Rights attestation is required')
  const url = requiredValue(input.url, 'URL', MAX_URL_LENGTH)
  const requestedTitle = input.title?.trim()
  const title = requestedTitle
    ? requiredValue(requestedTitle, 'Title', MAX_TITLE_LENGTH)
    : undefined
  return (
    await api.post<CreateImportResponse>(
      '/imports/url',
      title ? { url, title, rightsAttested: true } : { url, rightsAttested: true },
      { signal },
    )
  ).data
}

export async function createCatalogImport(
  input: CreateCatalogImportInput,
  signal?: AbortSignal,
): Promise<CreateImportResponse> {
  const payload = {
    providerId: requiredValue(input.providerId, 'Provider ID', MAX_IDENTIFIER_LENGTH),
    externalId: requiredValue(input.externalId, 'External ID', MAX_IDENTIFIER_LENGTH),
    offerId: requiredValue(input.offerId, 'Offer ID', MAX_IDENTIFIER_LENGTH),
  }
  return (await api.post<CreateImportResponse>('/imports/catalog', payload, { signal })).data
}

export async function getImportJob(id: string, signal?: AbortSignal): Promise<ImportJob> {
  const normalizedId = requiredValue(id, 'Import ID', MAX_IDENTIFIER_LENGTH)
  return (
    await api.get<ImportJobResponse>(`/imports/${encodeURIComponent(normalizedId)}`, { signal })
  ).data.job
}

type ImportApiErrorBody = {
  error?: string | (Partial<ImportError> & { message?: string })
}

export function importApiErrorMessage(error: unknown, fallback: string): string {
  if (!axios.isAxiosError<ImportApiErrorBody>(error)) {
    return error instanceof Error && error.message.endsWith('is required')
      ? error.message
      : fallback
  }
  const apiError = error.response?.data?.error
  if (apiError && typeof apiError === 'object' && typeof apiError.code === 'string') {
    return importErrorMessage({
      code: apiError.code,
      message: typeof apiError.message === 'string' ? apiError.message : '',
      retryable: apiError.retryable === true,
    })
  }
  if (typeof apiError === 'string' && apiError.trim()) return apiError
  if (!error.response && typeof navigator !== 'undefined' && navigator.onLine === false) {
    return 'You appear to be offline. Reconnect and try again.'
  }
  return fallback
}
