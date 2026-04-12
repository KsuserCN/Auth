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
                <el-skeleton-item v-else variant="text" class="row-arrow-skeleton" />
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
                <el-skeleton-item v-else variant="text" class="row-arrow-skeleton" />
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24" :lg="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Share />
            </el-icon>
            <span>第三方账号登录绑定</span>
          </div>
          <div class="oauth-list">
            <div v-for="provider in oauthProviders" :key="provider.key" class="oauth-item">
              <div class="oauth-left">
                <i class="oauth-icon" :class="provider.iconClass" aria-hidden="true"></i>
                <div class="oauth-meta">
                  <span class="oauth-name">{{ provider.label }}</span>
                  <span class="oauth-last-login">
                    {{ provider.lastLoginAt ? `最近登录：${formatDateTime(provider.lastLoginAt)}` : '暂无登录记录' }}
                  </span>
                </div>
              </div>
              <div class="oauth-right">
                <el-tag v-if="provider.bound" type="success" size="small" class="oauth-status">已绑定</el-tag>
                <el-tag v-else type="info" size="small" class="oauth-status">未绑定</el-tag>
                <el-tag v-if="!provider.supported" size="small" class="oauth-status">暂不支持</el-tag>
                <el-button v-if="provider.supported" text :type="provider.bound ? 'danger' : 'primary'" size="small"
                  :loading="provider.loading" @click="handleProviderAction(provider)">
                  {{ provider.bound ? '解绑' : '绑定' }}
                </el-button>
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
            <span>通行密钥(Passkey) 管理</span>
            <el-button type="primary" plain size="small" @click="handleAddPasskey" :loading="addLoading"
              style="margin-left: auto">
              添加 Passkey
            </el-button>
          </div>
          <div v-if="passkeyLoading" class="panel-skeleton">
            <div v-for="index in 3" :key="index" class="panel-skeleton-row">
              <div class="panel-skeleton-main">
                <el-skeleton-item variant="circle" class="panel-skeleton-icon" />
                <div class="panel-skeleton-copy">
                  <el-skeleton-item variant="text" class="panel-skeleton-title" />
                  <el-skeleton-item variant="text" class="panel-skeleton-meta" />
                </div>
              </div>
              <div class="panel-skeleton-actions">
                <el-skeleton-item variant="button" class="panel-skeleton-btn" />
                <el-skeleton-item variant="button" class="panel-skeleton-btn" />
              </div>
            </div>
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

  <!-- TOTP 管理入口 -->
  <el-row :gutter="16" class="row-gap">
    <el-col :xs="24" :lg="24">
      <el-card class="card" shadow="never">
        <div class="card-title">
          <el-icon>
            <Lock />
          </el-icon>
          <span>身份验证器(Authenticator) 管理</span>
          <el-tag class="totp-header-tag" :type="totpEnabled ? 'success' : 'info'" size="small" effect="light">
            {{ totpEnabled ? '已开启防护' : '未开启防护' }}
          </el-tag>
        </div>
        <div v-if="!totpStatusFetched" class="totp-skeleton">
          <el-skeleton-item variant="text" class="totp-skeleton-title" />
          <el-skeleton-item variant="text" class="totp-skeleton-desc" />
          <div class="totp-skeleton-metrics">
            <el-skeleton-item variant="text" class="totp-skeleton-metric" />
            <el-skeleton-item variant="text" class="totp-skeleton-metric" />
          </div>
          <div class="totp-skeleton-actions">
            <el-skeleton-item variant="text" class="totp-skeleton-action" />
            <el-skeleton-item variant="text" class="totp-skeleton-action" />
          </div>
        </div>
        <div v-else class="totp-panel">
          <div class="totp-hero" :class="{ enabled: totpEnabled }">
            <div class="totp-hero-icon-wrap">
              <el-icon class="totp-hero-icon">
                <Lock />
              </el-icon>
            </div>
            <div class="totp-hero-content">
              <h3 class="totp-hero-title">{{ totpEnabled ? '账户防护已开启' : '立即开启双因素认证' }}</h3>
              <p class="totp-hero-desc">
                {{
                  totpEnabled
                    ? '登录时将额外验证动态码，账户安全性更高。'
                    : '使用身份验证器应用生成动态码，为账户增加一道关键防线。'
                }}
              </p>
            </div>
            <div class="totp-hero-action">
              <el-button v-if="!totpEnabled" type="primary" size="small" @click="handleEnableTotp">
                启用 TOTP
              </el-button>
              <el-button v-else type="danger" plain size="small" @click="handleDisableTotp">
                禁用 TOTP
              </el-button>
            </div>
          </div>

          <div class="totp-metrics">
            <div class="totp-metric-item">
              <div class="totp-metric-label">状态</div>
              <div class="totp-metric-value">
                <el-tag v-if="totpEnabled" type="success" size="small" effect="dark">已启用</el-tag>
                <el-tag v-else type="info" size="small" effect="light">未启用</el-tag>
              </div>
            </div>
            <div v-if="totpEnabled" class="totp-metric-item">
              <div class="totp-metric-label">剩余恢复码</div>
              <div class="totp-metric-value recovery-count-wrap">
                <span class="totp-recovery-count">{{ recoveryCodesCount }} 个</span>
                <el-tag v-if="recoveryCodesCount < 3" type="warning" size="small" effect="light">
                  即将用完
                </el-tag>
              </div>
            </div>
          </div>

          <div v-if="totpEnabled" class="totp-quick-actions">
            <div class="totp-action-item">
              <div class="totp-action-info">
                <div class="totp-action-title">查看恢复码</div>
                <div class="totp-action-desc">用于设备丢失或无法获取动态码时登录账户。</div>
              </div>
              <div class="totp-action-buttons">
                <el-button text size="small" @click="handleViewRecoveryCodes">查看恢复码</el-button>
              </div>
            </div>
            <div class="totp-action-item">
              <div class="totp-action-info">
                <div class="totp-action-title">刷新恢复码</div>
                <div class="totp-action-desc">旧恢复码会立即失效，请在生成后立刻保存新恢复码。</div>
              </div>
              <div class="totp-action-buttons">
                <el-button text type="warning" size="small" @click="handleRegenerateRecoveryCodes"
                  :disabled="recoveryCodesCount >= 10">
                  生成新恢复码
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </el-col>
  </el-row>

  <!-- 恢复码列表弹窗 -->
  <el-dialog v-model="showRecoveryCodesDialog" title="恢复码列表" width="420px">
    <div v-if="recoveryCodesLoading" class="dialog-skeleton">
      <el-skeleton-item variant="text" class="dialog-skeleton-alert" />
      <div class="dialog-skeleton-grid">
        <el-skeleton-item v-for="index in 8" :key="index" variant="text" class="dialog-skeleton-code" />
      </div>
    </div>
    <div v-else>
      <el-alert title="请妥善保管您的恢复码，每个码只能使用一次" type="warning" :closable="false" style="margin-bottom: 16px" />
      <div v-if="recoveryCodesList.length === 0" class="passkey-empty">
        <p>没有可用的恢复码</p>
        <p class="empty-desc">所有恢复码都已使用，请重新生成</p>
      </div>
      <div v-else>
        <div class="recovery-codes-grid">
          <div v-for="code in recoveryCodesList" :key="code" class="recovery-code-item">
            {{ code }}
          </div>
        </div>
        <div style="margin-top: 16px; display: flex; gap: 8px;">
          <el-button size="small" @click="copyRecoveryCodes(recoveryCodesList)">复制恢复码</el-button>
          <el-button size="small" @click="downloadRecoveryCodes(recoveryCodesList)">下载恢复码</el-button>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="showRecoveryCodesDialog = false">关闭</el-button>
    </template>
  </el-dialog>

  <!-- 新恢复码弹窗 -->
  <el-dialog v-model="showRegenerateDialog" title="新的恢复码" width="450px" :close-on-click-modal="false">
    <div v-if="regenerateLoading" class="dialog-skeleton">
      <el-skeleton-item variant="text" class="dialog-skeleton-alert" />
      <div class="dialog-skeleton-grid">
        <el-skeleton-item v-for="index in 8" :key="index" variant="text" class="dialog-skeleton-code" />
      </div>
    </div>
    <div v-else>
      <el-alert title="新的恢复码已生成！请立即保存，关闭后将无法再次查看完整的恢复码。" type="success" :closable="false" style="margin-bottom: 16px" />
      <div class="recovery-codes-grid">
        <div v-for="code in newRecoveryCodes" :key="code" class="recovery-code-item">
          {{ code }}
        </div>
      </div>
      <div style="margin-top: 16px; display: flex; gap: 8px;">
        <el-button @click="copyRecoveryCodes(newRecoveryCodes)">复制恢复码</el-button>
        <el-button @click="downloadRecoveryCodes(newRecoveryCodes)">下载恢复码</el-button>
      </div>
    </div>
    <template #footer>
      <el-button type="primary" @click="showRegenerateDialog = false">已保存，关闭</el-button>
    </template>
  </el-dialog>

  <SensitiveVerificationDialog v-model="sensitiveDialogVisible" @success="handleSensitiveVerificationSuccess"
    @cancel="handleSensitiveVerificationCancel" />
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
  Cpu,
  Share,
} from '@element-plus/icons-vue'
import {
  checkSensitiveVerification,
  getPasskeyList,
  deletePasskey,
  renamePasskey,
  type PasskeyListItem,
  getTotpStatus,
  getRecoveryCodes,
  regenerateRecoveryCodes,
  disableTotp,
  buildGoogleAuthorizationUrl,
  buildGithubAuthorizationUrl,
  buildMicrosoftAuthorizationUrl,
  buildQQAuthorizationUrl,
  unbindGoogle,
  unbindGithub,
  unbindMicrosoft,
  unbindQQ,
  getOAuthAccountsStatus,
  type OAuthAccountStatusItem,
} from '@/api/auth'
import { isWebAuthnSupported } from '@/utils/webauthn'
import SensitiveVerificationDialog from '@/components/SensitiveVerificationDialog.vue'

