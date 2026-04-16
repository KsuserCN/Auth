<template>
  <div class="mobile-bridge-sidebar">
    <div class="section-label">跨端联通</div>
    <el-button
      class="bridge-btn"
      type="primary"
      plain
      :loading="mobileBridgeQrLoading || mobileBridgeQrRefreshing"
      @click="handleOpenMobileBridgeQr"
    >
      <el-icon><Iphone /></el-icon>
      <span>手机扫码登录</span>
    </el-button>
  </div>

  <el-dialog
    v-model="mobileBridgeQrVisible"
    title="手机扫码登录"
    width="420px"
    @closed="handleMobileBridgeQrDialogClosed"
  >
    <div class="mobile-bridge-qr-panel">
      <img
        v-if="mobileBridgeQrImage"
        :src="mobileBridgeQrImage"
        alt="手机扫码登录二维码"
        class="mobile-bridge-qr-image"
      />
      <div v-else class="mobile-bridge-qr-placeholder">正在生成二维码...</div>
      <p class="mobile-bridge-qr-meta">剩余有效期：{{ Math.max(mobileBridgeQrExpiresInSeconds, 0) }} 秒</p>
      <p class="mobile-bridge-qr-tip">二维码一次性有效，过期后请刷新。</p>
    </div>
    <template #footer>
      <el-button @click="mobileBridgeQrVisible = false">关闭</el-button>
      <el-button type="primary" :loading="mobileBridgeQrRefreshing" @click="refreshMobileBridgeQr">
        刷新二维码
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
import { onBeforeUnmount, ref } from 'vue'
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

    ElMessage.info('需要验证身份')
    pendingSensitiveAction = action
    sensitiveDialogDisableQr.value = disableQrMethod
    sensitiveDialogVisible.value = true
  } catch (error) {
    console.error('Check sensitive verification failed:', error)
    ElMessage.info('需要验证身份')
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
    await verifyPasskeySensitiveOperation(options.challengeId, extractAuthenticationData(credential))
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
      ElMessage.success('二维码已生成')
      return
    }

    await runWithSensitiveVerification(
      async () => {
        await issueMobileBridgeQr()
        ElMessage.success('二维码已生成')
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
  height: 40px;
  border-radius: 8px;
  justify-content: flex-start;
}

.bridge-btn :deep(.el-icon) {
  margin-right: 8px;
  font-size: 18px;
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
