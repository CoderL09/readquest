<script setup lang="ts">
import { ref, onMounted, onScopeDispose } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useNoticeStore } from '../stores/notices'
import { errorMessage } from '../lib/errors'
import { mediaUrl } from '../lib/api'
import { updateDisplayName, uploadAvatar } from '../features/profile/profileApi'
import {
  getAccountExport,
  listAccountSessions,
  revokeAccountSession,
  requestAccountDeletion,
  type AccountSession,
} from '../features/account/accountApi'
import DictionaryManager from '../features/dictionary/DictionaryManager.vue'
const auth = useAuthStore(),
  router = useRouter(),
  notices = useNoticeStore(),
  name = ref(auth.user?.displayName ?? ''),
  avatar = ref<File | null>(null),
  sessions = ref<AccountSession[]>([]),
  password = ref(''),
  confirmation = ref(''),
  busy = ref(false),
  error = ref('')
const controller = new AbortController()
onScopeDispose(() => controller.abort())
async function refreshSessions() {
  try {
    const value = await listAccountSessions()
    if (!controller.signal.aborted) sessions.value = value
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  }
}
onMounted(refreshSessions)
async function action(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await operation()
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
function save() {
  return action(async () => {
    auth.update(await updateDisplayName(name.value))
    notices.notify('显示名称已保存。')
  })
}
function chooseAvatar(event: Event) {
  avatar.value = (event.target as HTMLInputElement).files?.[0] ?? null
}
function saveAvatar() {
  return action(async () => {
    if (!avatar.value) return
    if (avatar.value.size > 2 * 1024 * 1024) throw new Error('头像不能超过 2 MB。')
    auth.update(await uploadAvatar(avatar.value))
    avatar.value = null
    notices.notify('头像已更新。')
  })
}
function revoke(id: string) {
  return action(async () => {
    await revokeAccountSession(id)
    await refreshSessions()
    notices.notify('会话已撤销。')
  })
}
function exportData() {
  return action(async () => {
    const file = await getAccountExport(),
      url = URL.createObjectURL(file),
      anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'readquest-data.json'
    anchor.click()
    URL.revokeObjectURL(url)
  })
}
function removeAccount() {
  if (
    confirmation.value !== 'DELETE' ||
    !confirm('注销后将清理账号私有数据，此操作无法撤销。确定继续吗？')
  )
    return
  return action(async () => {
    await requestAccountDeletion(password.value)
    await auth.deleted()
    await router.replace('/register')
  })
}
</script>
<template>
  <div class="page narrow">
    <header class="page-heading">
      <div>
        <h1>个人资料</h1>
        <p>管理阅读身份、个人词典和账号数据。</p>
      </div>
      <RouterLink class="button secondary" to="/app/billing">会员与激活</RouterLink>
    </header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="stack">
      <section class="panel stack">
        <h2>我的账号</h2>
        <p class="muted">{{ auth.user?.email }}</p>
        <form @submit.prevent="save">
          <label>显示名称<input v-model="name" required maxlength="50" /></label
          ><button class="button" :disabled="busy">保存名称</button>
        </form>
        <form @submit.prevent="saveAvatar">
          <img
            v-if="auth.user?.avatarUrl"
            :src="mediaUrl(auth.user.avatarUrl)"
            alt="我的头像"
            width="72"
            height="72"
            class="rounded-full"
          /><label
            >头像（JPEG、PNG、WebP，最大 2 MB）<input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              @change="chooseAvatar" /></label
          ><button class="button secondary" :disabled="busy || !avatar">更新头像</button>
        </form>
      </section>
      <DictionaryManager />
      <section class="panel">
        <header class="row">
          <h2>登录设备与会话</h2>
          <button class="button secondary small" @click="refreshSessions">刷新</button>
        </header>
        <div v-for="session in sessions" :key="session.id" class="list-row row">
          <span
            >登录于 {{ new Date(session.createdAt).toLocaleString()
            }}<small class="block"
              >到期 {{ new Date(session.expiresAt).toLocaleString() }}</small
            ></span
          ><button class="button secondary small" :disabled="busy" @click="revoke(session.id)">
            撤销会话
          </button>
        </div>
      </section>
      <section class="panel">
        <h2>导出我的数据</h2>
        <p class="muted">下载阅读进度、笔记、复习和账号相关数据。</p>
        <button class="button secondary" :disabled="busy" @click="exportData">下载数据副本</button>
      </section>
      <section class="panel danger-zone">
        <h2>注销账号</h2>
        <p class="muted">注销会撤销会话并清理私有数据，请先导出需要保留的内容。</p>
        <form @submit.prevent="removeAccount">
          <label
            >当前密码<input
              v-model="password"
              type="password"
              autocomplete="current-password"
              required /></label
          ><label
            >输入 DELETE 确认<input v-model="confirmation" autocomplete="off" required /></label
          ><button class="button danger" :disabled="busy || confirmation !== 'DELETE'">
            确认注销账号
          </button>
        </form>
      </section>
    </div>
  </div>
</template>