const router = useRouter()
const userStore = useUserStore()

const userEmail = computed(() => userStore.user?.email || '—')
const emailLoading = ref(false)
const passwordLoading = ref(false)
type OAuthProviderItem = {
  key: 'wechat' | 'qq' | 'github' | 'microsoft' | 'google'
  label: string
  iconClass: string
  supported: boolean
  bound: boolean
  lastLoginAt: string | null
  loading: boolean
}

const oauthProviders = ref<OAuthProviderItem[]>([
  { key: 'wechat', label: '微信', iconClass: 'fa-brands fa-weixin', supported: false, bound: false, lastLoginAt: null, loading: false },
  { key: 'qq', label: 'QQ', iconClass: 'fa-brands fa-qq', supported: true, bound: false, lastLoginAt: null, loading: false },
  { key: 'github', label: 'GitHub', iconClass: 'fa-brands fa-github', supported: true, bound: false, lastLoginAt: null, loading: false },
  { key: 'microsoft', label: '微软', iconClass: 'fa-brands fa-microsoft', supported: true, bound: false, lastLoginAt: null, loading: false },
  { key: 'google', label: 'Google', iconClass: 'fa-brands fa-google', supported: true, bound: false, lastLoginAt: null, loading: false },
])

// Passkey 相关状态
const isPasskeySupported = ref(false)
const passkeyList = ref<PasskeyListItem[]>([])
const passkeyLoading = ref(false)
const addLoading = ref(false)

