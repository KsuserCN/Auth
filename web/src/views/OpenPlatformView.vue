<template>
  <div class="open-platform-page">
    <header class="page-header">
      <h1 class="page-title">{{ t('ui.mainLayout.message016') }}</h1>
      <p class="page-subtitle">{{ t('ui.openPlatform.message002') }}</p>
    </header>

    <section class="identity-panel" :aria-label="t('ui.openPlatform.accountIdentity')">
      <el-skeleton v-if="identityLoading && !userStore.user" :rows="2" animated />
      <div v-else-if="identityLoadFailed" class="load-error" role="alert">
        <span>{{ t('ui.openPlatform.identityLoadFailed') }}</span>
        <el-button text type="primary" @click="loadIdentity">{{
          t('ui.openPlatform.retry')
        }}</el-button>
      </div>
      <template v-else>
        <div class="identity-main">
          <div class="identity-icon" aria-hidden="true">
            <el-icon :size="24"><OfficeBuilding v-if="isEnterprise" /><User v-else /></el-icon>
          </div>
          <div class="identity-copy">
            <div class="identity-label">{{ t('ui.openPlatform.realSubject') }}</div>
            <div class="identity-title-row">
              <h2 class="identity-name" :class="{ 'identity-name--empty': !subjectName }">
                {{ subjectName || t('ui.openPlatform.subjectMissing') }}
              </h2>
              <el-tag :type="verificationTagType" effect="light" size="small">
                {{ verificationLabel }}
              </el-tag>
            </div>
            <p class="identity-description">
              {{
                t(
                  subjectName
                    ? 'ui.openPlatform.subjectHint'
                    : 'ui.openPlatform.subjectMissingHint',
                )
              }}
            </p>
          </div>
        </div>
        <div class="identity-account">
          <span class="identity-label">{{ t('ui.openPlatform.currentAccount') }}</span>
          <div class="identity-account-name">
            <span>{{ userStore.user?.username || '—' }}</span>
            <el-tag v-if="isAdmin" type="info" effect="plain" size="small">
              {{ t('ui.mainLayout.message017') }}
            </el-tag>
          </div>
        </div>
      </template>
    </section>

    <section class="applications-panel" :aria-label="t('ui.openPlatform.applications')">
      <div class="applications-heading">
        <h2>{{ t('ui.openPlatform.applications') }}</h2>
        <a class="docs-link" :href="activeDocumentUrl" target="_blank" rel="noopener noreferrer">
          <el-icon><Document /></el-icon>
          {{ t('ui.openPlatform.message018') }}
          <span aria-hidden="true">↗</span>
        </a>
      </div>
      <el-tabs v-model="applicationCategory" class="category-tabs">
        <el-tab-pane :label="t('ui.openPlatform.websites')" name="website" />
        <el-tab-pane :label="t('ui.openPlatform.mobileApps')" name="mobile" />
      </el-tabs>
      <div class="applications-toolbar">
        <div class="applications-description">
          <p>{{ t(applicationCategory === 'mobile' ? 'ui.openPlatform.mobileHint' : 'ui.openPlatform.websiteHint') }}</p>
          <span class="shared-quota-note">{{ t('ui.openPlatform.sharedQuota') }}</span>
          <span v-if="oauthQuota" class="quota-text">{{ oauthQuota }}</span>
        </div>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" :loading="oauthLoading" @click="loadOAuthApps">
            {{ t('ui.devices.message002') }}
          </el-button>
          <el-button
            type="primary"
            :icon="Plus"
            :disabled="!oauthOverview?.canCreate || oauthLoading || oauthLoadFailed"
            @click="openOAuthCreateDialog"
          >
            {{ t('ui.openPlatform.message005') }}
          </el-button>
        </div>
      </div>

      <div class="applications-content" :aria-busy="oauthLoading">
        <el-skeleton v-if="oauthLoading" :rows="4" animated />
        <div v-else-if="oauthLoadFailed" class="load-error" role="alert">
          <span>{{ t('ui.openPlatform.applicationsLoadFailed') }}</span>
          <el-button text type="primary" @click="loadOAuthApps">{{
            t('ui.openPlatform.retry')
          }}</el-button>
        </div>
        <template v-else-if="oauthOverview">
          <div v-if="!oauthOverview.verified" class="permission-note" role="status">
            <el-icon><Lock /></el-icon>
            <span>{{ t('ui.openPlatform.message008') }}</span>
          </div>
          <div v-else-if="!oauthOverview.canCreate" class="permission-note" role="status">
            <span>{{ t('ui.openPlatform.limitReached') }}</span>
          </div>
          <el-empty
            v-if="!visibleApplications.length"
            :image-size="72"
            :description="t(applicationCategory === 'mobile' ? 'ui.openPlatform.emptyMobileApps' : 'ui.openPlatform.emptyWebsites')"
          >
            <p class="empty-hint">{{ t(applicationCategory === 'mobile' ? 'ui.openPlatform.mobileHint' : 'ui.openPlatform.oauthEmptyHint') }}</p>
          </el-empty>
          <div v-else class="app-list">
            <div v-for="app in visibleApplications" :key="app.appId" class="app-item">
              <button
                class="app-title-wrap"
                type="button"
                @click="openOAuthDetailDialog(app.appId)"
              >
                <el-avatar
                  shape="square"
                  :size="44"
                  :src="app.logoUrl || ''"
                  :class="['app-logo', { 'app-logo--image': !!app.logoUrl }]"
                >
                  {{ app.appName.slice(0, 1).toUpperCase() }}
                </el-avatar>
                <span class="app-title-body">
                  <span class="app-name">{{ app.appName }}</span>
                  <span class="app-id">AppID: {{ app.appId }}</span>
                </span>
              </button>
              <div class="app-item-actions">
                <el-button text type="primary" @click="openOAuthDetailDialog(app.appId)">{{
                  t('ui.openPlatform.manage')
                }}</el-button>
                <el-popconfirm
                  :title="t('ui.openPlatform.message022')"
                  :description="t('ui.openPlatform.message023')"
                  :confirm-button-text="t('ui.loginOptions.message020')"
                  :cancel-button-text="t('ui.addPasskey.message009')"
                  @confirm="handleOAuthDelete(app.appId)"
                >
                  <template #reference>
                    <el-button text type="danger">{{
                      t('ui.loginOptions.message020')
                    }}</el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </div>
        </template>
      </div>
    </section>

    <el-dialog
      v-model="oauthDetailDialogVisible"
      class="app-dialog detail-dialog"
      :title="t('ui.openPlatform.message032')"
      width="760px"
      @closed="resetOAuthDetailDialog"
    >
      <div v-if="oauthDetailApp" class="detail-shell">
        <div class="detail-head">
          <div class="detail-title">
            <el-avatar
              shape="square"
              :size="56"
              :src="oauthDetailApp.logoUrl || ''"
              :class="['app-logo', 'detail-logo', { 'app-logo--image': !!oauthDetailApp.logoUrl }]"
            >
              {{ oauthDetailApp.appName.slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div>
              <div class="detail-name">{{ oauthDetailApp.appName }}</div>
              <div class="detail-subrow">
                <span class="detail-mono">AppID: {{ oauthDetailApp.appId }}</span>
                <el-button text size="small" @click="copyText(oauthDetailApp.appId)">{{
                  t('ui.accountRecoveryCard.message012')
                }}</el-button>
              </div>
            </div>
          </div>
          <div class="detail-actions">
            <el-button size="small" @click="triggerLogoSelect('oauth', oauthDetailApp.appId)">{{
              t('ui.openPlatform.message033')
            }}</el-button>
            <el-button size="small" type="primary" @click="openOAuthEditDialog(oauthDetailApp)">{{
              t('ui.openPlatform.message034')
            }}</el-button>
          </div>
        </div>

        <el-tabs v-model="detailTab">
          <el-tab-pane :label="t('ui.openPlatform.basicInfo')" name="basic">
        <el-descriptions class="detail-desc" :column="1" border>
          <el-descriptions-item :label="t('ui.openPlatform.applicationType')">{{ t(oauthDetailApp.applicationType === 'mobile' ? 'ui.openPlatform.mobileApps' : 'ui.openPlatform.websites') }}</el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.clientType')">
            {{ t(oauthDetailApp.clientType === 'public' ? 'ui.openPlatform.publicClient' : 'ui.openPlatform.confidentialClient') }}
          </el-descriptions-item>
          <el-descriptions-item v-if="oauthDetailApp.applicationType !== 'mobile'" :label="t('ui.openPlatform.message035')">
            <div class="detail-stack">
              <div
                v-for="uri in oauthRedirectUris(oauthDetailApp.redirectUri)"
                :key="uri"
                class="detail-code-row"
              >
                <code class="detail-code">{{ uri }}</code>
                <el-button text size="small" @click="copyText(uri)">{{
                  t('ui.accountRecoveryCard.message012')
                }}</el-button>
              </div>
            </div>
          </el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message036')">{{
            oauthDetailApp.contactInfo
          }}</el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message037')">
            <div class="meta-scopes">
              <el-tag v-if="!oauthDetailApp.scopes.length" size="small" effect="plain">{{
                t('ui.openPlatform.message038')
              }}</el-tag>
              <el-tag
                v-for="scope in oauthDetailApp.scopes"
                :key="scope"
                size="small"
                effect="plain"
              >
                {{ oauthScopeLabel(scope) }}
              </el-tag>
            </div>
          </el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message039')">{{
            formatDateTime(oauthDetailApp.createdAt)
          }}</el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message040')">{{
            formatDateTime(oauthDetailApp.updatedAt)
          }}</el-descriptions-item>
        </el-descriptions>
          </el-tab-pane>
          <el-tab-pane v-if="oauthDetailApp.applicationType === 'mobile'" :label="t('ui.openPlatform.platformInfo')" name="platforms">
            <MobilePlatformDetails :app="oauthDetailApp" @updated="replaceApplication" />
          </el-tab-pane>
          <el-tab-pane :label="t('ui.openPlatform.apiPermissions')" name="permissions">
            <ApplicationPermissions :app="oauthDetailApp" @updated="replaceApplication" />
          </el-tab-pane>
        </el-tabs>
      </div>
      <el-empty v-else :description="t('ui.openPlatform.message041')" />
    </el-dialog>

    <el-dialog
      v-model="oauthCreateDialogVisible"
      class="app-dialog"
      :title="t(applicationCategory === 'mobile' ? 'ui.openPlatform.createMobile' : 'ui.openPlatform.createWebsite')"
      width="620px"
    >
      <el-form
        ref="oauthCreateFormRef"
        class="app-dialog-form"
        :model="oauthCreateForm"
        :rules="oauthCreateRules"
        label-position="top"
      >
        <el-alert :title="t(applicationCategory === 'mobile' ? 'ui.openPlatform.mobileHint' : 'ui.openPlatform.websiteHint')" type="info" :closable="false" />
        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item :label="t('ui.openPlatform.message045')" prop="appName">
              <el-input
                v-model="oauthCreateForm.appName"
                maxlength="100"
                :placeholder="t('ui.openPlatform.message046')"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item :label="t('ui.openPlatform.message036')" prop="contactInfo">
              <el-input
                v-model="oauthCreateForm.contactInfo"
                maxlength="120"
                :placeholder="t('ui.openPlatform.message047')"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item v-if="applicationCategory === 'website'" :label="t('ui.openPlatform.message035')" prop="redirectUri">
          <el-input
            v-model="oauthCreateForm.redirectUri"
            type="textarea"
            :rows="3"
            :placeholder="t('ui.openPlatform.message048')"
          />
        </el-form-item>

        <MobilePlatformFields v-if="applicationCategory === 'mobile'" v-model="createPlatforms" />
        <el-form-item :label="t('ui.openPlatform.message049')" prop="scopes">
          <el-checkbox-group v-model="oauthCreateForm.scopes">
            <el-checkbox value="profile">{{ t('ui.openPlatform.message050') }}</el-checkbox>
            <el-checkbox value="email">{{ t('ui.loginOptions.message003') }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="oauthCreateDialogVisible = false">{{
          t('ui.addPasskey.message009')
        }}</el-button>
        <el-button type="primary" :loading="oauthSubmitting" @click="handleOAuthCreate">{{
          t('ui.openPlatform.message005')
        }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="oauthEditDialogVisible"
      class="app-dialog"
      :title="t('ui.openPlatform.message051')"
      width="620px"
    >
      <el-form
        ref="oauthEditFormRef"
        class="app-dialog-form"
        :model="oauthEditForm"
        :rules="oauthEditRules"
        label-position="top"
      >
        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item :label="t('ui.openPlatform.message045')" prop="appName">
              <el-input
                v-model="oauthEditForm.appName"
                maxlength="100"
                :placeholder="t('ui.openPlatform.message046')"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item :label="t('ui.openPlatform.message036')" prop="contactInfo">
              <el-input
                v-model="oauthEditForm.contactInfo"
                maxlength="120"
                :placeholder="t('ui.openPlatform.message052')"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item v-if="editingApplicationType === 'website'" :label="t('ui.openPlatform.message035')" prop="redirectUri">
          <el-input
            v-model="oauthEditForm.redirectUri"
            type="textarea"
            :rows="3"
            :placeholder="t('ui.openPlatform.message048')"
          />
        </el-form-item>
        <MobilePlatformFields v-if="editingApplicationType === 'mobile'" v-model="editPlatforms" />
      </el-form>

      <template #footer>
        <el-button @click="oauthEditDialogVisible = false">{{
          t('ui.addPasskey.message009')
        }}</el-button>
        <el-button type="primary" :loading="oauthEditSubmitting" @click="handleOAuthEdit">{{
          t('ui.openPlatform.message053')
        }}</el-button>
      </template>
    </el-dialog>

    <input
      ref="logoFileInput"
      class="file-input"
      type="file"
      accept="image/*"
      @change="handleLogoChange"
    />

    <el-dialog
      v-model="logoCropDialogVisible"
      class="app-dialog"
      :title="t('ui.openPlatform.message065')"
      width="520px"
      destroy-on-close
      @close="resetLogoCropDialog"
    >
      <div class="avatar-crop-wrapper">
        <div class="crop-stage" @mousedown="startDragImage" @touchstart="startDragImage">
          <img
            v-if="cropImageUrl"
            :src="cropImageUrl"
            class="crop-image"
            :style="cropImageStyle"
            draggable="false"
          />
          <div class="crop-mask crop-mask-top" />
          <div class="crop-mask crop-mask-bottom" />
          <div class="crop-mask crop-mask-left" />
          <div class="crop-mask crop-mask-right" />
          <div class="crop-frame" />
        </div>
        <div class="crop-slider-row">
          <span>{{ t('ui.openPlatform.message066') }}</span>
          <el-slider
            v-model="cropScale"
            :min="cropMinScale"
            :max="cropMaxScale"
            :step="0.01"
            @input="handleCropScaleChange"
          />
        </div>
      </div>

      <template #footer>
        <el-button @click="logoCropDialogVisible = false">{{
          t('ui.addPasskey.message009')
        }}</el-button>
        <el-button type="primary" :loading="logoUploading" @click="confirmLogoCrop">{{
          t('ui.openPlatform.message067')
        }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="secretDialogVisible"
      class="app-dialog"
      :title="secretDialogTitle"
      width="560px"
    >
      <div v-if="oauthCreatedApp" class="secret-panel">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          :title="t(oauthCreatedApp.appSecret ? 'ui.openPlatform.message068' : 'ui.openPlatform.mobileCreated')"
        />
        <div class="secret-item">
          <span class="secret-label">AppID</span>
          <div class="secret-value">
            <code>{{ oauthCreatedApp.appId }}</code>
            <el-button text @click="copyText(oauthCreatedApp.appId)">{{
              t('ui.accountRecoveryCard.message012')
            }}</el-button>
          </div>
        </div>
        <div v-if="oauthCreatedApp.appSecret" class="secret-item">
          <span class="secret-label">AppSecret</span>
          <div class="secret-value">
            <code>{{ oauthCreatedApp.appSecret }}</code>
            <el-button text @click="copyText(oauthCreatedApp.appSecret)">{{
              t('ui.accountRecoveryCard.message012')
            }}</el-button>
          </div>
        </div>
      </div>

    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import MobilePlatformFields from '@/components/openPlatform/MobilePlatformFields.vue'
import MobilePlatformDetails from '@/components/openPlatform/MobilePlatformDetails.vue'
import ApplicationPermissions from '@/components/openPlatform/ApplicationPermissions.vue'
import { buildPlatforms, platformDraft } from '@/components/openPlatform/mobilePlatforms'
import { isSystemAdmin } from '@/utils/userRoles'
import { intlLocale, t, LocalizedError } from '@/i18n'
import { documentUrl } from '@/utils/documentLinks'
import { useUserStore } from '@/stores/user'
import { Document, Lock, OfficeBuilding, Plus, Refresh, User } from '@element-plus/icons-vue'

import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createOAuth2App,
  deleteOAuth2App,
  getOAuth2Apps,
  uploadOAuth2AppLogo,
  updateOAuth2App,
  type CreateOAuth2AppResponse,
  type OAuth2App,
  type OAuth2AppsOverview,
  type OAuth2Scope,
  type ApplicationType,
} from '@/api/oauth2'
const userStore = useUserStore()
const applicationCategory = ref<ApplicationType>('website')
const editingApplicationType = ref<ApplicationType>('website')
const detailTab = ref('basic')
const createPlatforms = ref(platformDraft())
const editPlatforms = ref(platformDraft())
const visibleApplications = computed(() => oauthOverview.value?.apps.filter((app) => (app.applicationType || 'website') === applicationCategory.value) ?? [])
const oauthQuota = computed(() => {
  if (!oauthOverview.value || oauthLoadFailed.value) return ''
  return t('ui.openPlatform.quota', {
    count: oauthOverview.value.currentCount,
    max: oauthOverview.value.maxApps,
  })
})
const activeDocumentUrl = computed(() => documentUrl('auth/oauth'))
const secretDialogTitle = computed(() => oauthCreatedApp.value ? t('ui.openPlatform.message071') : t('ui.openPlatform.message073'))
const identityLoading = ref(true)
const identityLoadFailed = ref(false)
const oauthLoading = ref(true)
const oauthLoadFailed = ref(false)
const oauthSubmitting = ref(false)
const oauthEditSubmitting = ref(false)

const oauthCreateDialogVisible = ref(false)
const oauthEditDialogVisible = ref(false)
const oauthDetailDialogVisible = ref(false)
const secretDialogVisible = ref(false)
const logoCropDialogVisible = ref(false)
const logoUploading = ref(false)

const oauthOverview = ref<OAuth2AppsOverview | null>(null)
const oauthDetailAppId = ref('')
const oauthCreatedApp = ref<CreateOAuth2AppResponse | null>(null)

const oauthCreateFormRef = ref<FormInstance>()
const oauthEditFormRef = ref<FormInstance>()

const editingOAuthAppId = ref('')
const logoFileInput = ref<HTMLInputElement>()
const logoUploadTargetType = ref<'oauth' | ''>('')
const logoUploadTargetId = ref('')
const selectedLogoFile = ref<File | null>(null)

const CROP_STAGE_SIZE = 320
const CROP_FRAME_SIZE = 220
const CROP_STAGE_CENTER = CROP_STAGE_SIZE / 2
const CROP_FRAME_LEFT = (CROP_STAGE_SIZE - CROP_FRAME_SIZE) / 2
const CROP_FRAME_TOP = (CROP_STAGE_SIZE - CROP_FRAME_SIZE) / 2
const MAX_LOGO_SIZE = 3 * 1024 * 1024
const cropImageUrl = ref('')
const cropImageNaturalWidth = ref(0)
const cropImageNaturalHeight = ref(0)
const cropScale = ref(1)
const cropMinScale = ref(1)
const cropMaxScale = ref(3)
const cropOffsetX = ref(0)
const cropOffsetY = ref(0)
const dragState = reactive({
  active: false,
  startX: 0,
  startY: 0,
  startOffsetX: 0,
  startOffsetY: 0,
})

const oauthCreateForm = reactive<{
  appName: string
  redirectUri: string
  contactInfo: string
  scopes: OAuth2Scope[]
}>({
  appName: '',
  redirectUri: '',
  contactInfo: '',
  scopes: ['profile'],
})

const oauthEditForm = reactive<{
  appName: string
  redirectUri: string
  contactInfo: string
}>({
  appName: '',
  redirectUri: '',
  contactInfo: '',
})

const normalizeSemicolonValues = (value: string) =>
  value
    .split(';')
    .map((item) => item.trim())
    .filter(Boolean)

const oauthRedirectUris = (value: string) => normalizeSemicolonValues(value || '')

const accountAccess = computed(() => oauthOverview.value ?? userStore.user)
const developerVerificationType = computed(() => {
  const type =
    accountAccess.value?.developerVerificationType ?? accountAccess.value?.verificationType
  return type === 'personal' || type === 'enterprise' ? type : 'none'
})
const isEnterprise = computed(() => developerVerificationType.value === 'enterprise')
const isAdmin = computed(
  () => isSystemAdmin(accountAccess.value),
)
const subjectName = computed(() => {
  if (developerVerificationType.value === 'none' && !isAdmin.value) return ''
  return userStore.user?.verificationEntityName?.trim() || ''
})

const verificationLabel = computed(() => {
  const type = developerVerificationType.value
  if (type === 'enterprise') return t('ui.mainLayout.message018')
  if (type === 'personal') return t('ui.mainLayout.message019')
  return t('ui.openPlatform.message070')
})

const verificationTagType = computed(() => {
  const type = developerVerificationType.value
  if (type === 'enterprise') return 'warning'
  if (type === 'personal') return 'success'
  return 'info'
})

const redirectUriValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const values = normalizeSemicolonValues(value || '')
  if (!values.length) {
    callback(new LocalizedError('ui.openPlatform.message074'))
    return
  }
  if (values.join(';').length > 500) {
    callback(new LocalizedError('ui.openPlatform.message075'))
    return
  }

  try {
    values.forEach((raw) => {
      const url = new URL(raw)
      const isHttps = url.protocol === 'https:'
      const isLocalhostHttp = url.protocol === 'http:' && url.hostname === 'localhost'
      if (!isHttps && !isLocalhostHttp) {
        throw new LocalizedError('ui.openPlatform.message076')
      }
      if (url.hash) {
        throw new LocalizedError('ui.openPlatform.message077')
      }
    })
    callback()
  } catch (error) {
    callback(error instanceof Error ? error : new LocalizedError('ui.openPlatform.message078'))
  }
}

const oauthCreateRules = computed<FormRules>(() => ({
  appName: [
    { required: true, message: t('ui.openPlatform.message084'), trigger: 'blur' },
    { min: 2, max: 100, message: t('ui.openPlatform.message085'), trigger: 'blur' },
  ],
  redirectUri: applicationCategory.value === 'website' ? [{ validator: redirectUriValidator, trigger: 'blur' }] : [],
  contactInfo: [
    { required: true, message: t('ui.openPlatform.message086'), trigger: 'blur' },
    { min: 3, max: 120, message: t('ui.openPlatform.message087'), trigger: 'blur' },
  ],
}))

const oauthEditRules = computed<FormRules>(() => ({
  appName: oauthCreateRules.value.appName,
  redirectUri: editingApplicationType.value === 'website' ? [{ validator: redirectUriValidator, trigger: 'blur' }] : [],
  contactInfo: oauthCreateRules.value.contactInfo,
}))

const oauthScopeLabel = (scope: OAuth2Scope) =>
  scope === 'email' ? t('ui.loginOptions.message003') : t('ui.openPlatform.message050')

const formatDateTime = (value?: string) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(intlLocale.value, { hour12: false })
}

