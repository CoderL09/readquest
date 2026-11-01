const DEFAULT_API_BASE_URL = '/api'

function withoutTrailingSlashes(value: string): string {
  return value.replace(/\/+$/, '')
}

export function resolveApiBaseUrl(configuredApiUrl: string | undefined): string {
  const configured = configuredApiUrl?.trim()
  return configured ? withoutTrailingSlashes(configured) : DEFAULT_API_BASE_URL
}
