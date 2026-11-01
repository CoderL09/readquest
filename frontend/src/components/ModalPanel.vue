<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLElement | null>(null)
let previous: HTMLElement | null = null
function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
  }
  if (event.key === 'Tab') {
    const elements = dialog.value?.querySelectorAll<HTMLElement>(
      'button:not(:disabled),a[href],input,select,textarea,[tabindex="0"]',
    )
    if (!elements?.length) {
      event.preventDefault()
      return
    }
    const first = elements[0],
      last = elements[elements.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }
}
onMounted(async () => {
  previous = document.activeElement as HTMLElement
  document.addEventListener('keydown', keydown)
  await nextTick()
  dialog.value?.querySelector<HTMLElement>('button')?.focus()
})
onUnmounted(() => {
  document.removeEventListener('keydown', keydown)
  previous?.focus()
})
</script>
<template>
  <Teleport to="body"
    ><div class="modal-backdrop" @click.self="$emit('close')">
      <section ref="dialog" role="dialog" aria-modal="true" :aria-label="title" class="modal">
        <header class="row">
          <h2>{{ title }}</h2>
          <button class="icon-button" aria-label="关闭窗口" @click="$emit('close')">×</button>
        </header>
        <slot />
      </section></div
  ></Teleport>
</template>