const resetOAuthCreateForm = () => {
  oauthCreateForm.appName = ''
  createPlatforms.value = platformDraft()
  oauthCreateForm.redirectUri = ''
  oauthCreateForm.contactInfo = ''
  oauthCreateForm.scopes = ['profile']
  oauthCreateFormRef.value?.clearValidate()
}

const resetOAuthEditForm = () => {
  editingOAuthAppId.value = ''
  oauthEditForm.appName = ''
  oauthEditForm.redirectUri = ''
  oauthEditForm.contactInfo = ''
  oauthEditFormRef.value?.clearValidate()
}

const loadIdentity = async () => {
  identityLoading.value = true
  identityLoadFailed.value = false
  try {
    await userStore.fetchUserInfo()
  } catch {
    identityLoadFailed.value = true
  } finally {
    identityLoading.value = false
  }
}

const loadOAuthApps = async () => {
  oauthLoading.value = true
  oauthLoadFailed.value = false
  try {
    oauthOverview.value = await getOAuth2Apps()
    ensureOAuthDetailExists()
  } catch {
    oauthLoadFailed.value = true
  } finally {
    oauthLoading.value = false
  }
}

const oauthDetailApp = computed(() => {
  const appId = oauthDetailAppId.value
  if (!appId) return null
  return oauthOverview.value?.apps.find((app) => app.appId === appId) ?? null
})

