<template>
  <el-card class="account-recovery-card" shadow="never">
    <div class="card-title">
      <el-icon>
        <RefreshRight />
      </el-icon>
      <span>账号恢复</span>
    </div>

    <div class="summary-row">
      <div class="summary-copy">
        <div class="summary-title">让当前已登录设备为新设备背书</div>
        <p class="summary-desc">
          忘记密码时，可先在本设备完成一次敏感验证，再生成一个 5 分钟内有效的一次性恢复二维码。
          新设备通过扫码或输入恢复码即可重置密码，并自动撤销旧会话。
        </p>
      </div>
      <el-button
        class="issue-btn"
        type="primary"
        plain
        :loading="issueLoading || refreshLoading"
        @click="handleIssue"
      >
        发起恢复授权
      </el-button>
    </div>
  </el-card>

  <el-dialog
    v-model="dialogVisible"
    title="账号恢复授权"
    width="460px"
    @closed="handleDialogClosed"
  >
    <div class="recovery-panel">
      <img v-if="qrImage" :src="qrImage" alt="账号恢复二维码" class="qr-image" />
      <div v-else class="qr-placeholder">正在生成恢复二维码...</div>

      <div class="account-chip">
        <strong>{{ ticket?.username || '当前账号' }}</strong>
        <span>{{ ticket?.maskedEmail }}</span>
      </div>

      <p class="meta">剩余有效期：{{ Math.max(expiresInSeconds, 0) }} 秒</p>
      <p class="tip">请在新设备打开“忘记密码”页面扫码，或手动输入下面的恢复码。</p>

      <div class="code-row">
        <el-input readonly :model-value="ticket?.recoveryCode || ''" />
        <el-button @click="copyRecoveryCode">复制</el-button>
      </div>

      <div class="detail-grid" v-if="ticket">
        <div class="detail-item">
          <span class="label">背书设备</span>
          <span class="value">{{ ticket.sponsorClientName || '当前设备' }}</span>
        </div>
        <div class="detail-item">
          <span class="label">位置</span>
          <span class="value">{{ ticket.sponsorIpLocation || '未知位置' }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
      <el-button type="primary" :loading="refreshLoading" @click="refreshTicket">刷新授权</el-button>
    </template>
  </el-dialog>

  <SensitiveVerificationDialog
    v-model="sensitiveDialogVisible"
    :disable-qr-method="true"
    @success="handleSensitiveVerificationSuccess"
    @cancel="handleSensitiveVerificationCancel"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import { RefreshRight } from '@element-plus/icons-vue'
import { checkSensitiveVerification, issueAccountRecoveryTicket, type AccountRecoveryTicketResponse } from '@/api/auth'
import SensitiveVerificationDialog from '@/components/SensitiveVerificationDialog.vue'

const dialogVisible = ref(false)
const sensitiveDialogVisible = ref(false)
const issueLoading = ref(false)
const refreshLoading = ref(false)
const qrImage = ref('')
const expiresInSeconds = ref(0)
const ticket = ref<AccountRecoveryTicketResponse | null>(null)
let pendingAction: null | (() => Promise<void>) = null
let countdownTimer: number | null = null

const clearCountdown = () => {
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer)
    countdownTimer = null
  }
}

const startCountdown = () => {
  clearCountdown()
  countdownTimer = window.setInterval(() => {
    if (expiresInSeconds.value <= 0) {
      clearCountdown()
      return
    }
    expiresInSeconds.value -= 1
  }, 1000)
}

const buildRecoveryUrl = (recoveryCode: string): string => {
  const url = new URL('/forgot-password', window.location.origin)
  url.searchParams.set('recoveryCode', recoveryCode)
  return url.toString()
}

const openTicketDialog = async (nextTicket: AccountRecoveryTicketResponse) => {
  ticket.value = nextTicket
  expiresInSeconds.value = nextTicket.expiresInSeconds
  qrImage.value = await QRCode.toDataURL(buildRecoveryUrl(nextTicket.recoveryCode), {
    width: 240,
    margin: 1,
  })
  dialogVisible.value = true
  startCountdown()
}

const fetchTicket = async () => {
  const nextTicket = await issueAccountRecoveryTicket()
  await openTicketDialog(nextTicket)
}

const refreshTicket = async () => {
  refreshLoading.value = true
  try {
    await fetchTicket()
    ElMessage.success('恢复授权已刷新')
  } finally {
    refreshLoading.value = false
  }
}

const runWithSensitiveVerification = async (action: () => Promise<void>) => {
  try {
    const status = await checkSensitiveVerification()
    if (status.verified) {
      await action()
      return
    }

    ElMessage.info('需要先完成身份验证')
    pendingAction = action
    sensitiveDialogVisible.value = true
  } catch (error) {
    console.error('Check sensitive verification failed:', error)
    ElMessage.info('需要先完成身份验证')
    pendingAction = action
    sensitiveDialogVisible.value = true
  }
}

const handleIssue = async () => {
  if (issueLoading.value || refreshLoading.value) {
    return
  }

  issueLoading.value = true
  try {
    await runWithSensitiveVerification(async () => {
      await fetchTicket()
      ElMessage.success('恢复授权已生成')
    })
  } finally {
    issueLoading.value = false
  }
}

const handleSensitiveVerificationSuccess = async () => {
  const action = pendingAction
  pendingAction = null
  if (!action) return

  try {
    await action()
  } catch (error) {
    console.error('Run pending recovery action failed:', error)
  }
}

const handleSensitiveVerificationCancel = () => {
  pendingAction = null
}

const copyRecoveryCode = async () => {
  if (!ticket.value?.recoveryCode) {
    return
  }

  try {
    await navigator.clipboard.writeText(ticket.value.recoveryCode)
    ElMessage.success('恢复码已复制')
  } catch (error) {
    console.error('Copy recovery code failed:', error)
    ElMessage.error('复制失败，请手动复制')
  }
}

const handleDialogClosed = () => {
  clearCountdown()
  qrImage.value = ''
  expiresInSeconds.value = 0
}

onBeforeUnmount(() => {
  clearCountdown()
})
</script>

<style scoped>
.account-recovery-card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 16px;
  font-size: 16px;
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.summary-copy {
  flex: 1;
}

.summary-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.summary-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.issue-btn {
  flex-shrink: 0;
}

.recovery-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding-top: 4px;
}

.qr-image {
  width: 240px;
  height: 240px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-light);
  background: #fff;
}

.qr-placeholder {
  width: 240px;
  height: 240px;
  display: grid;
  place-items: center;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  border-radius: 12px;
}

.account-chip {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 12px;
  color: var(--el-text-color-primary);
}

.account-chip span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.meta,
.tip {
  margin: 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.meta {
  font-size: 13px;
}

.tip {
  font-size: 12px;
  line-height: 1.6;
}

.code-row {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.detail-grid {
  width: 100%;
  display: grid;
  gap: 8px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}

.label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.value {
  color: var(--el-text-color-primary);
  font-size: 13px;
  text-align: right;
}

@media (max-width: 768px) {
  .summary-row {
    flex-direction: column;
    align-items: stretch;
  }

  .issue-btn {
    width: 100%;
  }

  .code-row {
    grid-template-columns: 1fr;
  }

  .detail-item {
    flex-direction: column;
  }

  .value {
    text-align: left;
  }
}
</style>
