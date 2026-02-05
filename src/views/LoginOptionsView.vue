<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">登录选项</h1>
        <p class="page-subtitle">管理您的登录凭据和认证方式</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Lock />
            </el-icon>
            <span>账户信息</span>
          </div>
          <div class="info-list">
            <!-- 邮箱 -->
            <div class="info-row" :class="{ loading: emailLoading }" @click="handleChangeEmail">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Message />
                </el-icon>
                <span class="row-label">邮箱</span>
              </div>
              <div class="row-right">
                <span class="row-value">{{ userEmail }}</span>
                <el-icon v-if="!emailLoading" class="row-arrow">
                  <ArrowRight />
                </el-icon>
                <el-icon v-else class="row-arrow loading-icon" :size="16">
                  <Loading />
                </el-icon>
              </div>
            </div>

            <!-- 密码 -->
            <div class="info-row" :class="{ loading: passwordLoading }" @click="handleChangePassword">
              <div class="row-left">
                <el-icon class="row-icon">
                  <Key />
                </el-icon>
                <span class="row-label">密码</span>
              </div>
              <div class="row-right">
                <span class="row-value">••••••••</span>
                <el-icon v-if="!passwordLoading" class="row-arrow">
                  <ArrowRight />
                </el-icon>
                <el-icon v-else class="row-arrow loading-icon" :size="16">
                  <Loading />
                </el-icon>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap" v-if="isPasskeySupported">
      <el-col :xs="24" :lg="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Cpu />
            </el-icon>
            <span>Passkey 管理</span>
            <el-button type="primary" plain size="small" @click="handleAddPasskey" :loading="addLoading"
              style="margin-left: auto">
              添加 Passkey
            </el-button>
          </div>
          <div v-if="passkeyLoading" class="passkey-loading">
            <el-icon class="loading-icon">
              <Loading />
            </el-icon>
            <span>加载中...</span>
          </div>
          <div v-else-if="passkeyList.length === 0" class="passkey-empty">
            <p>您还没有添加任何 Passkey</p>
            <p class="empty-desc">Passkey 可以让您使用生物识别或安全密钥快速登录</p>
          </div>
          <div v-else class="passkey-list">
            <div v-for="passkey in passkeyList" :key="passkey.id" class="passkey-item">
              <div class="passkey-left">
                <el-icon class="passkey-icon">
                  <Cpu />
                </el-icon>
                <div class="passkey-info">
                  <div class="passkey-name">
                    {{ passkey.name }}
                    <el-tag v-for="transport in getTransportTags(passkey.transports)" :key="transport.value"
                      :type="transport.type" size="small" style="margin-left: 6px">
                      {{ transport.label }}
                    </el-tag>
                  </div>
                  <div class="passkey-meta">
                    <span v-if="passkey.lastUsedAt">最后使用: {{ formatDate(passkey.lastUsedAt) }}</span>
                    <span v-else>从未使用</span>
                    <span class="separator">•</span>
                    <span>创建于: {{ formatDate(passkey.createdAt) }}</span>
                  </div>
                </div>
              </div>
              <div class="passkey-actions">
                <el-button text size="small" @click="handleRenamePasskey(passkey.id, passkey.name)">
                  重命名
                </el-button>
                <el-button text type="danger" size="small" @click="handleDeletePasskey(passkey.id, passkey.name)">
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  Key,
  Lock,
  Message,
  ArrowRight,
  Loading,
  Cpu,
} from '@element-plus/icons-vue'
import {
  checkSensitiveVerification,
  getPasskeyList,
  deletePasskey,
  renamePasskey,
  getPasskeyRegistrationOptions,
  verifyPasskeyRegistration,
  type PasskeyListItem,
} from '@/api/auth'
import { isWebAuthnSupported, createPasskeyCredential, extractRegistrationData } from '@/utils/webauthn'

const router = useRouter()
const userStore = useUserStore()

const userEmail = computed(() => userStore.user?.email || '—')
const emailLoading = ref(false)
const passwordLoading = ref(false)

// Passkey 相关状态
const isPasskeySupported = ref(false)
const passkeyList = ref<PasskeyListItem[]>([])
const passkeyLoading = ref(false)
const addLoading = ref(false)

onMounted(async () => {
  await userStore.fetchUserInfo()
  isPasskeySupported.value = isWebAuthnSupported()

  if (isPasskeySupported.value) {
    await loadPasskeyList()
  }
})