// TOTP 相关状态
const totpEnabled = ref(false)
const recoveryCodesCount = ref(0)
const totpStatusFetched = ref(false)

const showRecoveryCodesDialog = ref(false)
const recoveryCodesList = ref<string[]>([])
const recoveryCodesLoading = ref(false)

const showRegenerateDialog = ref(false)
const newRecoveryCodes = ref<string[]>([])
const regenerateLoading = ref(false)
const sensitiveDialogVisible = ref(false)
let pendingSensitiveAction: null | (() => Promise<void>) = null

onMounted(async () => {
  await userStore.fetchUserInfo()
  await loadOAuthStatus()
  isPasskeySupported.value = isWebAuthnSupported()

  if (isPasskeySupported.value) {
    await loadPasskeyList()
  }

  await fetchTotpStatus()

})

const loadOAuthStatus = async () => {
  try {
    const accounts = await getOAuthAccountsStatus()
    const statusMap = new Map<OAuthAccountStatusItem['provider'], OAuthAccountStatusItem>(
      accounts.map((item) => [item.provider, item]),
    )

    oauthProviders.value = oauthProviders.value.map((provider) => {
      const status = statusMap.get(provider.key)
      if (!status) {
        return {
          ...provider,
          bound: false,
          lastLoginAt: null,
        }
      }

      return {
        ...provider,
        bound: status.bound,
        lastLoginAt: status.lastLoginAt,
      }
    })
  } catch (error) {
    console.warn('Load OAuth accounts status failed:', error)
  }
}

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

