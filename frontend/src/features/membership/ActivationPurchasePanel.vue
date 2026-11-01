<script setup lang="ts">
import { ref, onMounted, onScopeDispose } from 'vue'
import { api } from '../../lib/api'
import { useAuthStore } from '../../stores/auth'
import { errorMessage } from '../../lib/errors'
type Offer = {
  enabled: boolean
  mailConfigured: boolean
  deliveryMode: string
  membershipDays: number
  purchasesPerDay: number
  aiRequestsPerDay: number
  imagesPerDay: number
}
type Purchase = {
  id: string
  email: string
  status: string
  deliveryStatus: string
  deliveryMode: string
  codeExpiresAt: string | null
}
const auth = useAuthStore(),
  offer = ref<Offer | null>(null),
  purchase = ref<Purchase | null>(null),
  email = ref(auth.user?.email ?? ''),
  busy = ref(false),
  error = ref('')
const controller = new AbortController()
let timer: ReturnType<typeof setTimeout> | undefined,
  key: string | null = null
onScopeDispose(() => {
  controller.abort()
  clearTimeout(timer)
})
onMounted(async () => {
  try {
    const [settings, orders] = await Promise.all([
      api.get<Offer>('/membership-offers', { signal: controller.signal }),
      api.get<{ purchases: Purchase[] }>('/activation-purchases', { signal: controller.signal }),
    ])
    if (controller.signal.aborted) return
    offer.value = settings.data
    purchase.value = orders.data.purchases[0] ?? null
    schedule()
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  }
})
function schedule() {
  clearTimeout(timer)
  if (purchase.value?.status === 'paid' && purchase.value.deliveryStatus === 'pending')
    timer = setTimeout(poll, 1500)
}
async function poll() {
  if (!purchase.value) return
  try {
    const result = await api.get<Purchase>(`/activation-purchases/${purchase.value.id}`, {
      signal: controller.signal,
    })
    if (!controller.signal.aborted) {
      purchase.value = result.data
      schedule()
    }
  } catch {
    if (!controller.signal.aborted) timer = setTimeout(poll, 5000)
  }
}
async function perform(kind: 'create' | 'confirm' | 'resend') {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    let value: Purchase
    if (kind === 'create') {
      key ??= crypto.randomUUID()
      value = (
        await api.post<Purchase>(
          '/activation-purchases',
          { email: email.value.trim() },
          { signal: controller.signal, headers: { 'Idempotency-Key': key } },
        )
      ).data
    } else {
      if (!purchase.value) return
      value =
        kind === 'confirm'
          ? (
              await api.put<Purchase>(
                `/activation-purchases/${purchase.value.id}/simulated-payment`,
                undefined,
                { signal: controller.signal },
              )
            ).data
          : (
              await api.post<Purchase>(
                `/activation-purchases/${purchase.value.id}/email-deliveries`,
                undefined,
                { signal: controller.signal },
              )
            ).data
    }
    if (!controller.signal.aborted) {
      purchase.value = value
      schedule()
    }
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
function reset() {
  purchase.value = null
  key = null
  clearTimeout(timer)
}
</script>
<template>
  <section class="panel stack">
    <h2>模拟购买与邮件激活</h2>
    <p>此流程不涉及真实扣款。购买成功后，输入邮件中的激活码兑换会员。</p>
    <p v-if="offer?.enabled" class="muted">
      体验会员 {{ offer.membershipDays }} 天，每个账号和邮箱每 24 小时限购
      {{ offer.purchasesPerDay }} 次；每日 AI 请求 {{ offer.aiRequestsPerDay }} 次，图片请求最多
      {{ offer.imagesPerDay }} 次。
    </p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="offer && !offer.enabled" class="muted">
      模拟购买仅在本地开发环境开放，已有激活码仍可兑换。
    </p>
    <p v-if="offer?.enabled && !offer.mailConfigured" class="error">发件邮箱尚未配置完成。</p>
    <form v-if="offer?.enabled && !purchase" @submit.prevent="perform('create')">
      <label
        >接收激活码的注册邮箱<input
          v-model="email"
          type="email"
          autocomplete="email"
          required
          maxlength="254"
          :disabled="busy"
          @input="key = null" /></label
      ><button class="button" :disabled="busy || !offer.mailConfigured">
        {{ busy ? '处理中…' : '创建模拟订单' }}
      </button>
    </form>
    <div v-if="purchase" class="stack">
      <p>接收邮箱：{{ purchase.email }}</p>
      <div v-if="purchase.status === 'pending'" class="actions">
        <button class="button" :disabled="busy || !offer?.enabled" @click="perform('confirm')">
          模拟付款（不扣款）</button
        ><button class="button secondary" :disabled="busy" @click="reset">返回填写邮箱</button>
      </div>
      <div v-else-if="purchase.status === 'paid'" class="stack">
        <p role="status">
          {{
            purchase.deliveryStatus === 'sent'
              ? purchase.deliveryMode === 'local'
                ? '邮件已生成到本地预览，未发送到外部邮箱。'
                : '激活邮件已提交发送，请检查收件箱与垃圾邮件。'
              : purchase.deliveryStatus === 'failed'
                ? '发送失败，可以重试发送相同的激活码。'
                : purchase.deliveryStatus === 'expired'
                  ? '激活码已过期。'
                  : '正在发送激活邮件…'
          }}
        </p>
        <p v-if="purchase.codeExpiresAt" class="muted">
          兑换截止 {{ new Date(purchase.codeExpiresAt).toLocaleString() }}，仅限购买账号使用。
        </p>
        <div class="actions">
          <button
            v-if="['sent', 'failed'].includes(purchase.deliveryStatus)"
            class="button secondary"
            :disabled="busy"
            @click="perform('resend')"
          >
            重新发送激活邮件</button
          ><button class="button secondary" :disabled="busy" @click="reset">
            创建下一笔模拟订单
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
