import { ref, shallowRef, onScopeDispose } from 'vue'
import { errorMessage } from '../lib/errors'
export function useRequest<T>() {
  const data = shallowRef<T | null>(null),
    error = ref(''),
    loading = ref(false)
  let controller: AbortController | null = null
  async function execute(action: (signal: AbortSignal) => Promise<T>) {
    controller?.abort()
    const request = new AbortController()
    controller = request
    loading.value = true
    error.value = ''
    try {
      const value = await action(request.signal)
      if (!request.signal.aborted) data.value = value
      return value
    } catch (failure) {
      if (!request.signal.aborted) error.value = errorMessage(failure)
      return undefined
    } finally {
      if (!request.signal.aborted) loading.value = false
    }
  }
  onScopeDispose(() => controller?.abort())
  return { data, error, loading, execute }
}