const ensureOAuthDetailExists = () => {
  if (!oauthDetailDialogVisible.value || !oauthDetailAppId.value) return
  if (!oauthDetailApp.value) {
    oauthDetailDialogVisible.value = false
    oauthDetailAppId.value = ''
  }
}

const openOAuthDetailDialog = (appId: string) => {
  oauthDetailAppId.value = appId
  detailTab.value = 'basic'
  oauthDetailDialogVisible.value = true
}

const resetOAuthDetailDialog = () => {
  oauthDetailAppId.value = ''
}

const openOAuthCreateDialog = () => {
  if (!oauthOverview.value?.canCreate) {
    ElMessage.warning(t('ui.openPlatform.message090'))
    return
  }
  resetOAuthCreateForm()
  oauthCreateDialogVisible.value = true
}

const openOAuthEditDialog = (app: OAuth2App) => {
  editingOAuthAppId.value = app.appId
  editingApplicationType.value = app.applicationType || 'website'
  editPlatforms.value = platformDraft(app.platforms)
  oauthEditForm.appName = app.appName
  oauthEditForm.redirectUri = app.redirectUri
  oauthEditForm.contactInfo = app.contactInfo
  oauthEditDialogVisible.value = true
}

const handleOAuthCreate = async () => {
  await oauthCreateFormRef.value?.validate()
  let platforms
  try { platforms = applicationCategory.value === 'mobile' ? buildPlatforms(createPlatforms.value) : undefined }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : String(error)); return }
  oauthSubmitting.value = true
  try {
    oauthCreatedApp.value = await createOAuth2App({
      appName: oauthCreateForm.appName.trim(),
      applicationType: applicationCategory.value,
      ...(applicationCategory.value === 'website' ? { redirectUri: normalizeSemicolonValues(oauthCreateForm.redirectUri).join(';') } : { platforms }),
      contactInfo: oauthCreateForm.contactInfo.trim(),
      scopes: [...oauthCreateForm.scopes],
    })
    oauthCreateDialogVisible.value = false
    secretDialogVisible.value = true
    ElMessage.success(t('ui.openPlatform.message092'))
    await loadOAuthApps()
  } finally {
    oauthSubmitting.value = false
  }
}

