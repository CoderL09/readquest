<script setup lang="ts">
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import NoticeHost from './components/NoticeHost.vue'
const route = useRoute()
const auth = useAuthStore()
const router = useRouter()
watch(
  () => auth.authenticated,
  (authenticated) => {
    if (auth.ready && !authenticated && route.meta.requiresAuth)
      void router.replace({ path: '/login', query: { redirect: route.fullPath } })
  },
)
</script>
<template>
  <RouterView :key="`${auth.user?.id ?? 'guest'}:${route.fullPath}`" /><NoticeHost />
</template>
