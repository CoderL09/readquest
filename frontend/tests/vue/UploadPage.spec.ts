import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UploadPage from '../../src/pages/UploadPage.vue'
import { api } from '../../src/lib/api'
import { userStorage } from '../../src/lib/userStorage'

vi.mock('../../src/lib/api', () => ({ api: { get: vi.fn(), post: vi.fn() } }))
vi.mock('../../src/lib/userStorage', () => ({
  userStorage: { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('../../src/api/books', () => ({
  contents: vi.fn(async () => ({ chapters: [{ id: 'chapter' }] })),
}))
const job = {
  id: 'job-1',
  bookId: 'book',
  status: 'processing',
  phase: 'parsing',
  progress: 10,
  attempts: 1,
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(userStorage.getItem).mockReturnValue(JSON.stringify({ id: 'job-1', bookId: 'book' }))
})
const mountPage = () =>
  mount(UploadPage, {
    global: { stubs: { DictionaryManager: true, RouterLink: { template: '<a><slot/></a>' } } },
  })
describe('durable book import', () => {
  it('restores a server task and can cancel without deleting the source', async () => {
    let cancelled = false
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/books/book'
          ? { id: 'book', title: 'Test' }
          : { ...job, ...(cancelled ? { status: 'cancelled', phase: 'cancelled' } : {}) },
    }))
    vi.mocked(api.post).mockImplementation(async () => {
      cancelled = true
      return { data: { ...job, status: 'cancelled', phase: 'cancelled' } }
    })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('解析文件与目录')
    expect(api.post).not.toHaveBeenCalled()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '取消导入')!
      .trigger('click')
    await flushPromises()
    expect(api.post).toHaveBeenCalledWith(
      '/book-imports/job-1/cancellations',
      {},
      expect.anything(),
    )
    expect(wrapper.text()).toContain('已取消')
    expect(wrapper.text()).toContain('重试解析')
    wrapper.unmount()
  })
  it('exposes completed tasks as readable after refresh', async () => {
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/books/book'
          ? { id: 'book', title: 'Test' }
          : { ...job, status: 'completed', phase: 'completed', progress: 100 },
    }))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('准备完成')
    expect(wrapper.text()).toContain('打开阅读器')
    wrapper.unmount()
  })
})