const handleOAuthEdit = async () => {
  await oauthEditFormRef.value?.validate()
  if (!editingOAuthAppId.value) {
    ElMessage.error(t('ui.openPlatform.message093'))
    return
  }

  let platforms
  try { platforms = editingApplicationType.value === 'mobile' ? buildPlatforms(editPlatforms.value) : undefined }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : String(error)); return }
  oauthEditSubmitting.value = true
  try {
    await updateOAuth2App(editingOAuthAppId.value, {
      appName: oauthEditForm.appName.trim(),
      ...(editingApplicationType.value === 'website' ? { redirectUri: normalizeSemicolonValues(oauthEditForm.redirectUri).join(';') } : { platforms }),
      contactInfo: oauthEditForm.contactInfo.trim(),
    })
    oauthEditDialogVisible.value = false
    resetOAuthEditForm()
    ElMessage.success(t('ui.openPlatform.message094'))
    await loadOAuthApps()
  } finally {
    oauthEditSubmitting.value = false
  }
}

const replaceApplication = (app: OAuth2App) => {
  const index = oauthOverview.value?.apps.findIndex((item) => item.appId === app.appId) ?? -1
  if (oauthOverview.value && index >= 0) oauthOverview.value.apps[index] = app
}

const handleOAuthDelete = async (appId: string) => {
  await deleteOAuth2App(appId)
  ElMessage.success(t('ui.openPlatform.message095'))
  await loadOAuthApps()
}

