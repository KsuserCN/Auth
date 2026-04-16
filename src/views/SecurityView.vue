<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">安全性</h1>
        <p class="page-subtitle">管理您的账户安全设置和登录认证方式</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Lock />
            </el-icon>
            <span>两步验证</span>
          </div>
          <div class="security-list">
            <div class="security-item">
              <div class="item-left">
                <div class="item-header">
                  <span class="item-title">邮箱验证码</span>
                </div>
                <p class="item-desc">使用发送到您邮箱的验证码进行两步验证</p>
              </div>
              <div class="item-right">
                <el-tag type="success">已启用</el-tag>
                <el-button plain size="small">管理</el-button>
              </div>
            </div>
            <el-divider />
            <div class="security-item">
              <div class="item-left">
                <div class="item-header">
                  <span class="item-title">通行密钥(Passkey)</span>
                </div>
                <p class="item-desc">使用生物识别或设备密码进行两步验证</p>
              </div>
              <div class="item-right">
                <el-tag v-if="passkeyLoading" type="info">检测中</el-tag>
                <el-tag v-else-if="passkeyEnabled" type="success">已启用</el-tag>
                <el-tag v-else type="info">未启用</el-tag>
                <el-button type="primary" plain size="small" @click="goToLoginOptions">
                  {{ passkeyEnabled ? '管理' : '前往设置' }}
                </el-button>
              </div>
            </div>
            <el-divider />
            <div class="security-item">
              <div class="item-left">
                <div class="item-header">
                  <span class="item-title">身份验证器(Authenticator)</span>
                </div>
                <p class="item-desc">使用身份验证器生成的动态验证码进行两步验证</p>
              </div>
              <div class="item-right">
                <el-tag v-if="totpLoading" type="info">检测中</el-tag>
                <el-tag v-else-if="totpEnabled" type="success">已启用</el-tag>
                <el-tag v-else type="info">未启用</el-tag>
                <el-button type="primary" plain size="small" @click="goToLoginOptions">
                  {{ totpEnabled ? '管理' : '前往设置' }}
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Key />
            </el-icon>
            <span>安全开关</span>
          </div>
          <div class="toggle-list">
            <div class="toggle-item">
              <div class="toggle-left">
                <span class="toggle-title">开启 MFA</span>
                <span class="toggle-desc">登录时启用多因素验证</span>
              </div>
              <el-switch v-model="mfaEnabled" />
            </div>
            <div class="toggle-item">
              <div class="toggle-left">
                <span class="toggle-title">开启异地登录检测</span>
                <span class="toggle-desc">检测异常地点登录并提醒</span>
              </div>
              <el-switch v-model="geoLoginEnabled" />
            </div>
            <div class="toggle-item">
              <div class="toggle-left">
                <span class="toggle-title">敏感操作邮件提醒</span>
                <span class="toggle-desc">敏感操作时向邮箱发送通知</span>
              </div>
              <el-switch v-model="sensitiveEmailEnabled" />
            </div>
          </div>

          <div class="preference-list">
            <div class="preference-item">
              <div class="toggle-left">
                <span class="toggle-title">登录 MFA 偏好</span>
                <span class="toggle-desc">仅在开启 MFA 时生效，默认优先跳转该方式</span>
              </div>
              <el-select v-model="preferredMfaMethod" placeholder="选择登录 MFA 偏好"
                :disabled="!mfaEnabled || settingsUpdating" class="preference-select"
                @change="handlePreferredMfaMethodChange">
                <el-option label="Passkey" value="passkey" :disabled="!passkeyEnabled" />
                <el-option label="TOTP" value="totp" :disabled="!totpEnabled" />
                <el-option label="手机扫码" value="qr" />
              </el-select>
            </div>

            <div class="preference-item">
              <div class="toggle-left">
                <span class="toggle-title">敏感操作验证偏好</span>
                <span class="toggle-desc">仅影响默认验证入口，仍可手动切换其他方式</span>
              </div>
              <el-select v-model="preferredSensitiveMethod" placeholder="选择敏感验证偏好" :disabled="settingsUpdating"
                class="preference-select" @change="handlePreferredSensitiveMethodChange">
                <el-option label="密码" value="password" />
                <el-option label="邮箱验证码" value="email-code" />
                <el-option label="Passkey" value="passkey" :disabled="!passkeyEnabled" />
                <el-option label="TOTP" value="totp" :disabled="!totpEnabled" />
              </el-select>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 活跃会话卡片已移除按需求 -->

    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24">
        <el-card class="card" shadow="never">
          <div class="card-title card-title-between">
            <div class="title-left">
              <el-icon>
                <Document />
              </el-icon>
              <span>近期敏感操作</span>
            </div>
            <div class="title-actions">
              <el-tag v-if="sensitiveLogsTotal" type="info" effect="plain">
                共 {{ sensitiveLogsTotal }} 条
              </el-tag>
              <el-button text size="small" :loading="sensitiveLogsLoading" @click="loadSensitiveLogs">
                刷新
              </el-button>
            </div>
          </div>

          <!-- 查询表单 -->
          <div style="margin-bottom: 12px; display:flex; gap:12px; align-items:center; flex-wrap:wrap">
            <el-select v-model="query.operationType" placeholder="操作类型" clearable style="width:180px">
              <el-option v-for="(label, key) in operationTypeLabels" :key="key" :label="label" :value="key" />
            </el-select>

            <el-select v-model="query.result" placeholder="结果" clearable style="width:140px">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILURE" />
            </el-select>

            <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
              end-placeholder="结束日期" value-format="yyyy-MM-dd" unlink-panels style="width:320px" />

            <el-button type="primary" @click="applyQuery">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </div>

          <el-table :data="sensitiveLogs" class="modern-table" size="small"
            :empty-text="sensitiveLogsLoading ? '加载中...' : '暂无记录'" @sort-change="handleSortChange">
            <el-table-column label="操作" min-width="180">
              <template #default="{ row }">
                <div class="log-primary">
                  <span class="log-title">{{ getOperationTitle(row.operationType) }}</span>
                  <el-tag
                    v-for="(tag, idx) in getOperationTags(row)"
                    :key="`${row.id}-${idx}-${tag}`"
                    size="small"
                    effect="plain"
                  >
                    {{ tag }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="结果" min-width="120">
              <template #default="{ row }">
                <div class="log-secondary">
                  <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
                    {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
                  </el-tag>
                  <span v-if="row.result === 'FAILURE'" class="log-sub">
                    {{ row.failureReason || '未知原因' }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="位置 / IP" min-width="160">
              <template #default="{ row }">
                <div class="log-secondary">
                  <span>{{ row.ipLocation || '未知位置' }}</span>
                  <span class="log-sub">{{ row.ipAddress }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="设备" min-width="160">
              <template #default="{ row }">
                <div class="log-secondary">
                  <span class="log-device">
                    <i :class="getDeviceIconClass(row.deviceType)" class="log-device-icon" aria-hidden="true"></i>
                    {{ row.deviceType || '未知设备' }}
                  </span>
                  <span class="log-sub log-browser">
                    <i :class="getBrowserIconClass(row.browser)" class="log-browser-icon" aria-hidden="true"></i>
                    {{ row.browser || '未知浏览器' }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="风险" min-width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="getRiskTagType(row.riskScore)" size="small" effect="plain">
                  {{ row.riskScore }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="160">
              <template #default="{ row }">
                {{ formatSensitiveTime(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>

          <div style="margin-top:12px; display:flex; justify-content:flex-end;">
            <el-pagination background :page-size="query.pageSize" :current-page="query.page" :total="sensitiveLogsTotal"
              layout="total, sizes, prev, pager, next, jumper" :page-sizes="[6, 12, 24, 48]"
              @current-change="handlePageChange" @size-change="handlePageSizeChange" />
          </div>
        </el-card>
      </el-col>
    </el-row>

	    <el-row :gutter="16" class="row-gap">
	      <el-col :xs="24">
	        <DangerZoneCard title="危险操作区域" :icon="WarningFilled">
	          <div class="danger-zone-item">
	            <div class="danger-zone-item-left">
	              <div class="danger-zone-item-title danger">注销账号</div>
	              <p class="danger-zone-item-desc">永久删除您的账号和所有相关数据。此操作不可逆转。</p>
	            </div>
	            <div class="danger-zone-item-right">
	              <el-button type="danger" plain size="small" @click="handleDeleteAccount">注销</el-button>
	            </div>
	          </div>
	        </DangerZoneCard>
	      </el-col>
	    </el-row>

    <SensitiveVerificationDialog v-model="sensitiveDialogVisible" @success="handleSensitiveVerificationSuccess"
      @cancel="handleSensitiveVerificationCancel" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
	import {
	  Key,
	  Lock,
	  Delete,
	  Document,
	  WarningFilled,
	} from '@element-plus/icons-vue'
import {
  checkSensitiveVerification,
  getPasskeyList,
  getSensitiveLogs,
  getTotpStatus,
  getUserInfo,
  updateUserSetting,
  type SensitiveLogItem,
  type SensitiveLoginMethod,
  type SensitiveOperationType,
} from '@/api/auth'
	import SensitiveVerificationDialog from '@/components/SensitiveVerificationDialog.vue'
	import DangerZoneCard from '@/components/DangerZoneCard.vue'

const router = useRouter()

const passkeyEnabled = ref(false)
const totpEnabled = ref(false)
const passkeyLoading = ref(true)
const totpLoading = ref(true)
const mfaEnabled = ref(false)
const preferredMfaMethod = ref<'totp' | 'passkey' | 'qr'>('totp')
const preferredSensitiveMethod = ref<'password' | 'email-code' | 'passkey' | 'totp'>('password')
const committedPreferredMfaMethod = ref<'totp' | 'passkey' | 'qr'>('totp')
const committedPreferredSensitiveMethod = ref<'password' | 'email-code' | 'passkey' | 'totp'>('password')
const geoLoginEnabled = ref(false)
const sensitiveEmailEnabled = ref(false)
const settingsReady = ref(false)
const settingsUpdating = ref(false)
const settingsRollbackGuard = ref<{
  mfaEnabled: boolean
  detectUnusualLogin: boolean
  notifySensitiveActionEmail: boolean
}>({
  mfaEnabled: false,
  detectUnusualLogin: false,
  notifySensitiveActionEmail: false,
})
const sensitiveLogs = ref<SensitiveLogItem[]>([])
const sensitiveLogsLoading = ref(false)
const sensitiveLogsTotal = ref(0)
const sensitiveDialogVisible = ref(false)
let pendingSensitiveAction: null | (() => Promise<void>) = null

// 查询与分页/排序状态
const query = ref<{ page: number; pageSize: number; startDate?: string; endDate?: string; operationType?: string | null; result?: string | null; sortBy?: string | null; sortOrder?: 'asc' | 'desc' | null }>({
  page: 1,
  pageSize: 6,
  startDate: undefined,
  endDate: undefined,
  operationType: undefined,
  result: undefined,
  sortBy: undefined,
  sortOrder: undefined,
})

const dateRange = ref<string[] | null>(null)

const operationTypeLabels: Record<SensitiveOperationType, string> = {
  REGISTER: '注册',
  LOGIN: '登录',
  SENSITIVE_VERIFY: '敏感验证',
  CHANGE_PASSWORD: '修改密码',
  CHANGE_EMAIL: '修改邮箱',
  ADD_PASSKEY: '新增 Passkey',
  DELETE_PASSKEY: '删除 Passkey',
  ENABLE_TOTP: '启用 TOTP',
  DISABLE_TOTP: '禁用 TOTP',
}

const loginMethodLabels: Record<SensitiveLoginMethod, string> = {
  PASSWORD: '密码登录',
  PASSWORD_MFA: '密码+二步验证',
  EMAIL: '验证码登录',
  EMAIL_CODE: '验证码登录',
  EMAIL_CODE_MFA: '验证码+二步验证',
  PASSKEY: 'Passkey登录',
  PASSKEY_MFA: 'Passkey+二步验证',
  QR: '扫码登录',
  QR_MFA: '扫码+二步验证',
  QQ: 'QQ',
  GITHUB: 'GitHub',
  MICROSOFT: 'Microsoft',
  GOOGLE: 'Google',
  WECHAT: '微信',
  WEIXIN: '微信',
  MFA: 'MFA',
  BRIDGE: '桥接登录',
  BRIDGE_FROM_DESKTOP: '电脑端桥接',
  BRIDGE_FROM_WEB: '网页端桥接',
}

const formatSensitiveTime = (value?: string | null) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const getOperationLabel = (op: unknown) => {
  return operationTypeLabels[(op as SensitiveOperationType)] || String(op ?? '-')
}

const normalizeLoginTokenLabel = (token: string) => {
  const key = token.trim().toUpperCase()
  if (!key) return ''
  if (key === 'PASSKEY') return 'Passkey'
  if (key === 'MFA') return 'MFA'
  if (key === 'PASSWORD') return '密码'
  if (key === 'EMAIL') return '验证码'
  if (key === 'EMAIL_CODE') return '验证码'
  if (key === 'TOTP') return 'TOTP'
  if (key === 'QR') return '扫码'
  if (key === 'GOOGLE') return 'Google'
  if (key === 'GITHUB') return 'GitHub'
  if (key === 'MICROSOFT') return 'Microsoft'
  if (key === 'QQ') return 'QQ'
  if (key === 'WECHAT' || key === 'WEIXIN') return '微信'
  if (key === 'BRIDGE') return '桥接登录'
  if (key === 'BRIDGE_FROM_DESKTOP') return '电脑端桥接'
  if (key === 'BRIDGE_FROM_WEB') return '网页端桥接'
  return token.trim()
}

const getLoginMethodTags = (row: SensitiveLogItem): string[] => {
  const fromArray = Array.isArray(row.loginMethods)
    ? row.loginMethods
      .map((item) => normalizeLoginTokenLabel(String(item)))
      .filter(Boolean)
    : []
  if (fromArray.length > 0) {
    return [...new Set(fromArray)]
  }

  const m = row.loginMethod
  if (m == null) return []

  const raw = String(m).trim()
  if (!raw) return []
  if (raw.toLowerCase() === 'null') return []

  const normalized = raw.toUpperCase()

  // Old backend format: "PASSKEY_MFA"
  if (normalized in loginMethodLabels) {
    const tokens: string[] = []
    if (normalized.endsWith('_MFA')) {
      tokens.push(normalized.slice(0, -4))
      tokens.push('MFA')
    } else {
      tokens.push(normalized)
    }
    return tokens.map(normalizeLoginTokenLabel).filter(Boolean)
  }

  // New backend format: "[passkey, mfa]"
  if (raw.startsWith('[') && raw.endsWith(']')) {
    return raw
      .slice(1, -1)
      .split(',')
      .map((item) => normalizeLoginTokenLabel(item))
      .filter(Boolean)
  }

  return [normalizeLoginTokenLabel(raw)].filter(Boolean)
}

const getOperationTitle = (op: unknown) => {
  const key = String(op ?? '').toUpperCase()
  if (key === 'LOGIN') return '登录'
  if (key === 'REGISTER') return '注册'
  return '敏感操作'
}

const getOperationTags = (row: SensitiveLogItem): string[] => {
  const op = String(row.operationType).toUpperCase()
  if (op === 'REGISTER') return []
  if (op === 'LOGIN') {
    return getLoginMethodTags(row)
  }
  return [getOperationLabel(row.operationType)]
}

const getRiskTagType = (score: number) => {
  if (score >= 70) return 'danger'
  if (score >= 40) return 'warning'
  return 'success'
}

const getDeviceIconClass = (deviceType?: string | null) => {
  const device = (deviceType || '').toLowerCase()
  if (device.includes('bot') || device.includes('spider') || device.includes('crawler')) {
    return 'fa-solid fa-robot'
  }
  if (device.includes('windows')) {
    return 'fa-brands fa-windows'
  }
  if (device.includes('mac')) {
    return 'fa-brands fa-apple'
  }
  if (device.includes('android')) {
    return 'fa-brands fa-android'
  }
  if (device.includes('ios') || device.includes('iphone') || device.includes('ipad')) {
    return 'fa-brands fa-apple'
  }
  if (device.includes('linux') || device.includes('x11')) {
    return 'fa-brands fa-linux'
  }
  if (device.includes('chromeos') || device.includes('cros')) {
    return 'fa-brands fa-chrome'
  }
  return 'fa-solid fa-desktop'
}

const getBrowserIconClass = (browser?: string | null) => {
  const value = (browser || '').toLowerCase()
  // Custom desktop/mobile client UA
  if (value.includes('ksuserauthdesktop')) return 'fa-solid fa-desktop'
  if (value.includes('ksuserauthmobile')) return 'fa-solid fa-mobile-screen-button'
  if (value.includes('edge') || value.includes('edg')) return 'fa-brands fa-edge'
  if (value.includes('chrome')) return 'fa-brands fa-chrome'
  if (value.includes('safari')) return 'fa-brands fa-safari'
  if (value.includes('firefox')) return 'fa-brands fa-firefox'
  if (value.includes('opera') || value.includes('opr')) return 'fa-brands fa-opera'
  if (value.includes('ie') || value.includes('trident')) return 'fa-brands fa-internet-explorer'
  return 'fa-solid fa-globe'
}

const loadSensitiveLogs = async () => {
  sensitiveLogsLoading.value = true
  try {
    const params: Record<string, string | number> = {
      page: query.value.page,
      pageSize: query.value.pageSize,
    }
    if (query.value.startDate) params.startDate = query.value.startDate
    if (query.value.endDate) params.endDate = query.value.endDate
    if (query.value.operationType) params.operationType = query.value.operationType
    if (query.value.result) params.result = query.value.result
    if (query.value.sortBy) params.sortBy = query.value.sortBy
    if (query.value.sortOrder) params.sortOrder = query.value.sortOrder

    const result = await getSensitiveLogs(params)
    sensitiveLogs.value = result.data
    sensitiveLogsTotal.value = result.total
  } catch (error) {
    console.error('Get sensitive logs failed:', error)
  } finally {
    sensitiveLogsLoading.value = false
  }
}

onMounted(async () => {
  try {
    passkeyLoading.value = true
    const passkeys = await getPasskeyList()
    passkeyEnabled.value = passkeys.length > 0
  } catch (error) {
    console.error('Get passkey list failed:', error)
  } finally {
    passkeyLoading.value = false
  }

  try {
    totpLoading.value = true
    const status = await getTotpStatus()
    totpEnabled.value = status.enabled
  } catch (error) {
    console.error('Get TOTP status failed:', error)
  } finally {
    totpLoading.value = false
  }

  try {
    const info = await getUserInfo()
    mfaEnabled.value = Boolean(info.settings?.mfaEnabled)
    preferredMfaMethod.value = info.settings?.preferredMfaMethod || 'totp'
    preferredSensitiveMethod.value = info.settings?.preferredSensitiveMethod || 'password'
    committedPreferredMfaMethod.value = preferredMfaMethod.value
    committedPreferredSensitiveMethod.value = preferredSensitiveMethod.value
    geoLoginEnabled.value = Boolean(info.settings?.detectUnusualLogin)
    sensitiveEmailEnabled.value = Boolean(info.settings?.notifySensitiveActionEmail)
  } catch (error) {
    console.error('Get user settings failed:', error)
  } finally {
    settingsReady.value = true
  }

  await loadSensitiveLogs()
})

const applyQuery = () => {
  if (dateRange.value && dateRange.value.length === 2) {
    query.value.startDate = dateRange.value[0]
    query.value.endDate = dateRange.value[1]
  } else {
    query.value.startDate = undefined
    query.value.endDate = undefined
  }
  query.value.page = 1
  loadSensitiveLogs()
}

const resetQuery = () => {
  query.value = { page: 1, pageSize: 6, startDate: undefined, endDate: undefined, operationType: undefined, result: undefined, sortBy: undefined, sortOrder: undefined }
  dateRange.value = null
  loadSensitiveLogs()
}

const handlePageChange = (page: number) => {
  query.value.page = page
  loadSensitiveLogs()
}

const handlePageSizeChange = (size: number) => {
  query.value.pageSize = size
  query.value.page = 1
  loadSensitiveLogs()
}

const handleSortChange = (payload: { prop: string; order: string }) => {
  if (!payload || !payload.prop) return
  query.value.sortBy = payload.prop
  query.value.sortOrder = payload.order === 'ascending' ? 'asc' : payload.order === 'descending' ? 'desc' : undefined
  query.value.page = 1
  loadSensitiveLogs()
}

const updateSetting = async (field: 'mfaEnabled' | 'detectUnusualLogin' | 'notifySensitiveActionEmail', value: boolean, rollback: () => void) => {
  if (settingsRollbackGuard.value[field]) {
    settingsRollbackGuard.value[field] = false
    return
  }
  if (!settingsReady.value || settingsUpdating.value) return
  settingsUpdating.value = true
  try {
    await updateUserSetting({ field, value })
  } catch (error) {
    console.error('Update setting failed:', error)
    settingsRollbackGuard.value[field] = true
    rollback()
  } finally {
    settingsUpdating.value = false
  }
}

const updateStringSetting = async (
  field: 'preferred_mfa_method' | 'preferred_sensitive_method',
  stringValue: string,
  rollback: () => void,
) => {
  if (!settingsReady.value || settingsUpdating.value) return false
  settingsUpdating.value = true
  try {
    await updateUserSetting({ field, stringValue })
    ElMessage.success('偏好设置已更新')
    return true
  } catch (error) {
    console.error('Update preference failed:', error)
    rollback()
    return false
  } finally {
    settingsUpdating.value = false
  }
}

const handlePreferredMfaMethodChange = async (value: 'totp' | 'passkey' | 'qr') => {
  if (!mfaEnabled.value) {
    ElMessage.warning('请先开启 MFA')
    return
  }

  const prev = committedPreferredMfaMethod.value
  const success = await updateStringSetting('preferred_mfa_method', value, () => {
    preferredMfaMethod.value = prev
  })
  if (success) {
    committedPreferredMfaMethod.value = preferredMfaMethod.value
  }
}

const handlePreferredSensitiveMethodChange = async (
  value: 'password' | 'email-code' | 'passkey' | 'totp',
) => {
  const prev = committedPreferredSensitiveMethod.value
  const success = await updateStringSetting('preferred_sensitive_method', value, () => {
    preferredSensitiveMethod.value = prev
  })
  if (success) {
    committedPreferredSensitiveMethod.value = preferredSensitiveMethod.value
  }
}

watch(mfaEnabled, async (value, prev) => {
  await updateSetting('mfaEnabled', value, () => { mfaEnabled.value = prev })
})

watch(geoLoginEnabled, async (value, prev) => {
  await updateSetting('detectUnusualLogin', value, () => { geoLoginEnabled.value = prev })
})

watch(sensitiveEmailEnabled, async (value, prev) => {
  await updateSetting('notifySensitiveActionEmail', value, () => { sensitiveEmailEnabled.value = prev })
})

const goToLoginOptions = () => {
  router.push('/home/login-options')
}

const handleDeleteAccount = async () => {
  try {
    await runWithSensitiveVerification(async () => {
      await router.push('/delete-account')
    })
  } catch (error) {
    console.error('Handle delete account failed:', error)
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
    pendingSensitiveAction = action
    sensitiveDialogVisible.value = true
  } catch (error) {
    console.error('Check sensitive verification failed:', error)
    ElMessage.info('需要先完成身份验证')
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

.card-title-between {
  justify-content: space-between;
}

.title-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}


.security-list {
  display: flex;
  flex-direction: column;
}


.security-item {
  padding: 16px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}


.item-left {
  flex: 1;
}

.item-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 16px;
}

.item-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.item-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}


.item-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.password-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preference-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.preference-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-fill-color-lighter);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.toggle-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-fill-color-lighter);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.toggle-item:hover,
.preference-item:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.toggle-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toggle-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.toggle-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.preference-select {
  width: 220px;
  flex-shrink: 0;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.info-value {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.change-pwd-btn {
  width: 100%;
}

.row-gap {
  margin-top: 16px;
}

.modern-table {
  border-radius: 8px;
}

.device-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.device-icon {
  color: var(--el-color-primary);
  font-size: 16px;
}

.log-primary {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-secondary {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.log-title {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.log-device {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.log-device-icon {
  color: var(--el-color-primary);
  font-size: 14px;
}

.log-browser {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.log-browser-icon {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.log-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

:deep(.el-divider--horizontal) {
  margin: 0;
  background-color: var(--el-border-color-light);
}

:deep(.el-table) {
  background: transparent;
}

:deep(.el-table__header) {
  background: var(--el-fill-color-light);
}

:deep(.el-table td) {
  border-color: var(--el-border-color-light);
}

:deep(.el-button) {
  transition: all 0.2s ease;
}

:deep(.toggle-item .el-switch),
:deep(.preference-item .el-switch) {
  --el-switch-on-color: var(--el-color-primary);
  --el-switch-off-color: var(--el-border-color-darker);
}

:deep(.preference-select .el-select__wrapper) {
  min-height: 38px;
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: none;
  border: 1px solid var(--el-border-color-light);
}

:deep(.preference-select .el-select__wrapper.is-focused) {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px rgba(255, 185, 15, 0.14);
}

@media (max-width: 768px) {

  .toggle-item,
  .preference-item {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .preference-select {
    width: 100%;
  }
}
</style>
