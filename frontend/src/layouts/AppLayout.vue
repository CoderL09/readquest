<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Library,
  NotebookPen,
  Users,
  Search,
  UserRound,
  LogOut,
  CalendarCheck,
  BookMarked,
  Plus,
  ChartNoAxesCombined,
  Settings,
  Crown,
} from '@lucide/vue'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../lib/errors'
import { useNoticeStore } from '../stores/notices'
const auth = useAuthStore(),
  router = useRouter(),
  route = useRoute(),
  notices = useNoticeStore(),
  menu = ref(false)
const links = [
  { path: '/app', label: '发现', icon: Search },
  { path: '/app/library', label: '我的书架', icon: Library },
  { path: '/app/vocabulary', label: '生词本', icon: BookMarked },
  { path: '/app/notes', label: '笔记', icon: NotebookPen },
  { path: '/app/groups', label: '读书小组', icon: Users },
  { path: '/app/review', label: '今日复习', icon: CalendarCheck },
]
function active(path: string) {
  if (path === '/app/library' && route.path.startsWith('/app/reader/')) return true
  return path === '/app'
    ? route.path === path
    : route.path === path || route.path.startsWith(path + '/')
}
watch(
  () => route.fullPath,
  () => {
    menu.value = false
  },
)
async function logout() {
  try {
    await auth.logout()
    await router.push('/login')
  } catch (error) {
    notices.notify(errorMessage(error))
  }
}
</script>
<template>
  <div class="app-shell" @keydown.esc="menu = false">
    <a href="#main-content" class="skip-link">跳到正文</a>
    <aside class="workspace-sidebar" aria-label="应用侧栏">
      <RouterLink to="/app" class="workspace-brand" aria-label="ReadQuest 首页"
        >Read<span>Quest</span></RouterLink
      >
      <nav class="workspace-nav" aria-label="主要导航">
        <RouterLink
          v-for="link in links"
          :key="link.path"
          :to="link.path"
          :class="{ active: active(link.path) }"
          :aria-current="active(link.path) ? 'page' : undefined"
        >
          <component :is="link.icon" :size="21" :stroke-width="1.7" /><span>{{ link.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-secondary">
        <RouterLink to="/app/sources"><Plus :size="18" />添加阅读</RouterLink>
        <RouterLink to="/app/journey"><ChartNoAxesCombined :size="18" />阅读成长</RouterLink>
        <RouterLink to="/app/billing"><Crown :size="18" />会员与激活</RouterLink>
      </div>
      <div class="sidebar-account">
        <button
          class="account-trigger"
          aria-label="打开账号菜单"
          :aria-expanded="menu"
          @click="menu = !menu"
        >
          <span class="avatar">{{ auth.user?.displayName?.slice(0, 1) || 'R' }}</span>
          <span class="account-name">{{ auth.user?.displayName || '阅读者' }}</span
          ><Settings :size="18" />
        </button>
        <div v-if="menu" class="account-menu">
          <RouterLink to="/app/profile"><UserRound :size="17" />个人资料</RouterLink>
          <RouterLink to="/app/billing"><Crown :size="17" />会员与激活</RouterLink>
          <button @click="logout"><LogOut :size="17" />退出登录</button>
        </div>
      </div>
    </aside>
    <header class="workspace-mobile-header">
      <RouterLink to="/app" class="workspace-brand">Read<span>Quest</span></RouterLink>
      <div class="mobile-account">
        <button
          class="icon-button"
          aria-label="打开账号菜单"
          :aria-expanded="menu"
          @click="menu = !menu"
        >
          <UserRound :size="20" />
        </button>
        <div v-if="menu" class="account-menu">
          <RouterLink to="/app/profile"><UserRound :size="17" />个人资料</RouterLink>
          <RouterLink to="/app/sources"><Plus :size="17" />添加阅读</RouterLink>
          <RouterLink to="/app/journey"><ChartNoAxesCombined :size="17" />阅读成长</RouterLink>
          <RouterLink to="/app/billing"><Crown :size="17" />会员与激活</RouterLink>
          <button @click="logout"><LogOut :size="17" />退出登录</button>
        </div>
      </div>
    </header>
    <main id="main-content" class="workspace-content"><RouterView :key="route.path" /></main>
    <nav class="workspace-bottom-nav" aria-label="移动端导航">
      <RouterLink
        v-for="link in links"
        :key="link.path"
        :to="link.path"
        :class="{ active: active(link.path) }"
        :aria-current="active(link.path) ? 'page' : undefined"
        ><component :is="link.icon" :size="20" /><span>{{ link.label }}</span></RouterLink
      >
    </nav>
  </div>
</template>