watch(oauthDetailDialogVisible, (visible) => {
  if (visible) ensureOAuthDetailExists()
})

const triggerLogoSelect = (targetType: 'oauth', targetId: string) => {
  logoUploadTargetType.value = targetType
  logoUploadTargetId.value = targetId
  logoFileInput.value?.click()
}

const handleLogoChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.error(t('ui.openPlatform.message100'))
    target.value = ''
    return
  }
  if (file.size > MAX_LOGO_SIZE) {
    ElMessage.error(t('ui.openPlatform.message101'))
    target.value = ''
    return
  }

  selectedLogoFile.value = file
  openLogoCropDialog(file)
}

const openLogoCropDialog = (file: File) => {
  if (cropImageUrl.value) {
    URL.revokeObjectURL(cropImageUrl.value)
  }
  const objectUrl = URL.createObjectURL(file)
  const image = new Image()
  image.onload = () => {
    cropImageNaturalWidth.value = image.naturalWidth
    cropImageNaturalHeight.value = image.naturalHeight
    initializeCropState()
    cropImageUrl.value = objectUrl
    logoCropDialogVisible.value = true
  }
  image.onerror = () => {
    URL.revokeObjectURL(objectUrl)
    ElMessage.error(t('ui.openPlatform.message102'))
  }
  image.src = objectUrl
}

