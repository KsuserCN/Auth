<template>
  <div v-if="!isMobileBrowser" class="mobile-bridge-sidebar">
    <div class="section-label">{{ t('ui.mobileBridgeQrSidebarAction.message001') }}</div>
    <el-button
      class="bridge-btn"
      type="primary"
      plain
      :loading="mobileBridgeQrLoading || mobileBridgeQrRefreshing"
      @click="handleOpenMobileBridgeQr"
    >
      <el-icon><Iphone /></el-icon>
      <span class="bridge-btn-label">{{ t('ui.mobileBridgeQrSidebarAction.message002') }}</span>
    </el-button>
  </div>

  <div v-else-if="isAppleMobile" class="mobile-bridge-sidebar">
    <div class="section-label">{{ t('ui.mobileBridgeQrSidebarAction.message009') }}</div>
    <a
      class="bridge-btn app-download-btn"
      :href="appStoreUrl"
      target="_blank"
      rel="noopener noreferrer"
    >
      <el-icon><Iphone /></el-icon>
      <span class="bridge-btn-label">{{ t('ui.mobileBridgeQrSidebarAction.message010') }}</span>
    </a>
  </div>

  <el-dialog
    v-model="mobileBridgeQrVisible"
    :title="t('ui.mobileBridgeQrSidebarAction.message002')"
    width="420px"
    @closed="handleMobileBridgeQrDialogClosed"
  >
    <div class="mobile-bridge-qr-panel">
      <img
        v-if="mobileBridgeQrImage"
        :src="mobileBridgeQrImage"
        :alt="t('ui.mobileBridgeQrSidebarAction.message003')"
        class="mobile-bridge-qr-image"
      />
      <div v-else class="mobile-bridge-qr-placeholder">
        {{ t('ui.mobileBridgeQrSidebarAction.message004') }}
      </div>
      <p class="mobile-bridge-qr-meta">
        {{ t('ui.accountRecoveryCard.message009')
        }}{{ Math.max(mobileBridgeQrExpiresInSeconds, 0) }}
        {{ t('ui.accountRecoveryCard.message010') }}
      </p>
      <p class="mobile-bridge-qr-tip">{{ t('ui.mobileBridgeQrSidebarAction.message005') }}</p>
    </div>
    <template #footer>
      <el-button @click="mobileBridgeQrVisible = false">{{
        t('ui.accountRecoveryCard.message017')
      }}</el-button>
      <el-button type="primary" :loading="mobileBridgeQrRefreshing" @click="refreshMobileBridgeQr">
        {{ t('ui.mobileBridgeQrSidebarAction.message006') }}
      </el-button>
    </template>
  </el-dialog>

  <SensitiveVerificationDialog
    v-model="sensitiveDialogVisible"
    :disable-qr-method="sensitiveDialogDisableQr"
    @success="handleSensitiveVerificationSuccess"
    @cancel="handleSensitiveVerificationCancel"
  />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { effectiveLocale, t } from '@/i18n'

import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import { Iphone } from '@element-plus/icons-vue'
import {
  checkSensitiveVerification,
  createSessionTransfer,
  getPasskeySensitiveVerificationOptions,
  verifyPasskeySensitiveOperation,
} from '@/api/auth'
import {
  extractAuthenticationData,
  getPasskeyCredential,
  isWebAuthnSupported,
} from '@/utils/webauthn'
import SensitiveVerificationDialog from '@/components/SensitiveVerificationDialog.vue'

const sensitiveDialogVisible = ref(false)
const sensitiveDialogDisableQr = ref(false)
const mobileBridgeQrVisible = ref(false)
const mobileBridgeQrLoading = ref(false)
const mobileBridgeQrRefreshing = ref(false)
const mobileBridgeQrImage = ref('')
const mobileBridgeQrExpiresInSeconds = ref(0)
const passkeySupported = isWebAuthnSupported()

const userAgent = typeof navigator === 'undefined' ? '' : navigator.userAgent
const isAppleMobile =
  /iPhone|iPad/i.test(userAgent) ||
  (typeof navigator !== 'undefined' &&
    navigator.platform === 'MacIntel' &&
    navigator.maxTouchPoints > 1)
const isMobileBrowser =
  /Android|iPhone|iPad|iPod|Mobile|Windows Phone/i.test(userAgent) || isAppleMobile
const appStoreUrl = computed(() =>
  effectiveLocale.value === 'zh-Hans'
    ? 'https://apps.apple.com/cn/app/ksuser%E5%AE%89%E5%85%A8/id6776157121'
    : 'https://apps.apple.com/us/app/ksuser%E5%AE%89%E5%85%A8/id6776157121',
)

let pendingSensitiveAction: null | (() => Promise<void>) = null
let mobileBridgeQrCountdownTimer: number | null = null

const cleanupMobileBridgeQrCountdown = () => {
  if (mobileBridgeQrCountdownTimer !== null) {
    window.clearInterval(mobileBridgeQrCountdownTimer)
    mobileBridgeQrCountdownTimer = null
  }
}

const startMobileBridgeQrCountdown = () => {
  cleanupMobileBridgeQrCountdown()
  mobileBridgeQrCountdownTimer = window.setInterval(() => {
    if (mobileBridgeQrExpiresInSeconds.value <= 0) {
      cleanupMobileBridgeQrCountdown()
      return
    }
    mobileBridgeQrExpiresInSeconds.value -= 1
  }, 1000)
}