const loadPasskeyList = async () => {
  try {
    passkeyLoading.value = true
    passkeyList.value = await getPasskeyList()
  } catch (error) {
    console.error('Load passkey list failed:', error)
  } finally {
    passkeyLoading.value = false
  }
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    return '今天'
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days} 天前`
  } else if (days < 30) {
    return `${Math.floor(days / 7)} 周前`
  } else if (days < 365) {
    return `${Math.floor(days / 30)} 个月前`
  } else {
    return `${Math.floor(days / 365)} 年前`
  }
}

const getTransportTags = (transports: string) => {
  const transportMap: Record<string, { label: string; type: 'success' | 'info' | 'warning' | '' }> = {
    internal: { label: '本设备', type: 'success' },
    hybrid: { label: '跨设备', type: 'info' },
    usb: { label: 'USB', type: '' },
    nfc: { label: 'NFC', type: '' },
    ble: { label: '蓝牙', type: '' },
  }

  return transports
    .split(',')
    .map(t => t.trim())
    .filter(t => t in transportMap)
    .map(t => ({ value: t, ...transportMap[t] }))
}

const handleAddPasskey = async () => {
  if (addLoading.value) return

  // 输入 Passkey 名称
  const { value: passkeyName } = await ElMessageBox.prompt('请输入 Passkey 名称', '添加 Passkey', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：我的 MacBook Pro',
    inputValidator: (value) => {
      if (!value || value.trim().length === 0) {
        return '请输入 Passkey 名称'
      }
      if (value.trim().length > 50) {
        return '名称不能超过50个字符'
      }
      return true
    },
  }).catch(() => ({ value: null }))

  if (!passkeyName) return

  try {
    addLoading.value = true

    // 1. 获取注册选项
    const options = await getPasskeyRegistrationOptions({ passkeyName: passkeyName.trim() })

    // 2. 创建凭证
    const credential = await createPasskeyCredential(options)

    if (!credential) {
      throw new Error('未创建凭证')
    }

    // 3. 提取注册数据
    const regData = extractRegistrationData(credential)

    // 4. 验证并完成注册
    await verifyPasskeyRegistration({
      ...regData,
      passkeyName: passkeyName.trim(),
    })

    ElMessage.success('Passkey 添加成功')

    // 5. 重新加载列表
    await loadPasskeyList()
  } catch (error: unknown) {
    if (error instanceof Error) {
      if (error.name === 'NotAllowedError') {
        ElMessage.error('用户取消了操作')
        return
      }
      if (error.name === 'NotSupportedError') {
        ElMessage.error('浏览器不支持该操作')
        return
      }
      if (error.name === 'SecurityError') {
        ElMessage.error('安全错误，请检查网络连接')
        return
      }
      console.error('Add passkey failed:', error)
    } else {
      console.error('Add passkey failed:', error)
    }
  } finally {
    addLoading.value = false
  }
}

const handleRenamePasskey = async (id: number, currentName: string) => {
  try {
    const { value: newName } = await ElMessageBox.prompt('请输入新的 Passkey 名称', '重命名 Passkey', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: currentName,
      inputPlaceholder: '例如：我的 MacBook Pro',
      inputValidator: (value) => {
        if (!value || value.trim().length === 0) {
          return '请输入 Passkey 名称'
        }
        if (value.trim().length > 50) {
          return '名称不能超过50个字符'
        }
        return true
      },
    }).catch(() => ({ value: null }))

    if (!newName || newName.trim() === currentName) return

    await renamePasskey(id, newName.trim())
    ElMessage.success('Passkey 重命名成功')

    // 重新加载列表
    await loadPasskeyList()
  } catch (error: unknown) {
    console.error('Rename passkey failed:', error)
  }
}

const handleDeletePasskey = async (id: number, name: string) => {
  try {
    await ElMessageBox.confirm(`确定要删除 Passkey "${name}" 吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    await deletePasskey(id)
    ElMessage.success('Passkey 已删除')

    // 重新加载列表
    await loadPasskeyList()
  } catch (error: unknown) {
    if (error === 'cancel') {
      return
    }
    console.error('Delete passkey failed:', error)
  }
}

const handleChangeEmail = async () => {
  if (emailLoading.value) return
  emailLoading.value = true

  try {
    const status = await checkSensitiveVerification()

    if (status.verified) {
      router.push('/change-email')
    } else {
      ElMessage.info('需要验证身份')
      router.push({
        path: '/sensitive-verification',
        query: { returnTo: '/change-email' }
      })
    }
  } catch (error: any) {
    console.error('Check sensitive verification failed:', error)
    router.push({
      path: '/sensitive-verification',
      query: { returnTo: '/change-email' }
    })
  } finally {
    emailLoading.value = false
  }
}

const handleChangePassword = async () => {
  if (passwordLoading.value) return

  passwordLoading.value = true
  try {
    const status = await checkSensitiveVerification()

    if (status.verified) {
      // 已验证，直接跳转到修改密码页面
      router.push('/change-password')
    } else {
      // 未验证，跳转到验证页面
      ElMessage.info('需要验证身份')
      router.push({
        path: '/sensitive-verification',
        query: { returnTo: '/change-password' }
      })
    }
  } catch (error: any) {
    console.error('Check sensitive verification failed:', error)
    // 出错时也跳转到验证页面
    router.push({
      path: '/sensitive-verification',
      query: { returnTo: '/change-password' }
    })
  } finally {
    passwordLoading.value = false
  }
}
</script>

<style scoped>
.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 6px 0 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
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

/* 信息列表样式 */
.info-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 0 -20px;
  padding: 0;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  border-bottom: 1px solid var(--el-border-color-light);
  user-select: none;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row:hover {
  background-color: var(--el-fill-color-light);
}

.row-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.row-icon {
  font-size: 18px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.row-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  min-width: 80px;
}

.row-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: flex-end;
}

.row-value {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: right;
  flex: 1;
  word-break: break-all;
}

.row-arrow {
  font-size: 16px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.info-row:hover .row-arrow {
  color: var(--el-color-primary);
}

.info-row.loading {
  pointer-events: none;
  opacity: 0.7;
}

.loading-icon {
  animation: rotate 1s linear infinite;
  color: var(--el-color-primary);
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.row-gap {
  margin-top: 16px;
}

/* Passkey 管理样式 */
.passkey-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--el-text-color-secondary);
}

.passkey-empty {
  padding: 32px 0;
  text-align: center;
}

.passkey-empty p {
  margin: 0 0 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.empty-desc {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.passkey-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.passkey-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  transition: all 0.2s ease;
}

.passkey-item:hover {
  background: var(--el-bg-color);
  border-color: var(--el-color-primary-light-7);
}

.passkey-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.passkey-icon {
  font-size: 24px;
  color: var(--el-color-primary);
}

.passkey-info {
  flex: 1;
}

.passkey-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.passkey-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.separator {
  margin: 0 8px;
}

.passkey-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