const initializeCropState = () => {
  if (!cropImageNaturalWidth.value || !cropImageNaturalHeight.value) return
  const minScaleByFrame = Math.max(
    CROP_FRAME_SIZE / cropImageNaturalWidth.value,
    CROP_FRAME_SIZE / cropImageNaturalHeight.value,
  )
  cropMinScale.value = minScaleByFrame
  cropMaxScale.value = Math.max(minScaleByFrame * 4, minScaleByFrame + 1)
  cropScale.value = minScaleByFrame
  cropOffsetX.value = CROP_STAGE_CENTER - (cropImageNaturalWidth.value * cropScale.value) / 2
  cropOffsetY.value = CROP_STAGE_CENTER - (cropImageNaturalHeight.value * cropScale.value) / 2
  clampCropOffset()
}

const clampCropOffset = () => {
  if (!cropImageNaturalWidth.value || !cropImageNaturalHeight.value) return
  const displayWidth = cropImageNaturalWidth.value * cropScale.value
  const displayHeight = cropImageNaturalHeight.value * cropScale.value
  const minX = CROP_FRAME_LEFT + CROP_FRAME_SIZE - displayWidth
  const maxX = CROP_FRAME_LEFT
  const minY = CROP_FRAME_TOP + CROP_FRAME_SIZE - displayHeight
  const maxY = CROP_FRAME_TOP
  cropOffsetX.value = Math.min(Math.max(cropOffsetX.value, minX), maxX)
  cropOffsetY.value = Math.min(Math.max(cropOffsetY.value, minY), maxY)
}

const startDragImage = (event: MouseEvent | TouchEvent) => {
  if (!cropImageUrl.value) return
  dragState.active = true
  const point = getEventPoint(event)
  dragState.startX = point.x
  dragState.startY = point.y
  dragState.startOffsetX = cropOffsetX.value
  dragState.startOffsetY = cropOffsetY.value

  window.addEventListener('mousemove', handleDragging)
  window.addEventListener('mouseup', stopDragImage)
  window.addEventListener('touchmove', handleDragging, { passive: false })
  window.addEventListener('touchend', stopDragImage)
}

const handleDragging = (event: MouseEvent | TouchEvent) => {
  if (!dragState.active) return
  if (event instanceof TouchEvent) {
    event.preventDefault()
  }
  const point = getEventPoint(event)
  cropOffsetX.value = dragState.startOffsetX + (point.x - dragState.startX)
  cropOffsetY.value = dragState.startOffsetY + (point.y - dragState.startY)
  clampCropOffset()
}

const stopDragImage = () => {
  dragState.active = false
  window.removeEventListener('mousemove', handleDragging)
  window.removeEventListener('mouseup', stopDragImage)
  window.removeEventListener('touchmove', handleDragging)
  window.removeEventListener('touchend', stopDragImage)
}

const getEventPoint = (event: MouseEvent | TouchEvent) => {
  if (event instanceof TouchEvent) {
    const touch = event.touches[0] || event.changedTouches[0]
    return { x: touch?.clientX || 0, y: touch?.clientY || 0 }
  }
  return { x: event.clientX, y: event.clientY }
}

const handleCropScaleChange = (value: number | number[]) => {
  const nextValue = Array.isArray(value) ? value[0] : value
  if (!nextValue || !cropImageNaturalWidth.value || !cropImageNaturalHeight.value) return
  const prevScale = cropScale.value
  const imagePointX = (CROP_STAGE_CENTER - cropOffsetX.value) / prevScale
  const imagePointY = (CROP_STAGE_CENTER - cropOffsetY.value) / prevScale

  cropScale.value = nextValue
  cropOffsetX.value = CROP_STAGE_CENTER - imagePointX * cropScale.value
  cropOffsetY.value = CROP_STAGE_CENTER - imagePointY * cropScale.value
  clampCropOffset()
}