const fetchTotpStatus = async () => {
  try {
    const data = await getTotpStatus()
    totpEnabled.value = data.enabled
    recoveryCodesCount.value = data.recoveryCodesCount
    totpStatusFetched.value = true
  } catch (error) {
    console.error('获取 TOTP 状态失败:', error)
    totpStatusFetched.value = true
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

const formatDateTime = (dateStr: string) => {
  const date = new Date(dateStr)

  if (Number.isNaN(date.getTime())) {
    return '时间未知'
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
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
  addLoading.value = true

  try {
    await runWithSensitiveVerification(async () => {
      await router.push('/add-passkey')
    })
  } catch (error: unknown) {
    console.error('Handle add passkey failed:', error)
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
    await runWithSensitiveVerification(async () => {
      await router.push('/change-email')
    })
  } catch (error: unknown) {
    console.error('Handle change email failed:', error)
  } finally {
    emailLoading.value = false
  }
}

const handleChangePassword = async () => {
  if (passwordLoading.value) return

  passwordLoading.value = true
  try {
    await runWithSensitiveVerification(async () => {
      await router.push('/change-password')
    })
  } catch (error: unknown) {
    console.error('Handle change password failed:', error)
  } finally {
    passwordLoading.value = false
  }
}

const runWithSensitiveVerification = async (action: () => Promise<void>) => {
  try {
    const status = await checkSensitiveVerification()
    if (status.verified) {
      await action()
      return
    }

    ElMessage.info('需要验证身份')
    pendingSensitiveAction = action
    sensitiveDialogVisible.value = true
  } catch (error) {
    console.error('Check sensitive verification failed:', error)
    ElMessage.info('需要验证身份')
    pendingSensitiveAction = action
    sensitiveDialogVisible.value = true
  }
}

const handleSensitiveVerificationSuccess = async () => {
  const action = pendingSensitiveAction
  pendingSensitiveAction = null
  if (!action) return

  try {
    await action()
  } catch (error) {
    console.error('Run pending sensitive action failed:', error)
  }
}

const handleSensitiveVerificationCancel = () => {
  pendingSensitiveAction = null
}

const handleEnableTotp = async () => {
  await runWithSensitiveVerification(async () => {
    await router.push('/totp')
  })
}

const handleViewRecoveryCodes = async () => {
  await runWithSensitiveVerification(async () => {
    recoveryCodesLoading.value = true
    showRecoveryCodesDialog.value = true
    try {
      recoveryCodesList.value = await getRecoveryCodes()
    } catch (error) {
      console.error('获取回复码失败:', error)
      ElMessage.error('获取恢复码失败')
      showRecoveryCodesDialog.value = false
    } finally {
      recoveryCodesLoading.value = false
    }
  })
}

const handleRegenerateRecoveryCodes = async () => {
  await runWithSensitiveVerification(async () => {
    try {
      await ElMessageBox.confirm(
        '重新生成恢复码会删除所有旧的恢复码。请确保您已保存新的恢复码。',
        '确认重新生成',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        }
      )

      regenerateLoading.value = true
      showRegenerateDialog.value = true
      try {
        newRecoveryCodes.value = await regenerateRecoveryCodes()
        ElMessage.success('恢复码已重新生成')
        await fetchTotpStatus()
      } catch (error) {
        console.error('重新生成回复码失败:', error)
        ElMessage.error('重新生成失败')
        showRegenerateDialog.value = false
      } finally {
        regenerateLoading.value = false
      }
    } catch {
      // 用户取消
    }
  })
}

const handleDisableTotp = async () => {
  await runWithSensitiveVerification(async () => {
    try {
      await ElMessageBox.confirm(
        '禁用 TOTP 后，您将无法使用双因素认证。是否继续？',
        '确认禁用',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        }
      )

      await disableTotp()
      ElMessage.success('TOTP 已禁用')
      await fetchTotpStatus()
    } catch (error) {
      if (error !== 'cancel') {
        console.error('禁用 TOTP 失败:', error)
        ElMessage.error('禁用失败')
      }
    }
  })
}

const copyRecoveryCodes = (codes: string[]) => {
  const text = codes.join('\n')
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

const downloadRecoveryCodes = (codes: string[]) => {
  const text = 'TOTP 恢复码\n' +
    new Date().toLocaleString() + '\n' +
    '每个码只能使用一次\n\n' +
    codes.join('\n')

  const blob = new Blob([text], { type: 'text/plain' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'recovery-codes.txt'
  a.click()
  window.URL.revokeObjectURL(url)
}

const generateRandomString = (): string => {
  const array = new Uint8Array(16)
  crypto.getRandomValues(array)
  return Array.from(array, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

const toBase64Url = (bytes: Uint8Array): string => {
  const base64 = btoa(String.fromCharCode(...bytes))
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

const generatePkcePair = async (): Promise<{ codeVerifier: string; codeChallenge: string }> => {
  const verifierBytes = new Uint8Array(32)
  crypto.getRandomValues(verifierBytes)
  const codeVerifier = toBase64Url(verifierBytes)

  const hashed = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(codeVerifier))
  const codeChallenge = toBase64Url(new Uint8Array(hashed))

  return { codeVerifier, codeChallenge }
}

const handleQQBind = async (provider: OAuthProviderItem) => {
  if (provider.loading) return

  provider.loading = true
  try {
    const randomString = generateRandomString()
    const debugState = import.meta.env.VITE_DEBUG_STATE || 'dev'
    const state = `${randomString};bind;${debugState}`

    sessionStorage.setItem('qq_oauth_state', state)
    window.location.href = buildQQAuthorizationUrl(state)
  } catch (error) {
    console.error('QQ bind failed:', error)
    ElMessage.error('QQ 绑定跳转失败，请重试')
    provider.loading = false
  }
}

const handleQQUnbind = async (provider: OAuthProviderItem) => {
  if (provider.loading) return
  await runWithSensitiveVerification(async () => {
    try {
      await ElMessageBox.confirm('解绑后您将无法使用 QQ 快速登录，是否继续？', '确认解绑', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }

    provider.loading = true
    try {
      await unbindQQ()
      provider.bound = false
      provider.lastLoginAt = null
      ElMessage.success('QQ 解绑成功')

      // 与服务端状态对齐
      await loadOAuthStatus()
    } catch (error) {
      console.error('QQ unbind failed:', error)
      ElMessage.error('QQ 解绑失败，请稍后重试')
    } finally {
      provider.loading = false
    }
  })
}

const handleGithubBind = async (provider: OAuthProviderItem) => {
  if (provider.loading) return

  provider.loading = true
  try {
    const randomString = generateRandomString()
    const debugState = import.meta.env.VITE_DEBUG_STATE || 'dev'
    const state = `${randomString};bind;${debugState}`

    sessionStorage.setItem('github_oauth_state', state)
    window.location.href = buildGithubAuthorizationUrl(state)
  } catch (error) {
    console.error('GitHub bind failed:', error)
    ElMessage.error('GitHub 绑定跳转失败，请重试')
    provider.loading = false
  }
}

const handleGithubUnbind = async (provider: OAuthProviderItem) => {
  if (provider.loading) return
  await runWithSensitiveVerification(async () => {
    try {
      await ElMessageBox.confirm('解绑后您将无法使用 GitHub 快速登录，是否继续？', '确认解绑', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }

    provider.loading = true
    try {
      await unbindGithub()
      provider.bound = false
      provider.lastLoginAt = null
      ElMessage.success('GitHub 解绑成功')

      await loadOAuthStatus()
    } catch (error) {
      console.error('GitHub unbind failed:', error)
      ElMessage.error('GitHub 解绑失败，请稍后重试')
    } finally {
      provider.loading = false
    }
  })
}

const handleMicrosoftBind = async (provider: OAuthProviderItem) => {
  if (provider.loading) return

  provider.loading = true
  try {
    const randomString = generateRandomString()
    const debugState = import.meta.env.VITE_DEBUG_STATE || 'dev'
    const state = `${randomString};bind;${debugState}`
    const { codeVerifier, codeChallenge } = await generatePkcePair()

    sessionStorage.setItem('microsoft_oauth_state', state)
    sessionStorage.setItem('microsoft_oauth_code_verifier', codeVerifier)
    window.location.href = buildMicrosoftAuthorizationUrl(state, codeChallenge)
  } catch (error) {
    console.error('Microsoft bind failed:', error)
    ElMessage.error('Microsoft 绑定跳转失败，请重试')
    provider.loading = false
  }
}

const handleMicrosoftUnbind = async (provider: OAuthProviderItem) => {
  if (provider.loading) return
  await runWithSensitiveVerification(async () => {
    try {
      await ElMessageBox.confirm('解绑后您将无法使用 Microsoft 快速登录，是否继续？', '确认解绑', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }

    provider.loading = true
    try {
      await unbindMicrosoft()
      provider.bound = false
      provider.lastLoginAt = null
      ElMessage.success('Microsoft 解绑成功')

      await loadOAuthStatus()
    } catch (error) {
      console.error('Microsoft unbind failed:', error)
      ElMessage.error('Microsoft 解绑失败，请稍后重试')
    } finally {
      provider.loading = false
    }
  })
}

const handleGoogleBind = async (provider: OAuthProviderItem) => {
  if (provider.loading) return

  provider.loading = true
  try {
    const randomString = generateRandomString()
    const debugState = import.meta.env.VITE_DEBUG_STATE || 'dev'
    const state = `${randomString};bind;${debugState}`

    sessionStorage.setItem('google_oauth_state', state)
    window.location.href = buildGoogleAuthorizationUrl(state)
  } catch (error) {
    console.error('Google bind failed:', error)
    ElMessage.error('Google 绑定跳转失败，请重试')
    provider.loading = false
  }
}

const handleGoogleUnbind = async (provider: OAuthProviderItem) => {
  if (provider.loading) return
  await runWithSensitiveVerification(async () => {
    try {
      await ElMessageBox.confirm('解绑后您将无法使用 Google 快速登录，是否继续？', '确认解绑', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }

    provider.loading = true
    try {
      await unbindGoogle()
      provider.bound = false
      provider.lastLoginAt = null
      ElMessage.success('Google 解绑成功')

      await loadOAuthStatus()
    } catch (error) {
      console.error('Google unbind failed:', error)
      ElMessage.error('Google 解绑失败，请稍后重试')
    } finally {
      provider.loading = false
    }
  })
}

const handleProviderAction = async (provider: OAuthProviderItem) => {
  if (!provider.supported || provider.loading) return

  if (provider.key === 'qq') {
    if (provider.bound) {
      await handleQQUnbind(provider)
    } else {
      await handleQQBind(provider)
    }
    return
  }

  if (provider.key === 'github') {
    if (provider.bound) {
      await handleGithubUnbind(provider)
    } else {
      await handleGithubBind(provider)
    }
    return
  }

  if (provider.key === 'microsoft') {
    if (provider.bound) {
      await handleMicrosoftUnbind(provider)
    } else {
      await handleMicrosoftBind(provider)
    }
    return
  }

  if (provider.key === 'google') {
    if (provider.bound) {
      await handleGoogleUnbind(provider)
    } else {
      await handleGoogleBind(provider)
    }
    return
  }

  ElMessage.info(`${provider.label} 暂不支持`)
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

.info-row.is-static {
  cursor: default;
}

.info-row.is-static:hover {
  background-color: transparent;
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

.row-arrow-skeleton {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  flex-shrink: 0;
}

.row-gap {
  margin-top: 16px;
}

/* Passkey 管理样式 */
.panel-skeleton,
.totp-skeleton {
  display: flex;
  flex-direction: column;
}

.panel-skeleton {
  gap: 12px;
}

.panel-skeleton-row,
.panel-skeleton-main,
.panel-skeleton-actions {
  display: flex;
  align-items: center;
}

.panel-skeleton-row {
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}

.panel-skeleton-main {
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.panel-skeleton-icon {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
}

.panel-skeleton-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.panel-skeleton-title {
  width: 180px;
  max-width: 100%;
  height: 15px;
}

.panel-skeleton-meta {
  width: 220px;
  max-width: 100%;
  height: 13px;
}

.panel-skeleton-actions {
  gap: 8px;
  flex-shrink: 0;
}

.panel-skeleton-btn {
  width: 52px;
  height: 28px;
}

.totp-skeleton {
  gap: 14px;
}

.totp-skeleton-title {
  width: 180px;
  height: 16px;
}

.totp-skeleton-desc {
  width: 72%;
  height: 14px;
}

.totp-skeleton-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.totp-skeleton-metric {
  width: 100%;
  height: 56px;
  border-radius: 10px;
}

.totp-skeleton-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.totp-skeleton-action {
  width: 100%;
  height: 52px;
  border-radius: 10px;
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

/* TOTP 管理样式 */
.totp-header-tag {
  margin-left: auto;
}

.totp-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.totp-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-light);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--el-color-primary) 10%, transparent), transparent 58%),
    var(--el-fill-color-light);
}

.totp-hero.enabled {
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--el-color-success) 12%, transparent), transparent 58%),
    var(--el-fill-color-light);
}

.totp-hero-icon-wrap {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--el-color-primary) 18%, transparent);
  flex-shrink: 0;
}

.totp-hero.enabled .totp-hero-icon-wrap {
  background: color-mix(in srgb, var(--el-color-success) 18%, transparent);
}

.totp-hero-icon {
  font-size: 20px;
  color: var(--el-color-primary);
}

.totp-hero.enabled .totp-hero-icon {
  color: var(--el-color-success);
}

.totp-hero-content {
  flex: 1;
  min-width: 0;
}

.totp-hero-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.totp-hero-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.totp-hero-action {
  flex-shrink: 0;
}

.totp-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.totp-metric-item {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color-page);
}

.totp-metric-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.totp-metric-value {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.recovery-count-wrap {
  flex-wrap: wrap;
}

.totp-recovery-count {
  font-size: 18px;
  line-height: 1;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.totp-quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.totp-action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.totp-action-info {
  min-width: 0;
}

.totp-action-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.totp-action-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.totp-action-buttons {
  flex-shrink: 0;
}

.dialog-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0;
}

.dialog-skeleton-alert {
  width: 88%;
  height: 16px;
}

.dialog-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.dialog-skeleton-code {
  width: 100%;
  height: 40px;
  border-radius: 4px;
}

.recovery-codes-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.recovery-code-item {
  padding: 10px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  text-align: center;
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.oauth-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.oauth-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.oauth-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.oauth-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.oauth-name {
  font-size: 14px;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.oauth-last-login {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.oauth-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.oauth-status {
  min-width: 56px;
  text-align: center;
}

.oauth-icon {
  width: 20px;
  text-align: center;
  font-size: 18px;
  color: var(--el-text-color-secondary);
  opacity: 0.85;
}

:global(html.dark) .oauth-icon {
  opacity: 0.95;
}

@media (max-width: 768px) {
  .oauth-item {
    padding: 10px;
  }

  .oauth-right {
    gap: 4px;
  }

  .totp-hero {
    flex-wrap: wrap;
  }

  .totp-hero-action {
    width: 100%;
  }

  .totp-hero-action :deep(.el-button) {
    width: 100%;
  }

  .totp-metrics {
    grid-template-columns: 1fr;
  }

  .totp-action-item {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .totp-action-buttons :deep(.el-button) {
    width: 100%;
  }
}
</style>
