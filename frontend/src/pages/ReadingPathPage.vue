<script setup lang="ts">
import { onMounted, ref, onScopeDispose } from 'vue'
import { useRouter } from 'vue-router'
import { catalog, contents } from '../api/books'
import { userStorage } from '../lib/userStorage'
import { errorMessage } from '../lib/errors'
const router = useRouter(),
  error = ref('')
const controller = new AbortController()
onScopeDispose(() => controller.abort())
onMounted(async () => {
  try {
    let id = userStorage.getItem('readquest.bookId')
    if (!id)
      id =
        (await catalog(controller.signal)).find((book) => book.status === 'completed')?.id ?? null
    if (!id) {
      await router.replace('/app/library')
      return
    }
    const value = await contents(id, controller.signal),
      saved = userStorage.getItem('readquest.stageId'),
      chapter = value.chapters.find((item) => item.id === saved) ?? value.chapters[0]
    if (!chapter) throw new Error('这本书还没有准备好。')
    if (!controller.signal.aborted)
      await router.replace({ path: `/app/reader/${chapter.id}`, query: { bookId: id } })
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  }
})
</script>
<template>
  <div class="page narrow state">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else role="status">正在翻开下一页…</p>
    <RouterLink v-if="error" class="button" to="/app/library">回到书架</RouterLink>
  </div>
</template>