const exportCroppedLogoBlob = async (): Promise<Blob> => {
  return await new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => {
      const sourceX = (CROP_FRAME_LEFT - cropOffsetX.value) / cropScale.value
      const sourceY = (CROP_FRAME_TOP - cropOffsetY.value) / cropScale.value
      const sourceSize = CROP_FRAME_SIZE / cropScale.value

      const canvas = document.createElement('canvas')
      canvas.width = 512
      canvas.height = 512
      const context = canvas.getContext('2d')
      if (!context) {
        reject(new Error('canvas_context_unavailable'))
        return
      }
      context.drawImage(
        image,
        sourceX,
        sourceY,
        sourceSize,
        sourceSize,
        0,
        0,
        canvas.width,
        canvas.height,
      )
      canvas.toBlob((blob) => {
        if (!blob) {
          reject(new Error('crop_failed'))
          return
        }
        resolve(blob)
      }, 'image/png')
    }
    image.onerror = () => reject(new Error('image_load_failed'))
    image.src = cropImageUrl.value
  })
}

const confirmLogoCrop = async () => {
  if (!cropImageUrl.value || !selectedLogoFile.value) return
  if (!logoUploadTargetType.value || !logoUploadTargetId.value) {
    ElMessage.error(t('ui.openPlatform.message103'))
    return
  }

  try {
    logoUploading.value = true
    const blob = await exportCroppedLogoBlob()
    await uploadOAuth2AppLogo(logoUploadTargetId.value, blob)
    await loadOAuthApps()
    logoCropDialogVisible.value = false
    ElMessage.success(t('ui.openPlatform.message104'))
  } catch {
    ElMessage.error(t('ui.openPlatform.message105'))
  } finally {
    logoUploading.value = false
  }
}

const resetLogoCropDialog = () => {
  stopDragImage()
  logoUploading.value = false
  selectedLogoFile.value = null
  if (cropImageUrl.value) {
    URL.revokeObjectURL(cropImageUrl.value)
  }
  cropImageUrl.value = ''
  cropImageNaturalWidth.value = 0
  cropImageNaturalHeight.value = 0
  logoUploadTargetType.value = ''
  logoUploadTargetId.value = ''
  if (logoFileInput.value) {
    logoFileInput.value.value = ''
  }
}

const cropImageStyle = computed(() => ({
  width: `${cropImageNaturalWidth.value * cropScale.value}px`,
  height: `${cropImageNaturalHeight.value * cropScale.value}px`,
  transform: `translate(${cropOffsetX.value}px, ${cropOffsetY.value}px)`,
}))

const copyText = async (value: string) => {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(t('ui.loginOptions.message080'))
  } catch {
    ElMessage.error(t('ui.accountRecoveryCard.message023'))
  }
}

onMounted(() => {
  void Promise.all([loadIdentity(), loadOAuthApps()])
})

onBeforeUnmount(() => {
  resetLogoCropDialog()
})
</script>

<style scoped>
.client-type-hint {
  width: 100%;
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
:deep(.el-dialog.app-dialog) {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.18);
}

:deep(.app-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:deep(.app-dialog .el-dialog__title) {
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 650;
}

:deep(.app-dialog .el-dialog__headerbtn) {
  top: 17px;
  right: 18px;
  width: 32px;
  height: 32px;
  border-radius: 9px;
}

:deep(.app-dialog .el-dialog__headerbtn:hover) {
  background: var(--el-fill-color-light);
}

:deep(.app-dialog .el-dialog__body) {
  padding: 22px 24px 8px;
}

:deep(.app-dialog .el-dialog__footer) {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 24px 22px;
}

:deep(.app-dialog .el-dialog__footer .el-button) {
  min-width: 88px;
  height: 38px;
  border-radius: 10px;
  font-weight: 600;
}

.open-platform-page {
  max-width: 1120px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  margin: 0;
  font-size: 26px;
  line-height: 1.35;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.identity-panel,
.applications-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
}

.identity-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
  padding: 24px;
}

.identity-main {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-width: 0;
  flex: 1;
}

.identity-icon,
.restricted-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
}

.identity-copy {
  min-width: 0;
}

.identity-label {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.identity-title-row,
.identity-account-name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.identity-name {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
}

.identity-name--empty {
  color: var(--el-text-color-regular);
  font-size: 17px;
}

.identity-title-row :deep(.el-tag),
.identity-account-name :deep(.el-tag) {
  flex-shrink: 0;
}

.identity-description {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.identity-account {
  max-width: 28%;
  padding-left: 24px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.identity-account-name {
  font-size: 14px;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}

.applications-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 24px;
}

.applications-heading h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.docs-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  text-decoration: none;
}

.docs-link:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}

.docs-link:focus-visible,
.app-title-wrap:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 4px;
  border-radius: 4px;
}

.category-tabs {
  margin: 12px 24px 0;
}

.app-dialog-form :deep(.el-alert) {
  margin-bottom: 20px;
}

.applications-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
}

