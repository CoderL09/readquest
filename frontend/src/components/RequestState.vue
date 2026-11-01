<script setup lang="ts">
defineProps<{ loading?: boolean; error?: string; empty?: boolean; message?: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <p v-if="loading" class="state" role="status">正在载入…</p>
  <div v-else-if="error" class="state error" role="alert">
    <p>{{ error }}</p>
    <button class="button secondary" @click="$emit('retry')">重新载入</button>
  </div>
  <div v-else-if="empty" class="state">
    <p>{{ message || '这里还没有内容。' }}</p>
    <slot name="empty" />
  </div>
  <slot v-else />
</template>
