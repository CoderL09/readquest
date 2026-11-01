import { defineStore } from 'pinia'
import { ref } from 'vue'
export const useNoticeStore = defineStore('notices', () => {
  const messages = ref<Array<{ id: number; text: string }>>([])
  let sequence = 0
  function dismiss(id: number) {
    messages.value = messages.value.filter((message) => message.id !== id)
  }
  function notify(text: string) {
    const id = ++sequence
    messages.value.push({ id, text })
    setTimeout(() => dismiss(id), 7000)
  }
  return { messages, notify, dismiss }
})
