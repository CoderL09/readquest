<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useNoticeStore } from '../stores/notices'
const notices = useNoticeStore()
const quota = () => notices.notify('今日 AI 额度已用完，请稍后再试。')
const pro = () => notices.notify('此功能需要会员，可在会员页面获取并兑换激活码。')
onMounted(() => {
  window.addEventListener('readquest:ai-quota-exceeded', quota)
  window.addEventListener('readquest:entitlement-required', pro)
})
onUnmounted(() => {
  window.removeEventListener('readquest:ai-quota-exceeded', quota)
  window.removeEventListener('readquest:entitlement-required', pro)
})
</script>
<template>
  <aside class="notices" aria-live="polite">
    <div v-for="message in notices.messages" :key="message.id" class="notice">
      <span>{{ message.text }}</span
      ><button aria-label="关闭通知" @click="notices.dismiss(message.id)">×</button>
    </div>
  </aside>
</template>
