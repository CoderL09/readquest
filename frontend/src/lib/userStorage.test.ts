import assert from 'node:assert/strict'
import test from 'node:test'
import { createUserScopedStorage } from './userStorage.js'

class MemoryStorage {
  private readonly values = new Map<string, string>()

  getItem(key: string) {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string) {
    this.values.set(key, value)
  }

  removeItem(key: string) {
    this.values.delete(key)
  }
}

test('learning-state storage keeps the same key isolated between users', () => {
  const storage = new MemoryStorage()
  let userId: string | null = 'reader-a'
  const scoped = createUserScopedStorage(storage, () => userId)

  scoped.setItem('readquest.reader.page.book-1.chapter-1', '12')
  userId = 'reader-b'
  assert.equal(scoped.getItem('readquest.reader.page.book-1.chapter-1'), null)

  scoped.setItem('readquest.reader.page.book-1.chapter-1', '3')
  userId = 'reader-a'
  assert.equal(scoped.getItem('readquest.reader.page.book-1.chapter-1'), '12')

  userId = null
  assert.equal(scoped.getItem('readquest.reader.page.book-1.chapter-1'), null)
  userId = 'reader-a'
  assert.equal(scoped.getItem('readquest.reader.page.book-1.chapter-1'), '12')
})

test('learning-state storage is unavailable without an authenticated user', () => {
  const storage = new MemoryStorage()
  let userId: string | null = null
  const scoped = createUserScopedStorage(storage, () => userId)

  scoped.setItem('readquest.bookId', 'book-1')
  assert.equal(scoped.getItem('readquest.bookId'), null)

  userId = 'reader-a'
  assert.equal(scoped.getItem('readquest.bookId'), null)
})

test('legacy unscoped keys stay invisible and removal affects only the active user', () => {
  const storage = new MemoryStorage()
  storage.setItem('readquest.bookId', 'legacy-demo-book')
  let userId: string | null = 'reader-a'
  const scoped = createUserScopedStorage(storage, () => userId)

  assert.equal(scoped.getItem('readquest.bookId'), null)
  scoped.setItem('readquest.bookId', 'book-a')
  userId = 'reader-b'
  scoped.setItem('readquest.bookId', 'book-b')
  scoped.removeItem('readquest.bookId')

  assert.equal(scoped.getItem('readquest.bookId'), null)
  userId = 'reader-a'
  assert.equal(scoped.getItem('readquest.bookId'), 'book-a')
})
