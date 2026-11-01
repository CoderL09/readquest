<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOpen } from '@lucide/vue'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../lib/errors'
const auth = useAuthStore(),
  route = useRoute(),
  router = useRouter()
const register = computed(() => route.path === '/register')
const email = ref(''),
  password = ref(''),
  name = ref(''),
  busy = ref(false),
  error = ref('')
async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    if (register.value) await auth.register(email.value, password.value, name.value)
    else await auth.login(email.value, password.value)
    const target =
      typeof route.query.redirect === 'string' &&
      route.query.redirect.startsWith('/app') &&
      !route.query.redirect.startsWith('//')
        ? route.query.redirect
        : '/app'
    await router.replace(target)
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="auth-page">
    <RouterLink to="/" class="brand"><BookOpen />ReadQuest</RouterLink>
    <form class="auth-form" @submit.prevent="submit">
      <h1>{{ register ? '建立你的阅读身份' : '欢迎回来' }}</h1>
      <p class="muted">
        {{ register ? '从今天开始，积累属于你的阅读旅程。' : '翻开上次读到的那一页。' }}
      </p>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <label v-if="register"
        >显示名称<input v-model="name" autocomplete="nickname" required maxlength="50" /></label
      ><label
        >邮箱<input
          v-model="email"
          type="email"
          autocomplete="email"
          required
          maxlength="254" /></label
      ><label
        >密码<input
          v-model="password"
          type="password"
          :autocomplete="register ? 'new-password' : 'current-password'"
          minlength="8"
          maxlength="128"
          required /></label
      ><button class="button" :disabled="busy">
        {{ busy ? '正在处理…' : register ? '创建账号' : '登录' }}
      </button>
      <p class="muted">
        {{ register ? '已经有账号？' : '还没有账号？'
        }}<RouterLink :to="register ? '/login' : '/register'">{{
          register ? '登录' : '注册'
        }}</RouterLink>
      </p>
    </form>
  </div>
</template>
