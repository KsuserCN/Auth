<template>
  <el-card class="account-recovery-card" shadow="never">
    <div class="card-title">
      <el-icon>
        <RefreshRight />
      </el-icon>
      <span>{{ t('ui.accountRecoveryCard.message001') }}</span>
    </div>

    <div class="summary-row">
      <div class="summary-copy">
        <div class="summary-title">{{ t('ui.accountRecoveryCard.message002') }}</div>
        <p class="summary-desc">
          {{ t('ui.accountRecoveryCard.message003') }}
        </p>
      </div>
      <el-button
        class="issue-btn"
        type="primary"
        plain
        :loading="issueLoading || refreshLoading"
        @click="handleIssue"
      >
        {{ t('ui.accountRecoveryCard.message004') }}
      </el-button>
    </div>
  </el-card>

  <el-dialog
    v-model="dialogVisible"
    :title="t('ui.accountRecoveryCard.message005')"
    width="460px"
    @closed="handleDialogClosed"
  >
    <div class="recovery-panel">
      <img
        v-if="qrImage"
        :src="qrImage"
        :alt="t('ui.accountRecoveryCard.message006')"
        class="qr-image"
      />
      <div v-else class="qr-placeholder">{{ t('ui.accountRecoveryCard.message007') }}</div>

      <div class="account-chip">
        <strong>{{ ticket?.username || t('ui.accountRecoveryCard.message008') }}</strong>
        <span>{{ ticket?.maskedEmail }}</span>
      </div>

      <p class="meta">
        {{ t('ui.accountRecoveryCard.message009') }}{{ Math.max(expiresInSeconds, 0) }}
        {{ t('ui.accountRecoveryCard.message010') }}
      </p>
      <p class="tip">{{ t('ui.accountRecoveryCard.message011') }}</p>

      <div class="code-row">
        <el-input readonly :model-value="ticket?.recoveryCode || ''" />
        <el-button @click="copyRecoveryCode">{{
          t('ui.accountRecoveryCard.message012')
        }}</el-button>
      </div>

      <div class="detail-grid" v-if="ticket">
        <div class="detail-item">
          <span class="label">{{ t('ui.accountRecoveryCard.message013') }}</span>
          <span class="value">{{
            ticket.sponsorClientName || t('ui.accountRecoveryCard.message014')
          }}</span>
        </div>
        <div class="detail-item">
          <span class="label">{{ t('ui.accountRecoveryCard.message015') }}</span>
          <span class="value">{{
            ticket.sponsorIpLocation || t('ui.accountRecoveryCard.message016')
          }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">{{
        t('ui.accountRecoveryCard.message017')
      }}</el-button>
      <el-button type="primary" :loading="refreshLoading" @click="refreshTicket">{{
        t('ui.accountRecoveryCard.message018')
      }}</el-button>
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
import { t } from '@/i18n'

import { onBeforeUnmount, ref } from 'vue'
import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import { RefreshRight } from '@element-plus/icons-vue'
import {
  checkSensitiveVerification,
  issueAccountRecoveryTicket,
  type AccountRecoveryTicketResponse,
} from '@/api/auth'
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
    ElMessage.success(t('ui.accountRecoveryCard.message019'))
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

    ElMessage.info(t('ui.accountRecoveryCard.message020'))
    pendingAction = action
    sensitiveDialogVisible.value = true
  } catch (error) {
    console.error('Check sensitive verification failed:', error)
    ElMessage.info(t('ui.accountRecoveryCard.message020'))
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
      ElMessage.success(t('ui.accountRecoveryCard.message021'))
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
    ElMessage.success(t('ui.accountRecoveryCard.message022'))
  } catch (error) {
    console.error('Copy recovery code failed:', error)
    ElMessage.error(t('ui.accountRecoveryCard.message023'))
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
