<template>
  <div>
    <div class="content-header">
      <div>
        <h1 class="page-title">{{ t('ui.mainLayout.message011') }}</h1>
        <p class="page-subtitle">{{ t('ui.security.message001') }}</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="24">
        <el-card class="card" shadow="never">
          <div class="card-title">
            <el-icon>
              <Lock />
            </el-icon>
            <span>{{ t('ui.security.message002') }}</span>
          </div>
          <div class="security-list">
            <div class="security-item">
              <div class="item-left">
                <div class="item-header">
                  <span class="item-title">{{
                    t('ui.sensitiveVerificationDialog.message025')
                  }}</span>
                </div>
                <p class="item-desc">{{ t('ui.security.message003') }}</p>
              </div>
              <div class="item-right">
                <el-tag type="success">{{ t('ui.loginOptions.message030') }}</el-tag>
                <el-button plain size="small">{{ t('ui.security.message004') }}</el-button>
              </div>
            </div>
            <el-divider />
            <div class="security-item">
              <div class="item-left">
                <div class="item-header">
                  <span class="item-title">{{ t('ui.security.message005') }}</span>
                </div>
                <p class="item-desc">{{ t('ui.security.message006') }}</p>
              </div>
              <div class="item-right">
                <el-tag v-if="passkeyLoading" type="info">{{ t('ui.security.message007') }}</el-tag>
                <el-tag v-else-if="passkeyEnabled" type="success">{{
                  t('ui.loginOptions.message030')
                }}</el-tag>
                <el-tag v-else type="info">{{ t('ui.loginOptions.message031') }}</el-tag>
                <el-button type="primary" plain size="small" @click="goToLoginOptions">
                  {{ passkeyEnabled ? t('ui.security.message004') : t('ui.security.message008') }}
                </el-button>
              </div>
            </div>
            <el-divider />
            <div class="security-item">
              <div class="item-left">
                <div class="item-header">
                  <span class="item-title">{{ t('ui.security.message009') }}</span>
                </div>
                <p class="item-desc">{{ t('ui.security.message010') }}</p>
              </div>
              <div class="item-right">
                <el-tag v-if="totpLoading" type="info">{{ t('ui.security.message007') }}</el-tag>
                <el-tag v-else-if="totpEnabled" type="success">{{
                  t('ui.loginOptions.message030')
                }}</el-tag>
                <el-tag v-else type="info">{{ t('ui.loginOptions.message031') }}</el-tag>
                <el-button type="primary" plain size="small" @click="goToLoginOptions">
                  {{ totpEnabled ? t('ui.security.message004') : t('ui.security.message008') }}
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
            <span>{{ t('ui.security.message011') }}</span>
          </div>
          <div class="toggle-list">
            <div class="toggle-item">
              <div class="toggle-left">
                <span class="toggle-title">{{ t('ui.security.message012') }}</span>
                <span class="toggle-desc">{{ t('ui.security.message013') }}</span>
              </div>
              <el-switch v-model="mfaEnabled" />
            </div>
            <div class="toggle-item">
              <div class="toggle-left">
                <span class="toggle-title">{{ t('ui.security.message014') }}</span>
                <span class="toggle-desc">{{ t('ui.security.message015') }}</span>
              </div>
              <el-switch v-model="geoLoginEnabled" />
            </div>
            <div class="toggle-item">
              <div class="toggle-left">
                <span class="toggle-title">{{ t('ui.security.message016') }}</span>
                <span class="toggle-desc">{{ t('ui.security.message017') }}</span>
              </div>
              <el-switch v-model="sensitiveEmailEnabled" />
            </div>
          </div>

          <div class="preference-list">
            <div class="preference-item">
              <div class="toggle-left">
                <span class="toggle-title">{{ t('ui.security.message018') }}</span>
                <span class="toggle-desc">{{ t('ui.security.message019') }}</span>
              </div>
              <el-select
                v-model="preferredMfaMethod"
                :placeholder="t('ui.security.message020')"
                :disabled="!mfaEnabled || settingsUpdating"
                class="preference-select"
                @change="handlePreferredMfaMethodChange"
              >
                <el-option label="Passkey" value="passkey" :disabled="!passkeyEnabled" />
                <el-option label="TOTP" value="totp" :disabled="!totpEnabled" />
                <el-option v-if="appleBound" label="Apple" value="apple" />
                <el-option :label="t('ui.security.message021')" value="qr" />
              </el-select>
            </div>

            <div class="preference-item">
              <div class="toggle-left">
                <span class="toggle-title">{{ t('ui.security.message022') }}</span>
                <span class="toggle-desc">{{ t('ui.security.message023') }}</span>
              </div>
              <el-select
                v-model="preferredSensitiveMethod"
                :placeholder="t('ui.security.message024')"
                :disabled="settingsUpdating"
                class="preference-select"
                @change="handlePreferredSensitiveMethodChange"
              >
                <el-option :label="t('ui.loginOptions.message004')" value="password" />
                <el-option
                  :label="t('ui.sensitiveVerificationDialog.message025')"
                  value="email-code"
                />
                <el-option label="Passkey" value="passkey" :disabled="!passkeyEnabled" />
                <el-option label="TOTP" value="totp" :disabled="!totpEnabled" />
              </el-select>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24">
        <el-card class="card" shadow="never">
          <div class="card-title card-title-between">
            <div class="title-left">
              <el-icon>
                <Lock />
              </el-icon>
              <span>{{ t('ui.security.message025') }}</span>
            </div>
            <div class="title-actions">
              <el-tag
                v-if="adaptiveAuthStatus"
                :type="getAdaptiveRiskTagType(adaptiveAuthStatus.riskLevel)"
                effect="plain"
              >
                {{ getAdaptiveRiskLabel(adaptiveAuthStatus.riskLevel)
                }}{{ t('ui.security.message026') }} {{ adaptiveAuthStatus.riskScore }}
              </el-tag>
              <el-button
                text
                size="small"
                :loading="adaptiveAuthLoading"
                @click="loadAdaptiveAuthStatus"
              >
                {{ t('ui.devices.message002') }}
              </el-button>
            </div>
          </div>

          <div v-if="adaptiveAuthStatus" class="adaptive-auth-card">
            <div class="adaptive-auth-summary">
              <div class="adaptive-auth-main">
                <span class="adaptive-auth-title">
                  {{
                    adaptiveAuthStatus.requiresStepUp
                      ? t('ui.security.message027')
                      : t('ui.security.message028')
                  }}
                </span>
                <p class="adaptive-auth-desc">
                  {{ resolveApiResult({ errorCode: adaptiveAuthStatus.recommendedActionCode }) }}
                </p>
              </div>
              <div class="adaptive-auth-actions">
                <el-tag :type="adaptiveAuthStatus.trusted ? 'success' : 'warning'">
                  {{
                    adaptiveAuthStatus.trusted
                      ? t('ui.security.message029')
                      : t('ui.security.message030')
                  }}
                </el-tag>
                <el-tag
                  :type="getPolicyDecisionTagType(adaptiveAuthStatus.policyDecision)"
                  effect="plain"
                >
                  {{ getPolicyDecisionLabel(adaptiveAuthStatus.policyDecision) }}
                </el-tag>
                <el-button
                  type="primary"
                  plain
                  :disabled="adaptiveAuthStatus.sensitiveVerified"
                  @click="openAdaptiveStepUpDialog"
                >
                  {{
                    adaptiveAuthStatus.sensitiveVerified
                      ? t('ui.security.message031')
                      : t('ui.security.message032')
                  }}
                </el-button>
              </div>
            </div>

            <el-alert
              v-if="adaptiveAuthStatus.multiEndpointAlert"
              :title="
                adaptiveAuthStatus.alertTitleCode
                  ? resolveApiResult({ errorCode: adaptiveAuthStatus.alertTitleCode })
                  : t('ui.security.message033')
              "
              :description="
                adaptiveAuthStatus.alertMessageCode
                  ? resolveApiResult({ errorCode: adaptiveAuthStatus.alertMessageCode })
                  : t('ui.security.message034')
              "
              :type="adaptiveAuthStatus.alertLevel === 'high' ? 'error' : 'warning'"
              show-icon
              :closable="false"
              class="adaptive-alert"
            />

            <div class="adaptive-auth-metrics">
              <div class="adaptive-auth-metric">
                <span class="metric-label">{{ t('ui.security.message035') }}</span>
                <span class="metric-value">
                  {{
                    adaptiveAuthStatus.sensitiveVerified
                      ? t('ui.security.message036', {
                          arg0: adaptiveAuthStatus.sensitiveVerificationRemainingSeconds,
                        })
                      : t('ui.security.message037')
                  }}
                </span>
              </div>
              <div class="adaptive-auth-metric">
                <span class="metric-label">{{ t('ui.security.message038') }}</span>
                <span class="metric-value">{{
                  formatDuration(adaptiveAuthStatus.authAgeSeconds)
                }}</span>
              </div>
              <div class="adaptive-auth-metric">
                <span class="metric-label">{{ t('ui.security.message039') }}</span>
                <span class="metric-value">{{
                  formatDuration(adaptiveAuthStatus.idleSeconds)
                }}</span>
              </div>
              <div class="adaptive-auth-metric">
                <span class="metric-label">{{ t('ui.security.message040') }}</span>
                <span class="metric-value"
                  >{{
                    adaptiveAuthStatus.currentLocation || t('ui.accountRecoveryCard.message016')
                  }}
                  / {{ adaptiveAuthStatus.deviceType || t('ui.devices.message038') }}</span
                >
              </div>
            </div>

            <div v-if="adaptiveMetrics" class="adaptive-auth-metrics adaptive-auth-metrics--engine">
              <div class="adaptive-auth-metric">
                <span class="metric-label">{{ securityLogText('ui.policyEvents') }}</span>
                <span class="metric-value">{{ adaptiveMetrics.interceptedCount }}</span>
              </div>
              <div class="adaptive-auth-metric">
                <span class="metric-label">{{ logActionLabel('STEP_UP') }}</span>
                <span class="metric-value">{{ adaptiveMetrics.stepUpCount }}</span>
              </div>
              <div class="adaptive-auth-metric">
                <span class="metric-label">{{ t('ui.security.message043') }}</span>
                <span class="metric-value"
                  >{{ adaptiveMetrics.falsePositiveRatePercent.toFixed(2) }}%</span
                >
              </div>
              <div class="adaptive-auth-metric">
                <span class="metric-label">{{ t('ui.security.message044') }}</span>
                <span class="metric-value">{{ adaptiveMetrics.avgVerificationLatencyMs }}ms</span>
              </div>
            </div>

            <div class="adaptive-auth-reasons">
              <span class="metric-label">{{ t('ui.security.message045') }}</span>
              <div class="adaptive-auth-chip-list">
                <el-tag
                  v-for="(reason, index) in adaptiveAuthStatus.reasonCodes"
                  :key="`${reason.errorCode}-${index}`"
                  size="small"
                  effect="plain"
                >
                  {{ resolveApiResult(reason) }}
                </el-tag>
              </div>
            </div>
          </div>

          <el-skeleton v-else :rows="4" animated />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24">
        <AccountRecoveryCard />
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
              <span>{{ t('ui.security.message046') }}</span>
            </div>
            <div class="title-actions">
              <el-tag v-if="sensitiveLogsTotal" type="info" effect="plain">
                {{ t('ui.security.message047') }} {{ sensitiveLogsTotal }}
                {{ t('ui.security.message048') }}
              </el-tag>
              <el-button
                text
                size="small"
                :loading="sensitiveLogsLoading"
                @click="loadSensitiveLogs"
              >
                {{ t('ui.devices.message002') }}
              </el-button>
            </div>
          </div>

          <!-- 查询表单 -->
          <div
            style="
              margin-bottom: 12px;
              display: flex;
              gap: 12px;
              align-items: center;
              flex-wrap: wrap;
            "
          >
            <el-select
              v-model="query.operationType"
              :placeholder="t('ui.security.message049')"
              clearable
              style="width: 180px"
            >
              <el-option
                v-for="(label, key) in operationTypeLabels"
                :key="key"
                :label="label"
                :value="key"
              />
            </el-select>

            <el-select
              v-model="query.result"
              :placeholder="t('ui.security.message050')"
              clearable
              style="width: 140px"
            >
              <el-option :label="t('ui.security.message051')" value="SUCCESS" />
              <el-option :label="t('ui.desktopPasskeyBridge.message010')" value="FAILURE" />
            </el-select>

            <el-date-picker
              v-model="dateRange"
              type="daterange"
              :range-separator="t('ui.security.message052')"
              :start-placeholder="t('ui.security.message053')"
              :end-placeholder="t('ui.security.message054')"
              value-format="yyyy-MM-dd"
              unlink-panels
              style="width: 320px"
            />

            <el-button type="primary" @click="applyQuery">{{
              t('ui.security.message055')
            }}</el-button>
            <el-button @click="resetQuery">{{ t('ui.security.message056') }}</el-button>
          </div>

          <el-table
            :data="sensitiveLogs"
            class="modern-table"
            size="small"
            :empty-text="
              sensitiveLogsLoading ? t('ui.security.message057') : t('ui.security.message058')
            "
            @sort-change="handleSortChange"
          >
            <el-table-column :label="t('ui.devices.message015')" min-width="180">
              <template #default="{ row }">
                <div class="log-primary">
                  <span class="log-title">{{ logTitle(row) }}</span>
                  <el-tag
                    v-for="(tag, idx) in logMethodLabels(row)"
                    :key="`${row.id}-${idx}-${tag}`"
                    size="small"
                    effect="plain"
                  >
                    {{ tag }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column :label="t('ui.security.message050')" min-width="120">
              <template #default="{ row }">
                <div class="log-secondary">
                  <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
                    {{
                      row.result === 'SUCCESS'
                        ? row.operationType === 'ADAPTIVE_POLICY'
                          ? securityLogText('ui.executed')
                          : t('ui.security.message051')
                        : t('ui.desktopPasskeyBridge.message010')
                    }}
                  </el-tag>
                  <span v-if="row.operationType === 'ADAPTIVE_POLICY'" class="log-sub">{{
                    securityLogText('ui.system')
                  }}</span>
                  <span
                    v-if="row.actionTaken && row.operationType !== 'ADAPTIVE_POLICY'"
                    class="log-sub"
                    >{{ logActionLabel(row.actionTaken) }}</span
                  >
                  <span
                    v-if="row.result === 'SUCCESS' && logReason(row.failureReason)"
                    class="log-sub"
                    >{{ logReason(row.failureReason) }}</span
                  >
                  <span v-if="row.triggeredMultiErrorLock" class="log-sub">{{
                    securityLogText('ui.multiErrorLock')
                  }}</span>
                  <span v-if="row.triggeredRateLimitLock" class="log-sub">{{
                    securityLogText('ui.rateLimitLock')
                  }}</span>
                  <span v-if="row.result === 'FAILURE'" class="log-sub">
                    {{ logReason(row.failureReason) || t('ui.security.message059') }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column :label="t('ui.security.message060')" min-width="160">
              <template #default="{ row }">
                <div class="log-secondary">
                  <span>{{ row.ipLocation || t('ui.accountRecoveryCard.message016') }}</span>
                  <span class="log-sub">{{ row.ipAddress }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column :label="t('ui.devices.message006')" min-width="160">
              <template #default="{ row }">
                <div class="log-secondary">
                  <span class="log-device">
                    <i
                      :class="getDeviceIconClass(row.deviceType)"
                      class="log-device-icon"
                      aria-hidden="true"
                    ></i>
                    {{ row.deviceType || t('ui.devices.message038') }}
                  </span>
                  <span class="log-sub log-browser">
                    <i
                      :class="getBrowserIconClass(row.browser)"
                      class="log-browser-icon"
                      aria-hidden="true"
                    ></i>
                    {{ row.browser || t('ui.devices.message037') }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column :label="t('ui.security.message026')" min-width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="getRiskTagType(row.riskScore)" size="small" effect="plain">
                  {{ logRiskLabel(row.riskScore) }} · {{ row.riskScore }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('ui.security.message061')" min-width="160">
              <template #default="{ row }">
                {{ formatSensitiveTime(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>

          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination
              background
              :page-size="query.pageSize"
              :current-page="query.page"
              :total="sensitiveLogsTotal"
              layout="total, sizes, prev, pager, next, jumper"
              :page-sizes="[6, 12, 24, 48]"
              @current-change="handlePageChange"
              @size-change="handlePageSizeChange"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :xs="24">
        <DangerZoneCard :title="t('ui.devices.message027')" :icon="WarningFilled">
          <div class="danger-zone-item">
            <div class="danger-zone-item-left">
              <div class="danger-zone-item-title danger">
                {{ t('ui.deleteAccount.message001') }}
              </div>
              <p class="danger-zone-item-desc">{{ t('ui.security.message062') }}</p>
            </div>
            <div class="danger-zone-item-right">
              <el-button type="danger" plain size="small" @click="handleDeleteAccount">{{
                t('ui.security.message063')
              }}</el-button>
            </div>
          </div>
        </DangerZoneCard>
      </el-col>
    </el-row>

    <SensitiveVerificationDialog
      v-model="sensitiveDialogVisible"
      :purpose="sensitivePurpose"
      @success="handleSensitiveVerificationSuccess"
      @cancel="handleSensitiveVerificationCancel"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { intlLocale, durationUnit, t } from '@/i18n'

import { resolveApiResult } from '@/utils/apiResult'
import {
  securityLogText,
  logTitle,
  logMethodLabels,
  logOperationLabel,
  logActionLabel,
  logReason,
  logRiskLabel,
} from '@/utils/securityLog'
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { Key, Lock, Document, WarningFilled } from '@element-plus/icons-vue'
import {
  checkSensitiveVerification,
  getAppleStatus,
  getAdaptiveAuthStatus,
  getAdaptiveRiskMetrics,
  getPasskeyList,
  getSensitiveLogs,
  getTotpStatus,
  getUserInfo,
  updateUserSetting,
  type AdaptiveAuthStatus,
  type AdaptiveRiskMetrics,
  type SensitiveLogItem,
} from '@/api/auth'
import SensitiveVerificationDialog from '@/components/SensitiveVerificationDialog.vue'
import DangerZoneCard from '@/components/DangerZoneCard.vue'
import AccountRecoveryCard from '@/components/AccountRecoveryCard.vue'

const router = useRouter()

const passkeyEnabled = ref(false)
const totpEnabled = ref(false)
const passkeyLoading = ref(true)
const totpLoading = ref(true)
const mfaEnabled = ref(false)
const preferredMfaMethod = ref<'totp' | 'passkey' | 'qr'>('totp')
const preferredSensitiveMethod = ref<'password' | 'email-code' | 'passkey' | 'totp' | 'apple'>(
  'password',
)
const committedPreferredMfaMethod = ref<'totp' | 'passkey' | 'qr'>('totp')
const committedPreferredSensitiveMethod = ref<
  'password' | 'email-code' | 'passkey' | 'totp' | 'apple'
>('password')
const appleBound = ref(false)
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
const adaptiveAuthStatus = ref<AdaptiveAuthStatus | null>(null)
const adaptiveAuthLoading = ref(false)
const adaptiveMetrics = ref<AdaptiveRiskMetrics | null>(null)
const sensitiveDialogVisible = ref(false)
const sensitivePurpose = ref<'delete-account' | 'login-options'>('login-options')
let pendingSensitiveAction: null | (() => Promise<void>) = null

// 查询与分页/排序状态
const query = ref<{
  page: number
  pageSize: number
  startDate?: string
  endDate?: string
  operationType?: string | null
  result?: string | null
  sortBy?: string | null
  sortOrder?: 'asc' | 'desc' | null
}>({
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

const operationTypeLabels = computed<Record<string, string>>(() =>
  Object.fromEntries(
    [
      'REGISTER',
      'LOGIN',
      'SENSITIVE_VERIFY',
      'ADAPTIVE_POLICY',
      'CHANGE_PASSWORD',
      'CHANGE_EMAIL',
      'ADD_PASSKEY',
      'DELETE_PASSKEY',
      'ENABLE_TOTP',
      'DISABLE_TOTP',
      'BIND_OAUTH',
      'UNBIND_OAUTH',
      'UPDATE_PROFILE',
      'DELETE_ACCOUNT',
    ].map((operation) => [operation, logOperationLabel(operation)]),
  ),
)

const formatSensitiveTime = (value?: string | null) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(intlLocale.value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const getRiskTagType = (score: number) => {
  if (score >= 70) return 'danger'
  if (score >= 40) return 'warning'
  return 'success'
}

const getAdaptiveRiskTagType = (level: AdaptiveAuthStatus['riskLevel']) => {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  return 'success'
}

const getAdaptiveRiskLabel = (level: AdaptiveAuthStatus['riskLevel']) => {
  if (level === 'high') return t('ui.home.message016')
  if (level === 'medium') return t('ui.home.message017')
  return t('ui.home.message018')
}

const getPolicyDecisionLabel = (decision?: AdaptiveAuthStatus['policyDecision']) => {
  if (decision === 'FREEZE') return t('ui.security.message085')
  if (decision === 'STEP_UP') return t('ui.security.message086')
  return t('ui.security.message087')
}

const getPolicyDecisionTagType = (decision?: AdaptiveAuthStatus['policyDecision']) => {
  if (decision === 'FREEZE') return 'danger'
  if (decision === 'STEP_UP') return 'warning'
  return 'success'
}

const formatDuration = (seconds?: number | null) => {
  const total = Math.max(Number(seconds || 0), 0)
  if (total < 60) return durationUnit(total, 'second')
  if (total < 3600) return durationUnit(Math.floor(total / 60), 'minute')
  if (total < 86400) return durationUnit(Math.floor(total / 3600), 'hour')
  return durationUnit(Math.floor(total / 86400), 'day')
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

const loadAdaptiveAuthStatus = async () => {
  adaptiveAuthLoading.value = true
  try {
    adaptiveAuthStatus.value = await getAdaptiveAuthStatus()
    adaptiveMetrics.value = await getAdaptiveRiskMetrics(168)
  } catch (error) {
    console.error('Get adaptive auth status failed:', error)
  } finally {
    adaptiveAuthLoading.value = false
  }
}

onMounted(async () => {
  appleBound.value = await getAppleStatus()
    .then((result) => result.enabled)
    .catch(() => false)
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
  await loadAdaptiveAuthStatus()
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
  query.value = {
    page: 1,
    pageSize: 6,
    startDate: undefined,
    endDate: undefined,
    operationType: undefined,
    result: undefined,
    sortBy: undefined,
    sortOrder: undefined,
  }
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
  query.value.sortOrder =
    payload.order === 'ascending' ? 'asc' : payload.order === 'descending' ? 'desc' : undefined
  query.value.page = 1
  loadSensitiveLogs()
}

const updateSetting = async (
  field: 'mfaEnabled' | 'detectUnusualLogin' | 'notifySensitiveActionEmail',
  value: boolean,
  rollback: () => void,
) => {
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
    ElMessage.success(t('ui.security.message092'))
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
    ElMessage.warning(t('ui.security.message093'))
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
  value: 'password' | 'email-code' | 'passkey' | 'totp' | 'apple',
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
  await updateSetting('mfaEnabled', value, () => {
    mfaEnabled.value = prev
  })
})

watch(geoLoginEnabled, async (value, prev) => {
  await updateSetting('detectUnusualLogin', value, () => {
    geoLoginEnabled.value = prev
  })
})

watch(sensitiveEmailEnabled, async (value, prev) => {
  await updateSetting('notifySensitiveActionEmail', value, () => {
    sensitiveEmailEnabled.value = prev
  })
})

const goToLoginOptions = () => {
  router.push('/home/login-options')
}

const handleDeleteAccount = async () => {
  sensitivePurpose.value = 'delete-account'
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
    const status = await checkSensitiveVerification(sensitivePurpose.value)
    if (status.verified) {
      await action()
      return
    }

    ElMessage.info(t('ui.accountRecoveryCard.message020'))
    pendingSensitiveAction = action
    sensitiveDialogVisible.value = true
  } catch (error) {
    console.error('Check sensitive verification failed:', error)
    ElMessage.info(t('ui.accountRecoveryCard.message020'))
    pendingSensitiveAction = action
    sensitiveDialogVisible.value = true
  }
}

const handleSensitiveVerificationSuccess = async () => {
  const action = pendingSensitiveAction
  pendingSensitiveAction = null

  await loadAdaptiveAuthStatus()

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

const openAdaptiveStepUpDialog = () => {
  sensitivePurpose.value = 'login-options'
  sensitiveDialogVisible.value = true
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
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

.toggle-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-fill-color-lighter);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease;
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

.adaptive-auth-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.adaptive-auth-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(255, 185, 15, 0.08), rgba(255, 255, 255, 0.8));
}

.adaptive-auth-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.adaptive-auth-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.adaptive-auth-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.adaptive-auth-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.adaptive-alert {
  margin-top: -4px;
}

.adaptive-auth-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.adaptive-auth-metrics--engine {
  border-top: 1px dashed var(--el-border-color);
  padding-top: 12px;
}

.adaptive-auth-metric,
.adaptive-auth-reasons {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.adaptive-auth-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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

  .adaptive-auth-summary,
  .adaptive-auth-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .adaptive-auth-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
