<script setup lang="ts">
import { onMounted, ref, onScopeDispose } from 'vue'
import { api } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
const dictionary = ref<{ id: string; displayName: string; originalFileName?: string } | null>(null),
  error = ref(''),
  busy = ref(false),
  file = ref<File | null>(null)
const controller = new AbortController()
onScopeDispose(() => controller.abort())
async function load() {
  try {
    dictionary.value = (
      await api.get('/dictionaries/current', { signal: controller.signal })
    ).data.dictionary
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  }
}
onMounted(load)
function choose(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
}
async function upload() {
  if (!file.value || busy.value) return
  if (!file.value.name.toLowerCase().endsWith('.mdx') || file.value.size > 80 * 1024 * 1024) {
    error.value = '请选择 80 MB 以内的 MDX 词典。'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const data = new FormData()
    data.append('file', file.value)
    await api.post('/dictionaries', data, { signal: controller.signal })
    await load()
    file.value = null
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
async function remove() {
  if (!confirm('确定移除个人词典吗？')) return
  busy.value = true
  try {
    await api.delete('/dictionaries/current', { signal: controller.signal })
    dictionary.value = null
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="panel stack">
    <div>
      <h2>个人 MDX 词典</h2>
      <p class="muted">仅当前账号使用，用自己的词典理解原文。</p>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="dictionary" class="row">
      <span>{{ dictionary.displayName || dictionary.originalFileName }}</span
      ><button class="button secondary small" :disabled="busy" @click="remove">移除词典</button>
    </div>
    <form @submit.prevent="upload">
      <label
        >选择 MDX 文件（最大 80 MB）<input
          type="file"
          accept=".mdx"
          :disabled="busy"
          @change="choose" /></label
      ><button class="button secondary" :disabled="busy || !file">
        {{ busy ? '正在处理…' : dictionary ? '替换词典' : '上传词典' }}
      </button>
    </form>
  </section>
</template>
