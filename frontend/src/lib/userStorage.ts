type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

function scopedKey(userId: string, key: string): string {
  const logicalKey = key.startsWith('readquest.') ? key.slice('readquest.'.length) : key
  return `readquest.user.v1.${encodeURIComponent(userId)}.${logicalKey}`
}

export function createUserScopedStorage(
  storage: StorageLike,
  getUserId: () => string | null,
): StorageLike {
  return {
    getItem(key) {
      const userId = getUserId()
      return userId ? storage.getItem(scopedKey(userId, key)) : null
    },
    setItem(key, value) {
      const userId = getUserId()
      if (userId) storage.setItem(scopedKey(userId, key), value)
    },
    removeItem(key) {
      const userId = getUserId()
      if (userId) storage.removeItem(scopedKey(userId, key))
    },
  }
}

let activeUserId: string | null = null

export function setUserStorageScope(userId: string | null): void {
  activeUserId = userId
}

const browserStorage: StorageLike = {
  getItem: (key) => window.localStorage.getItem(key),
  setItem: (key, value) => window.localStorage.setItem(key, value),
  removeItem: (key) => window.localStorage.removeItem(key),
}

export const userStorage = createUserScopedStorage(browserStorage, () => activeUserId)

const browserSessionStorage: StorageLike = {
  getItem: (key) => window.sessionStorage.getItem(key),
  setItem: (key, value) => window.sessionStorage.setItem(key, value),
  removeItem: (key) => window.sessionStorage.removeItem(key),
}

export const userSessionStorage = createUserScopedStorage(browserSessionStorage, () => activeUserId)