const resetMobileBridgeQrState = () => {
  cleanupMobileBridgeQrCountdown()
  mobileBridgeQrImage.value = ''
  mobileBridgeQrExpiresInSeconds.value = 0
}

const issueMobileBridgeQr = async () => {
  mobileBridgeQrRefreshing.value = true
  try {
    const transfer = await createSessionTransfer('mobile', 'bridge_login')
    const qrText = `KSUSER-AUTH-XFER:v1:${transfer.transferCode}`
    mobileBridgeQrImage.value = await QRCode.toDataURL(qrText, { width: 240, margin: 1 })
    mobileBridgeQrExpiresInSeconds.value = transfer.expiresInSeconds
    mobileBridgeQrVisible.value = true
    startMobileBridgeQrCountdown()
  } finally {
    mobileBridgeQrRefreshing.value = false
  }
}

const runWithSensitiveVerification = async (
  action: () => Promise<void>,
  options?: { disableQrMethod?: boolean },
) => {
  const disableQrMethod = options?.disableQrMethod ?? false
  try {
    const status = await checkSensitiveVerification()
    if (status.verified) {
      sensitiveDialogDisableQr.value = false
      await action()
      return
    }

    ElMessage.info(t('ui.mobileBridgeQrSidebarAction.message007'))
    pendingSensitiveAction = action
    sensitiveDialogDisableQr.value = disableQrMethod
    sensitiveDialogVisible.value = true
  } catch (error) {
    console.error('Check sensitive verification failed:', error)
    ElMessage.info(t('ui.mobileBridgeQrSidebarAction.message007'))
    pendingSensitiveAction = action
    sensitiveDialogDisableQr.value = disableQrMethod
    sensitiveDialogVisible.value = true
  }
}

const handleSensitiveVerificationSuccess = async () => {
  const action = pendingSensitiveAction
  pendingSensitiveAction = null
  sensitiveDialogDisableQr.value = false
  if (!action) return

  try {
    await action()
  } catch (error) {
    console.error('Run pending sensitive action failed:', error)
  }
}

const handleSensitiveVerificationCancel = () => {
  pendingSensitiveAction = null
  sensitiveDialogDisableQr.value = false
}

const tryPasskeySensitiveVerificationForBridge = async (): Promise<boolean> => {
  if (!passkeySupported) {
    return false
  }

  try {
    const options = await getPasskeySensitiveVerificationOptions()
    const credential = await getPasskeyCredential({
      challenge: options.challenge,
      timeout: options.timeout,
      rpId: options.rpId,
      userVerification: options.userVerification,
      allowCredentials: options.allowCredentials,
    })
    if (!credential) {
      return false
    }
    await verifyPasskeySensitiveOperation(
      options.challengeId,
      extractAuthenticationData(credential),
    )
    return true
  } catch (error) {
    console.warn('Passkey sensitive verification failed, fallback to dialog:', error)
    return false
  }
}

const handleOpenMobileBridgeQr = async () => {
  if (mobileBridgeQrLoading.value || mobileBridgeQrRefreshing.value) {
    return
  }

  mobileBridgeQrLoading.value = true
  try {
    const passkeyVerified = await tryPasskeySensitiveVerificationForBridge()
    if (passkeyVerified) {
      await issueMobileBridgeQr()
      ElMessage.success(t('ui.mobileBridgeQrSidebarAction.message008'))
      return
    }

    await runWithSensitiveVerification(
      async () => {
        await issueMobileBridgeQr()
        ElMessage.success(t('ui.mobileBridgeQrSidebarAction.message008'))
      },
      { disableQrMethod: true },
    )
  } finally {
    mobileBridgeQrLoading.value = false
  }
}

const refreshMobileBridgeQr = async () => {
  await issueMobileBridgeQr()
}

const handleMobileBridgeQrDialogClosed = () => {
  resetMobileBridgeQrState()
}

onBeforeUnmount(() => {
  cleanupMobileBridgeQrCountdown()
})
</script>

<style scoped>
.mobile-bridge-sidebar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.section-label {
  padding: 0 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.8px;
}

.bridge-btn {
  width: 100%;
  min-width: 0;
  min-height: 40px;
  height: auto;
  padding: 8px 12px;
  border-radius: 8px;
  justify-content: flex-start;
  gap: 8px;
  white-space: normal;
}

.app-download-btn {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  color: var(--el-color-primary);
  text-decoration: none;
}

.app-download-btn:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.app-download-btn:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.bridge-btn :deep(.el-icon) {
  flex: 0 0 auto;
  margin-right: 0;
  font-size: 18px;
}

.bridge-btn :deep(.bridge-btn-label) {
  min-width: 0;
  flex: 1 1 auto;
  margin-left: 0;
  overflow-wrap: anywhere;
  text-align: left;
  white-space: normal;
  line-height: 1.25;
}

.mobile-bridge-qr-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 8px 0 4px;
}

.mobile-bridge-qr-image,
.mobile-bridge-qr-placeholder {
  width: 240px;
  height: 240px;
  border-radius: 16px;
  background: var(--el-fill-color-light);
}

.mobile-bridge-qr-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.mobile-bridge-qr-meta {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mobile-bridge-qr-tip {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
