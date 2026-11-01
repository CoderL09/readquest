import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import CatalogExplorer from '../../src/features/catalog/CatalogExplorer.vue'
import { api } from '../../src/lib/api'
vi.mock('../../src/lib/api', () => ({ api: { get: vi.fn() } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.get).mockImplementation(async (url) => ({
    data:
      url === '/catalog/categories' ? { categories: [] } : { items: [], nextCursor: 'cursor-one' },
  }))
})
const render = () => mount(CatalogExplorer, { global: { stubs: { RouterLink: true } } })
describe('catalog request contracts', () => {
  it('uses the supported Open Library provider for trending regardless of search provider', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.findAll('select')[0]!.setValue('googlebooks')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('本周热门'))!
      .trigger('click')
    await flushPromises()
    const call = vi.mocked(api.get).mock.calls.find(([url]) => url === '/catalog/trending')!
    expect(call[1]?.params.provider).toBe('openlibrary')
    wrapper.unmount()
  })
  it('invalidates a search cursor when the query changes', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('input').setValue('Alice')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('加载更多书目')
    await wrapper.get('input').setValue('River')
    expect(wrapper.text()).not.toContain('加载更多书目')
    wrapper.unmount()
  })
})
