<script setup lang="ts">
import type { Book } from '../lib/api'
import { mediaUrl } from '../lib/api'
defineProps<{ book: Book }>()
defineEmits<{ open: [book: Book] }>()
const states = { saved: '想读', reading: '在读', completed: '已读' }
</script>
<template>
  <button class="book-card" @click="$emit('open', book)">
    <div
      class="book-cover"
      :style="{ background: `hsl(${(book.title.charCodeAt(0) * 17) % 360} 20% 38%)` }"
    >
      <img
        v-if="book.coverUrl"
        :src="mediaUrl(book.coverUrl)"
        :alt="`${book.title} 封面`"
        loading="lazy"
        @error="($event.target as HTMLImageElement).style.display = 'none'"
      /><span>{{ book.title }}</span
      ><span class="book-open-hint">打开这本书 ↗</span>
    </div>
    <strong>{{ book.title }}</strong
    ><small>{{ book.author || '作者未知' }}</small>
    <span class="book-state" :class="{ ready: book.status === 'completed' }">{{
      book.status === 'completed'
        ? book.shelfState
          ? states[book.shelfState]
          : '可开始阅读'
        : book.status === 'failed'
          ? '解析失败'
          : `准备中 ${Math.round(book.progress || 0)}%`
    }}</span>
  </button>
</template>
