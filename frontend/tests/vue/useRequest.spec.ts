import { effectScope } from 'vue'
import { describe, it, expect } from 'vitest'
import { useRequest } from '../../src/composables/useRequest'
describe('request lifetime', () => {
  it('aborts the previous request and ignores its late result', async () => {
    const scope = effectScope()
    const request = scope.run(() => useRequest<string>())!
    let firstSignal: AbortSignal | undefined
    let resolveFirst!: (value: string) => void
    const first = request.execute((signal) => {
      firstSignal = signal
      return new Promise((resolve) => {
        resolveFirst = resolve
      })
    })
    await request.execute(async () => 'latest')
    resolveFirst('stale')
    await first
    expect(firstSignal?.aborted).toBe(true)
    expect(request.data.value).toBe('latest')
    expect(request.loading.value).toBe(false)
    scope.stop()
  })
  it('does not publish an aborted request error after unmount', async () => {
    const scope = effectScope()
    const request = scope.run(() => useRequest<string>())!
    let reject!: (error: Error) => void
    const pending = request.execute(
      () =>
        new Promise((_, fail) => {
          reject = fail
        }),
    )
    scope.stop()
    reject(new Error('late failure'))
    await pending
    expect(request.error.value).toBe('')
  })
})
