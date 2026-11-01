<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getMembershipStatus,
  redeemMembership,
  type MembershipEntitlement,
} from '../features/membership/membershipApi'
import { errorMessage } from '../lib/errors'
import ActivationPurchasePanel from '../features/membership/ActivationPurchasePanel.vue'
const entitlements = ref<MembershipEntitlement[]>([]),
  code = ref(''),
  busy = ref(false),
  error = ref(''),
  success = ref(false)
async function load() {
  try {
    entitlements.value = await getMembershipStatus()
  } catch (failure) {
    error.value = errorMessage(failure)
  }
}
onMounted(load)
async function redeem() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  success.value = false
  try {
    await redeemMembership(code.value)
    code.value = ''
    success.value = true
    await load()
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="page narrow">
    <header class="page-heading">
      <div>
        <h1>会员与兑换</h1>
        <p>查看会员状态，获取激活码后在当前账号完成开通。</p>
      </div>
    </header>
    <div class="stack">
      <section class="panel">
        <h2>当前会员</h2>
        <div v-if="entitlements.length">
          <div v-for="item in entitlements" :key="`${item.code}:${item.startsAt}`" class="list-row">
            <strong>{{ item.label }}</strong>
            <p class="muted">
              {{
                item.endsAt ? `有效期至 ${new Date(item.endsAt).toLocaleDateString()}` : '永久有效'
              }}
            </p>
          </div>
        </div>
        <p v-else class="muted">免费账号，尚未开通 ReadQuest Pro。</p>
      </section>
      <ActivationPurchasePanel />
      <section class="panel">
        <h2>输入激活码</h2>
        <p class="muted">激活码只能使用一次，购买发出的激活码仅限购买账号兑换。</p>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="success" class="success" role="status">兑换成功，会员状态已更新。</p>
        <form @submit.prevent="redeem">
          <label
            >会员激活码<input
              v-model="code"
              required
              maxlength="64"
              autocomplete="off"
              placeholder="RQ-XXXX-XXXX-XXXX" /></label
          ><button class="button" :disabled="busy || !code.trim()">
            {{ busy ? '正在兑换…' : '立即兑换' }}
          </button>
        </form>
      </section>
    </div>
  </div>
</template>