.applications-description p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.quota-text,
.shared-quota-note {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.toolbar-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.toolbar-actions :deep(.el-button) {
  height: 36px;
  margin: 0;
  border-radius: 8px;
}

.toolbar-actions :deep(.el-button--primary:not(.is-disabled)) {
  color: #252525;
}

.applications-content {
  padding: 0 24px 12px;
}

.applications-content > .el-skeleton {
  padding: 12px 0 24px;
}

.permission-note,
.load-error {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
}

.permission-note {
  margin-bottom: 8px;
}

.permission-note .el-icon {
  flex-shrink: 0;
}

.load-error {
  justify-content: space-between;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.load-error :deep(.el-button) {
  flex-shrink: 0;
  margin: 0;
}

.empty-hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.applications-content :deep(.el-empty) {
  padding: 32px 0 40px;
}

.applications-content :deep(.el-empty__bottom) {
  margin-top: 8px;
}

.restricted-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 12px 48px;
  text-align: center;
}

.restricted-state h3 {
  margin: 16px 0 8px;
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.restricted-state p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.app-list {
  display: flex;
  flex-direction: column;
}

.app-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.app-title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

button.app-title-wrap {
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

button.app-title-wrap:hover .app-name {
  text-decoration: underline;
  text-underline-offset: 4px;
}

.app-title-body {
  min-width: 0;
}

.app-logo {
  flex-shrink: 0;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-weight: 700;
  border-radius: 12px;
  overflow: hidden;
}

.app-logo--image {
  background: transparent;
  color: var(--el-text-color-primary);
  border: 1px solid var(--el-border-color-lighter);
}

.app-logo :deep(img) {
  object-fit: contain;
  background: transparent;
}

.app-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-id {
  display: block;
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.app-item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.detail-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-fill-color-extra-light);
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.detail-logo {
  border-radius: 14px;
}

.detail-name {
  font-size: 18px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.detail-subrow {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.detail-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.detail-desc :deep(.el-descriptions__label) {
  width: 120px;
}

.detail-desc :deep(.el-descriptions__table) {
  border-radius: 12px;
  overflow: hidden;
}

.detail-desc :deep(.el-descriptions__label.el-descriptions__cell) {
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.detail-desc :deep(.el-descriptions__content.el-descriptions__cell) {
  color: var(--el-text-color-primary);
  line-height: 1.65;
}

.detail-code-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.detail-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.detail-stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.app-dialog :deep(.el-dialog__body) {
  padding: 22px 24px 8px;
}

.app-dialog :deep(.el-dialog__footer) {
  padding: 12px 24px 22px;
}

.app-dialog-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.app-dialog-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.app-dialog-form :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
  transition: box-shadow 0.2s ease;
}

.app-dialog-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5) inset;
}

.app-dialog-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--el-color-primary) inset,
    0 0 0 3px var(--el-color-primary-light-9);
}

.app-dialog-form :deep(.el-textarea__inner) {
  border-radius: 10px;
  padding: 11px 12px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

.app-dialog-form :deep(.el-textarea__inner:focus) {
  box-shadow:
    0 0 0 1px var(--el-color-primary) inset,
    0 0 0 3px var(--el-color-primary-light-9);
}

.app-dialog-form :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}

.app-dialog-form :deep(.el-switch) {
  margin-top: 4px;
}

.app-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item-full {
  grid-column: 1 / -1;
}

.meta-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.meta-value,
.stack-code {
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.mono,
.stack-code,
.secret-value code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.meta-scopes,
.meta-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stack-code {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
}

.secret-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.secret-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.secret-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.secret-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--el-fill-color-extra-light);
}

.file-input {
  display: none;
}

.avatar-crop-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.crop-stage {
  position: relative;
  width: 320px;
  height: 320px;
  margin: 0 auto;
  overflow: hidden;
  border-radius: 16px;
  background: #101418;
  touch-action: none;
  cursor: grab;
  user-select: none;
}

.crop-image {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: top left;
}

.crop-mask {
  position: absolute;
  background: rgba(0, 0, 0, 0.45);
  pointer-events: none;
}

.crop-mask-top {
  top: 0;
  left: 0;
  width: 100%;
  height: 50px;
}

.crop-mask-bottom {
  bottom: 0;
  left: 0;
  width: 100%;
  height: 50px;
}

.crop-mask-left {
  top: 50px;
  left: 0;
  width: 50px;
  height: 220px;
}

.crop-mask-right {
  top: 50px;
  right: 0;
  width: 50px;
  height: 220px;
}

.crop-frame {
  position: absolute;
  top: 50px;
  left: 50px;
  width: 220px;
  height: 220px;
  border: 2px solid rgba(255, 255, 255, 0.88);
  border-radius: 20px;
  pointer-events: none;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.3) inset;
}

.crop-slider-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 4px;
  color: var(--el-text-color-secondary);
}

.crop-slider-row :deep(.el-slider) {
  flex: 1;
}

@media (max-width: 900px) {
  .app-meta-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .identity-panel {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
    padding: 20px;
  }

  .identity-account {
    max-width: none;
    padding: 14px 0 0;
    border-left: none;
    border-top: 1px solid var(--el-border-color-lighter);
  }

  .applications-heading {
    flex-wrap: wrap;
    padding: 16px 20px;
  }

  .applications-toolbar {
    flex-direction: column;
    align-items: stretch;
    padding: 16px 20px;
  }

  .toolbar-actions {
    justify-content: flex-end;
  }

  .applications-content {
    padding: 0 20px 8px;
  }

  .app-item,
  .detail-head,
  .secret-value {
    flex-direction: column;
    align-items: stretch;
  }

  .app-item-actions,
  .detail-actions {
    justify-content: flex-end;
  }

  :deep(.app-dialog .el-dialog__header) {
    padding: 18px 18px 14px;
  }

  :deep(.app-dialog .el-dialog__body) {
    padding: 18px 18px 6px;
  }

  :deep(.app-dialog .el-dialog__footer) {
    padding: 10px 18px 18px;
  }

  .detail-head {
    align-items: stretch;
  }

  .detail-actions {
    display: flex;
  }

  .page-title {
    font-size: 25px;
  }
}
</style>
