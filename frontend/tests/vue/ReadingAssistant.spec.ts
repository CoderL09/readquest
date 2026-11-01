import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReadingAssistant from '../../src/features/review/ReadingAssistant.vue'
const { get, post, put } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../../src/lib/api', () => ({ api: { get, post, put } }))
describe('ReadingAssistant', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    get.mockResolvedValue({ data: [{ id: 'book', title: 'Test Book' }] })
  })
  it('requires explicit confirmation before starting a session', async () => {
    const draft = {
      id: 'run',
      state: 'draft',
      result: { proposal: '<script>untrusted</script>' },
      events: [{ tool: 'getReviewWords', state: 'completed' }],
      session: null,
    }
    post.mockResolvedValue({ data: draft })
    put.mockResolvedValue({ data: { ...draft, state: 'confirmed', session: { id: 'session' } } })
    const wrapper = mount(ReadingAssistant)
    await wrapper.get('button').trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '生成复习建议')!
      .trigger('click')
    await flushPromises()
    expect(put).not.toHaveBeenCalled()
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>untrusted</script>')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '确认并进入待复习卡片')!
      .trigger('click')
    await flushPromises()
    expect(put).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('confirmed')).toHaveLength(1)
  })
  it('reuses the idempotency key after an uncertain network failure', async () => {
    post.mockRejectedValue(new Error('network unavailable'))
    const wrapper = mount(ReadingAssistant)
    await wrapper.get('button').trigger('click')
    await flushPromises()
    const generate = () => wrapper.findAll('button').find((b) => b.text() === '生成复习建议')!
    await generate().trigger('click')
    await flushPromises()
    await generate().trigger('click')
    await flushPromises()
    expect(post.mock.calls[0]![2].headers['Idempotency-Key']).toBe(
      post.mock.calls[1]![2].headers['Idempotency-Key'],
    )
  })
})
