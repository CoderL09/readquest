export type ImportKind = 'web-article' | 'direct-file-url' | 'catalog-offer'

export type ImportErrorCode =
  | 'INVALID_URL'
  | 'URL_NOT_PUBLIC'
  | 'URL_BLOCKED_HOST'
  | 'URL_REDIRECT_BLOCKED'
  | 'PAGE_REQUIRES_LOGIN'
  | 'PAGE_ACCESS_CHALLENGE'
  | 'PAGE_NOT_READABLE'
  | 'CONTENT_TOO_LARGE'
  | 'UNSUPPORTED_CONTENT_TYPE'
  | 'DOWNLOAD_NOT_AUTHORIZED'
  | 'SOURCE_TEMPORARILY_UNAVAILABLE'
  | 'IMPORT_ALREADY_EXISTS'
  | 'INSPECTION_EXPIRED'
  | 'RIGHTS_ATTESTATION_REQUIRED'
  | 'RATE_LIMITED'

export type ImportError = {
  code: ImportErrorCode | string
  message: string
  retryable: boolean
}

export type ImportInspection = {
  id: string
  kind: 'web-article' | 'direct-file-url'
  displayUrl: string
  originHost: string
  detectedFormat: 'html' | 'epub' | 'pdf' | 'txt'
  preview: {
    title: string
    byline?: string
    siteName?: string
    excerpt?: string
    wordCount?: number
    fileName?: string
    sizeBytes?: number
  }
  expiresAt: string
}

export type ImportJobStatus =
  'queued' | 'fetching' | 'extracting' | 'ingesting' | 'completed' | 'failed' | 'canceled'

export type ImportJob = {
  id: string
  kind: ImportKind
  status: ImportJobStatus
  progress: {
    phase: string
    percent: number
  }
  error: ImportError | null
  bookId: string | null
  firstStageId: string | null
  createdAt: string
  updatedAt: string
}

export type InspectUrlResponse = {
  inspection: ImportInspection
}

export type CreateImportResponse = {
  job: ImportJob
  deduplicated: boolean
}

export type ImportJobResponse = {
  job: ImportJob
}

export type CreateCatalogImportInput = {
  providerId: string
  externalId: string
  offerId: string
}
