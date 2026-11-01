import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, it, expect } from 'vitest'
import ModalPanel from '../../src/components/ModalPanel.vue'
afterEach(() => {
  document.body.innerHTML = ''
})
describe('accessible modal', () => {
  it('moves focus into the dialog, closes with Escape, and restores focus', async () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const wrapper = mount(ModalPanel, {
      attachTo: document.body,
      props: { title: '阅读笔记' },
      slots: { default: '<input aria-label="感想" />' },
    })
    await flushPromises()
    const dialog = document.querySelector('[role=dialog]')!
    expect(dialog.getAttribute('aria-label')).toBe('阅读笔记')
    expect(dialog.contains(document.activeElement)).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
    expect(document.activeElement).toBe(opener)
  })
  it('keeps keyboard navigation inside the dialog', async () => {
    const wrapper = mount(ModalPanel, {
      attachTo: document.body,
      props: { title: '设置' },
      slots: { default: '<button id="last-action">保存</button>' },
    })
    await flushPromises()
    const close = document.querySelector<HTMLButtonElement>('[aria-label="关闭窗口"]')!
    close.focus()
    document.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }),
    )
    expect(document.activeElement?.id).toBe('last-action')
    document.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }),
    )
    expect(document.activeElement).toBe(close)
    wrapper.unmount()
  })
})
