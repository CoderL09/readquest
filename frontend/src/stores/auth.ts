import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  api,
  AUTH_REFRESHED_EVENT,
  beginAuthenticationTransition,
  commitAuthenticationTransition,
  rollbackAuthenticationTransition,
  waitForPendingAuthRefresh,
  refreshAccessToken,
  type AuthResponse,
  type AuthUser,
} from '../lib/api'
import { setUserStorageScope } from '../lib/userStorage'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const ready = ref(false)
  const authenticated = computed(() => user.value !== null)
  let initialization: Promise<void> | null = null
  function apply(next: AuthUser | null) {
    user.value = next
    setUserStorageScope(next?.id ?? null)
  }
  window.addEventListener('readquest:auth-expired', () => apply(null))
  window.addEventListener(AUTH_REFRESHED_EVENT, (event) => {
    const response = (event as CustomEvent<AuthResponse>).detail
    if (response?.user) apply(response.user)
  })
  function initialize() {
    return (initialization ??= (async () => {
      try {
        const response = await refreshAccessToken()
        apply(response?.user ?? null)
      } catch {
        apply(null)
      } finally {
        ready.value = true
      }
    })())
  }
  async function transition<T>(
    request: () => Promise<T>,
    response: (value: T) => AuthResponse | null,
  ) {
    const operation = beginAuthenticationTransition()
    await waitForPendingAuthRefresh()
    try {
      const value = await request()
      const auth = response(value)
      if (!commitAuthenticationTransition(operation, auth))
        throw new Error('登录状态已变化，请重试')
      apply(auth?.user ?? null)
      return value
    } catch (error) {
      rollbackAuthenticationTransition(operation)
      throw error
    }
  }
  async function login(email: string, password: string) {
    await transition(
      async () => (await api.post<AuthResponse>('/auth/login', { email, password })).data,
      (value) => value,
    )
  }
  async function register(email: string, password: string, displayName: string) {
    await transition(
      async () =>
        (await api.post<AuthResponse>('/auth/register', { email, password, displayName })).data,
      (value) => value,
    )
  }
  async function logout() {
    await transition(
      async () => {
        await api.post('/auth/logout')
      },
      () => null,
    )
  }
  async function deleted() {
    await transition(
      async () => undefined,
      () => null,
    )
  }
  function update(next: AuthUser) {
    if (user.value?.id === next.id) apply(next)
  }
  return { user, ready, authenticated, initialize, login, register, logout, deleted, update }
})
