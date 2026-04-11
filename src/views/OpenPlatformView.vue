<template>
  <div class="open-platform-page">
    <div class="content-header">
      <div>
        <h1 class="page-title">开放平台</h1>
        <p class="page-subtitle">OAuth2.0 与 SSO 内部服务已分离管理</p>
      </div>
    </div>

    <el-row :gutter="16" class="summary-grid">
      <el-col :xs="24" :md="8">
        <el-card class="card summary-card" shadow="never">
          <div class="summary-label">账号认证</div>
          <div class="summary-value">
            <el-tag :type="verificationTagType" effect="light" size="large">
              {{ verificationLabel }}
            </el-tag>
          </div>
          <div class="summary-desc">个人、企业、管理员认证账号都可以创建 OAuth2.0 应用。</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card class="card summary-card" shadow="never">
          <div class="summary-label">OAuth2.0 应用</div>
          <div class="summary-count">
            <span class="summary-number">{{ oauthOverview?.currentCount ?? 0 }}</span>
            <span class="summary-total">/ {{ oauthOverview?.maxApps ?? 5 }}</span>
          </div>
          <div class="summary-desc">面向第三方站点接入，支持 `profile`、`email` 授权。</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card class="card summary-card" shadow="never">
          <div class="summary-label">SSO 内部服务</div>
          <div class="summary-count">
            <span class="summary-number">{{ ssoOverview?.currentCount ?? 0 }}</span>
            <span class="summary-total">/ {{ ssoOverview?.maxClients ?? 20 }}</span>
          </div>
          <div class="summary-desc">仅管理员可创建，独立走 `openid`、`id_token` 与 PKCE 能力。</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert v-if="oauthOverview && !oauthOverview.verified" type="warning" :closable="false" show-icon
      class="notice-banner" title="当前账号无法创建 OAuth2.0 应用"
      description="请将 users.verification_type 设置为 personal、enterprise 或 admin。" />

    <el-alert v-if="ssoOverview && !ssoOverview.admin" type="info" :closable="false" show-icon class="notice-banner"
      title="SSO 内部服务只对管理员开放" description="只有 users.verification_type=admin 的账号可以创建和管理 SSO 应用。" />

    <el-card class="card app-list-card" shadow="never">
      <template #header>
        <div class="section-header">
          <div>
            <div class="section-title">OAuth2.0 应用</div>
            <div class="section-subtitle">第三方应用通过 `/oauth2/*` 完成授权、换 token 与 userinfo。</div>
          </div>
          <div class="section-actions">
            <el-button text @click="loadOAuthApps" :loading="oauthLoading">刷新</el-button>
            <el-button class="primary-btn" type="primary" :disabled="!oauthOverview?.canCreate"
              @click="openOAuthCreateDialog">
              创建 OAuth 应用
            </el-button>
          </div>
        </div>
      </template>

      <el-skeleton v-if="oauthLoading" :rows="5" animated />
      <el-empty v-else-if="!oauthOverview?.apps.length" description="还没有创建任何 OAuth2.0 应用" />

      <div v-else class="app-list">
        <div v-for="app in oauthOverview.apps" :key="app.appId" class="app-item">
          <div class="app-head">
            <div class="app-title-wrap">
              <el-avatar shape="square" :size="46" :src="app.logoUrl || ''" class="app-logo">
                {{ app.appName.slice(0, 1).toUpperCase() }}
              </el-avatar>
              <div>
                <div class="app-name">{{ app.appName }}</div>
                <div class="app-id">{{ app.appId }}</div>
              </div>
            </div>
            <div class="app-actions">
              <el-button text @click="triggerLogoSelect('oauth', app.appId)">上传 Logo</el-button>
              <el-button text @click="openOAuthEditDialog(app)">编辑</el-button>
              <el-popconfirm title="确认删除应用？" description="删除后该应用将无法继续进行 OAuth 授权。" confirm-button-text="删除"
                cancel-button-text="取消" @confirm="handleOAuthDelete(app.appId)">
                <template #reference>
                  <el-button text type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>

          <div class="app-meta-grid">
            <div class="meta-item">
              <span class="meta-label">回调地址</span>
              <span class="meta-value mono">{{ app.redirectUri }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">联系方式</span>
              <span class="meta-value">{{ app.contactInfo }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">权限范围</span>
              <div class="meta-scopes">
                <el-tag v-if="!app.scopes.length" size="small" effect="plain">仅基础标识</el-tag>
                <el-tag v-for="scope in app.scopes" :key="scope" size="small" effect="plain">
                  {{ oauthScopeLabel(scope) }}
                </el-tag>
              </div>
            </div>
            <div class="meta-item">
              <span class="meta-label">创建时间</span>
              <span class="meta-value">{{ formatDateTime(app.createdAt) }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">最后更新</span>
              <span class="meta-value">{{ formatDateTime(app.updatedAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="card app-list-card" shadow="never">
      <template #header>
        <div class="section-header">
          <div>
            <div class="section-title">SSO 内部服务应用</div>
            <div class="section-subtitle">内部客户端通过 `/sso/*` 与 `/.well-known/openid-configuration` 接入。</div>
          </div>
          <div class="section-actions">
            <el-button text @click="loadSSOClients" :loading="ssoLoading">刷新</el-button>
            <el-button class="primary-btn" type="primary" :disabled="!ssoOverview?.canCreate"
              @click="openSSOCreateDialog">
              创建 SSO 应用
            </el-button>
          </div>
        </div>
      </template>

      <el-skeleton v-if="ssoLoading" :rows="5" animated />
      <el-empty v-else-if="ssoOverview?.admin && !ssoOverview.clients.length" description="还没有创建任何 SSO 内部服务应用" />
      <el-empty v-else-if="ssoOverview && !ssoOverview.admin" description="当前账号不是管理员，无法查看或创建 SSO 内部服务应用" />

      <div v-else class="app-list">
        <div v-for="client in ssoOverview?.clients" :key="client.clientId" class="app-item">
          <div class="app-head">
            <div class="app-title-wrap">
              <el-avatar shape="square" :size="46" :src="client.logoUrl || ''" class="app-logo">
                {{ client.clientName.slice(0, 1).toUpperCase() }}
              </el-avatar>
              <div>
                <div class="app-name">{{ client.clientName }}</div>
                <div class="app-id">{{ client.clientId }}</div>
              </div>
            </div>
            <div class="app-actions">
              <el-button text @click="triggerLogoSelect('sso', client.clientId)">上传 Logo</el-button>
              <el-button text @click="openSSOEditDialog(client)">编辑</el-button>
              <el-popconfirm title="确认删除 SSO 应用？" description="删除后该客户端将无法继续完成内部单点登录。" confirm-button-text="删除"
                cancel-button-text="取消" @confirm="handleSSODelete(client.clientId)">
                <template #reference>
                  <el-button text type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>

          <div class="app-meta-grid">
            <div class="meta-item meta-item-full">
              <span class="meta-label">回调地址</span>
              <div class="meta-stack">
                <code v-for="uri in client.redirectUris" :key="uri" class="stack-code">{{ uri }}</code>
              </div>
            </div>
            <div class="meta-item" v-if="client.postLogoutRedirectUris.length">
              <span class="meta-label">登出回调</span>
              <div class="meta-stack">
                <code v-for="uri in client.postLogoutRedirectUris" :key="uri" class="stack-code">
              {{ uri }}
            </code>
              </div>
            </div>
            <div class="meta-item">
              <span class="meta-label">Audience</span>
              <div class="meta-stack">
                <code v-for="audience in client.audiences" :key="audience" class="stack-code">
              {{ audience }}
            </code>
              </div>
            </div>
            <div class="meta-item">
              <span class="meta-label">权限范围</span>
              <div class="meta-scopes">
                <el-tag v-for="scope in client.scopes" :key="scope" size="small" effect="plain">
                  {{ scope }}
                </el-tag>
                <el-tag v-if="client.requirePkce" size="small" type="success" effect="plain">
                  PKCE Required
                </el-tag>
              </div>
            </div>
            <div class="meta-item">
              <span class="meta-label">创建时间</span>
              <span class="meta-value">{{ formatDateTime(client.createdAt) }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">最后更新</span>
              <span class="meta-value">{{ formatDateTime(client.updatedAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="oauthCreateDialogVisible" title="创建 OAuth2.0 应用" width="620px">
      <el-form ref="oauthCreateFormRef" :model="oauthCreateForm" :rules="oauthCreateRules" label-position="top">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="oauthCreateForm.appName" maxlength="100" placeholder="例如：Ksuser Demo" />
        </el-form-item>

        <el-form-item label="回调地址" prop="redirectUri">
          <el-input v-model="oauthCreateForm.redirectUri"
            placeholder="https://example.com/oauth/callback 或 http://localhost:3000/callback" />
        </el-form-item>

        <el-form-item label="联系方式" prop="contactInfo">
          <el-input v-model="oauthCreateForm.contactInfo" maxlength="120" placeholder="邮箱、工单地址或开发者说明" />
        </el-form-item>

        <el-form-item label="可授权范围" prop="scopes">
          <el-checkbox-group v-model="oauthCreateForm.scopes">
            <el-checkbox label="profile">获取昵称与头像</el-checkbox>
            <el-checkbox label="email">获取邮箱</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="oauthCreateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="oauthSubmitting" @click="handleOAuthCreate">创建应用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="oauthEditDialogVisible" title="编辑 OAuth2.0 应用" width="620px">
      <el-form ref="oauthEditFormRef" :model="oauthEditForm" :rules="oauthEditRules" label-position="top">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="oauthEditForm.appName" maxlength="100" placeholder="例如：Ksuser Demo" />
        </el-form-item>

        <el-form-item label="回调地址" prop="redirectUri">
          <el-input v-model="oauthEditForm.redirectUri"
            placeholder="https://example.com/oauth/callback 或 http://localhost:3000/callback" />
        </el-form-item>

        <el-form-item label="联系方式" prop="contactInfo">
          <el-input v-model="oauthEditForm.contactInfo" maxlength="120" placeholder="邮箱、工单地址或开发者说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="oauthEditDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="oauthEditSubmitting" @click="handleOAuthEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ssoCreateDialogVisible" title="创建 SSO 内部服务应用" width="680px">
      <el-form ref="ssoCreateFormRef" :model="ssoCreateForm" :rules="ssoRules" label-position="top">
        <el-form-item label="客户端名称" prop="clientName">
          <el-input v-model="ssoCreateForm.clientName" maxlength="120" placeholder="例如：Ksuser Admin Console" />
        </el-form-item>

        <el-form-item label="回调地址" prop="redirectUrisText">
          <el-input v-model="ssoCreateForm.redirectUrisText" type="textarea" :rows="4"
            placeholder="每行一个回调地址，仅支持 https:// 或 http://localhost" />
        </el-form-item>

        <el-form-item label="登出回调地址" prop="postLogoutRedirectUrisText">
          <el-input v-model="ssoCreateForm.postLogoutRedirectUrisText" type="textarea" :rows="3"
            placeholder="可选，每行一个" />
        </el-form-item>

        <el-form-item label="Audience" prop="audiencesText">
          <el-input v-model="ssoCreateForm.audiencesText" type="textarea" :rows="2"
            placeholder="每行一个 audience，例如 ksuser-auth" />
        </el-form-item>

        <el-form-item label="可授权范围" prop="scopes">
          <el-checkbox-group v-model="ssoCreateForm.scopes">
            <el-checkbox label="openid">基础身份标识</el-checkbox>
            <el-checkbox label="profile">昵称与头像</el-checkbox>
            <el-checkbox label="email">邮箱</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="PKCE">
          <el-switch v-model="ssoCreateForm.requirePkce" inline-prompt active-text="开启" inactive-text="关闭" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="ssoCreateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ssoSubmitting" @click="handleSSOCreate">创建应用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ssoEditDialogVisible" title="编辑 SSO 内部服务应用" width="680px">
      <el-form ref="ssoEditFormRef" :model="ssoEditForm" :rules="ssoRules" label-position="top">
        <el-form-item label="客户端名称" prop="clientName">
          <el-input v-model="ssoEditForm.clientName" maxlength="120" placeholder="例如：Ksuser Admin Console" />
        </el-form-item>

        <el-form-item label="回调地址" prop="redirectUrisText">
          <el-input v-model="ssoEditForm.redirectUrisText" type="textarea" :rows="4"
            placeholder="每行一个回调地址，仅支持 https:// 或 http://localhost" />
        </el-form-item>

        <el-form-item label="登出回调地址" prop="postLogoutRedirectUrisText">
          <el-input v-model="ssoEditForm.postLogoutRedirectUrisText" type="textarea" :rows="3" placeholder="可选，每行一个" />
        </el-form-item>

        <el-form-item label="Audience" prop="audiencesText">
          <el-input v-model="ssoEditForm.audiencesText" type="textarea" :rows="2"
            placeholder="每行一个 audience，例如 ksuser-auth" />
        </el-form-item>

        <el-form-item label="可授权范围" prop="scopes">
          <el-checkbox-group v-model="ssoEditForm.scopes">
            <el-checkbox label="openid">基础身份标识</el-checkbox>
            <el-checkbox label="profile">昵称与头像</el-checkbox>
            <el-checkbox label="email">邮箱</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="PKCE">
          <el-switch v-model="ssoEditForm.requirePkce" inline-prompt active-text="开启" inactive-text="关闭" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="ssoEditDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ssoEditSubmitting" @click="handleSSOEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <input ref="logoFileInput" class="file-input" type="file" accept="image/*" @change="handleLogoChange" />

    <el-dialog v-model="logoCropDialogVisible" title="裁剪应用 Logo" width="520px" destroy-on-close
      @close="resetLogoCropDialog">
      <div class="avatar-crop-wrapper">
        <div class="crop-stage" @mousedown="startDragImage" @touchstart="startDragImage">
          <img v-if="cropImageUrl" :src="cropImageUrl" class="crop-image" :style="cropImageStyle" draggable="false" />
          <div class="crop-mask crop-mask-top" />
          <div class="crop-mask crop-mask-bottom" />
          <div class="crop-mask crop-mask-left" />
          <div class="crop-mask crop-mask-right" />
          <div class="crop-frame" />
        </div>
        <div class="crop-slider-row">
          <span>缩放</span>
          <el-slider v-model="cropScale" :min="cropMinScale" :max="cropMaxScale" :step="0.01"
            @input="handleCropScaleChange" />
        </div>
      </div>

      <template #footer>
        <el-button @click="logoCropDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="logoUploading" @click="confirmLogoCrop">确认上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretDialogVisible" :title="secretDialogTitle" width="560px">
      <div v-if="oauthCreatedApp" class="secret-panel">
        <el-alert type="success" :closable="false" show-icon title="AppSecret 只会展示这一次，请立即保存。" />
        <div class="secret-item">
          <span class="secret-label">AppID</span>
          <div class="secret-value">
            <code>{{ oauthCreatedApp.appId }}</code>
            <el-button text @click="copyText(oauthCreatedApp.appId)">复制</el-button>
          </div>
        </div>
        <div class="secret-item">
          <span class="secret-label">AppSecret</span>
          <div class="secret-value">
            <code>{{ oauthCreatedApp.appSecret }}</code>
            <el-button text @click="copyText(oauthCreatedApp.appSecret)">复制</el-button>
          </div>
        </div>
      </div>

      <div v-else-if="ssoCreatedClient" class="secret-panel">
        <el-alert type="success" :closable="false" show-icon title="ClientSecret 只会展示这一次，请立即保存。" />
        <div class="secret-item">
          <span class="secret-label">ClientID</span>
          <div class="secret-value">
            <code>{{ ssoCreatedClient.clientId }}</code>
            <el-button text @click="copyText(ssoCreatedClient.clientId)">复制</el-button>
          </div>
        </div>
        <div class="secret-item">
          <span class="secret-label">ClientSecret</span>
          <div class="secret-value">
            <code>{{ ssoCreatedClient.clientSecret }}</code>
            <el-button text @click="copyText(ssoCreatedClient.clientSecret)">复制</el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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
} from '@/api/oauth2'
import {
  createSSOClient,
  deleteSSOClient,
  getSSOClients,
  uploadSSOClientLogo,
  updateSSOClient,
  type CreateSSOClientResponse,
  type SSOClient,
  type SSOClientsOverview,
  type SSOScope,
} from '@/api/sso'

const oauthLoading = ref(false)
const ssoLoading = ref(false)
const oauthSubmitting = ref(false)
const oauthEditSubmitting = ref(false)
const ssoSubmitting = ref(false)
const ssoEditSubmitting = ref(false)

const oauthCreateDialogVisible = ref(false)
const oauthEditDialogVisible = ref(false)
const ssoCreateDialogVisible = ref(false)
const ssoEditDialogVisible = ref(false)
const secretDialogVisible = ref(false)
const logoCropDialogVisible = ref(false)
const logoUploading = ref(false)

const oauthOverview = ref<OAuth2AppsOverview | null>(null)
const ssoOverview = ref<SSOClientsOverview | null>(null)
const oauthCreatedApp = ref<CreateOAuth2AppResponse | null>(null)
const ssoCreatedClient = ref<CreateSSOClientResponse | null>(null)

const oauthCreateFormRef = ref<FormInstance>()
const oauthEditFormRef = ref<FormInstance>()
const ssoCreateFormRef = ref<FormInstance>()
const ssoEditFormRef = ref<FormInstance>()

const editingOAuthAppId = ref('')
const editingSSOClientId = ref('')
const logoFileInput = ref<HTMLInputElement>()
const logoUploadTargetType = ref<'oauth' | 'sso' | ''>('')
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

const ssoCreateForm = reactive({
  clientName: '',
  redirectUrisText: '',
  postLogoutRedirectUrisText: '',
  audiencesText: 'ksuser-auth',
  scopes: ['openid', 'profile', 'email'] as SSOScope[],
  requirePkce: true,
})

const ssoEditForm = reactive({
  clientName: '',
  redirectUrisText: '',
  postLogoutRedirectUrisText: '',
  audiencesText: 'ksuser-auth',
  scopes: ['openid', 'profile', 'email'] as SSOScope[],
  requirePkce: true,
})

const normalizeLines = (value: string) =>
  value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)

const verificationLabel = computed(() => {
  const type = oauthOverview.value?.verificationType || ssoOverview.value?.verificationType || 'none'
  if (type === 'admin') return '管理员'
  if (type === 'enterprise') return '企业认证'
  if (type === 'personal') return '个人认证'
  return '未认证'
})

const verificationTagType = computed(() => {
  const type = oauthOverview.value?.verificationType || ssoOverview.value?.verificationType || 'none'
  if (type === 'admin') return 'danger'
  if (type === 'enterprise') return 'warning'
  if (type === 'personal') return 'success'
  return 'info'
})

const secretDialogTitle = computed(() => {
  if (oauthCreatedApp.value) return 'OAuth 应用已创建'
  if (ssoCreatedClient.value) return 'SSO 应用已创建'
  return '密钥信息'
})

const redirectUriValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const raw = value?.trim() || ''
  if (!raw) {
    callback(new Error('请输入回调地址'))
    return
  }

  try {
    const url = new URL(raw)
    const isHttps = url.protocol === 'https:'
    const isLocalhostHttp = url.protocol === 'http:' && url.hostname === 'localhost'
    if (!isHttps && !isLocalhostHttp) {
      callback(new Error('回调地址只支持 https:// 或 http://localhost'))
      return
    }
    callback()
  } catch {
    callback(new Error('回调地址格式不正确'))
  }
}

const multilineUriValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const lines = normalizeLines(value || '')
  if (!lines.length) {
    callback(new Error('请至少填写一个地址'))
    return
  }

  try {
    lines.forEach((line) => {
      const url = new URL(line)
      const isHttps = url.protocol === 'https:'
      const isLocalhostHttp = url.protocol === 'http:' && url.hostname === 'localhost'
      if (!isHttps && !isLocalhostHttp) {
        throw new Error('地址只支持 https:// 或 http://localhost')
      }
    })
    callback()
  } catch (error) {
    callback(error instanceof Error ? error : new Error('地址格式不正确'))
  }
}

const audiencesValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!normalizeLines(value || '').length) {
    callback(new Error('请至少填写一个 audience'))
    return
  }
  callback()
}

const oauthCreateRules: FormRules = {
  appName: [
    { required: true, message: '请输入应用名称', trigger: 'blur' },
    { min: 2, max: 100, message: '应用名称长度需在 2-100 个字符之间', trigger: 'blur' },
  ],
  redirectUri: [{ validator: redirectUriValidator, trigger: 'blur' }],
  contactInfo: [
    { required: true, message: '请输入联系方式', trigger: 'blur' },
    { min: 3, max: 120, message: '联系方式长度需在 3-120 个字符之间', trigger: 'blur' },
  ],
}

const oauthEditRules: FormRules = {
  appName: oauthCreateRules.appName,
  redirectUri: oauthCreateRules.redirectUri,
  contactInfo: oauthCreateRules.contactInfo,
}

const ssoRules: FormRules = {
  clientName: [
    { required: true, message: '请输入客户端名称', trigger: 'blur' },
    { min: 2, max: 120, message: '客户端名称长度需在 2-120 个字符之间', trigger: 'blur' },
  ],
  redirectUrisText: [{ validator: multilineUriValidator, trigger: 'blur' }],
  postLogoutRedirectUrisText: [{
    validator: (_rule, value, callback) => {
      const lines = normalizeLines(value || '')
      if (!lines.length) {
        callback()
        return
      }
      multilineUriValidator(_rule, value, callback)
    }, trigger: 'blur'
  }],
  audiencesText: [{ validator: audiencesValidator, trigger: 'blur' }],
}

const oauthScopeLabel = (scope: OAuth2Scope) => (scope === 'email' ? '邮箱' : '昵称与头像')

const formatDateTime = (value?: string) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

const resetOAuthCreateForm = () => {
  oauthCreateForm.appName = ''
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

const resetSSOCreateForm = () => {
  ssoCreateForm.clientName = ''
  ssoCreateForm.redirectUrisText = ''
  ssoCreateForm.postLogoutRedirectUrisText = ''
  ssoCreateForm.audiencesText = 'ksuser-auth'
  ssoCreateForm.scopes = ['openid', 'profile', 'email']
  ssoCreateForm.requirePkce = true
  ssoCreateFormRef.value?.clearValidate()
}

const resetSSOEditForm = () => {
  editingSSOClientId.value = ''
  ssoEditForm.clientName = ''
  ssoEditForm.redirectUrisText = ''
  ssoEditForm.postLogoutRedirectUrisText = ''
  ssoEditForm.audiencesText = 'ksuser-auth'
  ssoEditForm.scopes = ['openid', 'profile', 'email']
  ssoEditForm.requirePkce = true
  ssoEditFormRef.value?.clearValidate()
}

const loadOAuthApps = async () => {
  oauthLoading.value = true
  try {
    oauthOverview.value = await getOAuth2Apps()
  } finally {
    oauthLoading.value = false
  }
}

const loadSSOClients = async () => {
  ssoLoading.value = true
  try {
    ssoOverview.value = await getSSOClients()
  } finally {
    ssoLoading.value = false
  }
}

const openOAuthCreateDialog = () => {
  if (!oauthOverview.value?.canCreate) {
    ElMessage.warning('当前账号暂不满足创建 OAuth 应用条件')
    return
  }
  resetOAuthCreateForm()
  oauthCreateDialogVisible.value = true
}

const openOAuthEditDialog = (app: OAuth2App) => {
  editingOAuthAppId.value = app.appId
  oauthEditForm.appName = app.appName
  oauthEditForm.redirectUri = app.redirectUri
  oauthEditForm.contactInfo = app.contactInfo
  oauthEditDialogVisible.value = true
}

const openSSOCreateDialog = () => {
  if (!ssoOverview.value?.canCreate) {
    ElMessage.warning('当前账号暂不满足创建 SSO 应用条件')
    return
  }
  resetSSOCreateForm()
  ssoCreateDialogVisible.value = true
}

const openSSOEditDialog = (client: SSOClient) => {
  editingSSOClientId.value = client.clientId
  ssoEditForm.clientName = client.clientName
  ssoEditForm.redirectUrisText = client.redirectUris.join('\n')
  ssoEditForm.postLogoutRedirectUrisText = client.postLogoutRedirectUris.join('\n')
  ssoEditForm.audiencesText = client.audiences.join('\n')
  ssoEditForm.scopes = [...client.scopes]
  ssoEditForm.requirePkce = client.requirePkce
  ssoEditDialogVisible.value = true
}

const handleOAuthCreate = async () => {
  await oauthCreateFormRef.value?.validate()
  oauthSubmitting.value = true
  try {
    oauthCreatedApp.value = await createOAuth2App({
      appName: oauthCreateForm.appName.trim(),
      redirectUri: oauthCreateForm.redirectUri.trim(),
      contactInfo: oauthCreateForm.contactInfo.trim(),
      scopes: [...oauthCreateForm.scopes],
    })
    ssoCreatedClient.value = null
    oauthCreateDialogVisible.value = false
    secretDialogVisible.value = true
    ElMessage.success('OAuth 应用创建成功')
    await loadOAuthApps()
  } finally {
    oauthSubmitting.value = false
  }
}

const handleOAuthEdit = async () => {
  await oauthEditFormRef.value?.validate()
  if (!editingOAuthAppId.value) {
    ElMessage.error('缺少应用标识')
    return
  }

  oauthEditSubmitting.value = true
  try {
    await updateOAuth2App(editingOAuthAppId.value, {
      appName: oauthEditForm.appName.trim(),
      redirectUri: oauthEditForm.redirectUri.trim(),
      contactInfo: oauthEditForm.contactInfo.trim(),
    })
    oauthEditDialogVisible.value = false
    resetOAuthEditForm()
    ElMessage.success('OAuth 应用信息已更新')
    await loadOAuthApps()
  } finally {
    oauthEditSubmitting.value = false
  }
}

const handleOAuthDelete = async (appId: string) => {
  await deleteOAuth2App(appId)
  ElMessage.success('OAuth 应用已删除')
  await loadOAuthApps()
}

const handleSSOCreate = async () => {
  await ssoCreateFormRef.value?.validate()
  ssoSubmitting.value = true
  try {
    ssoCreatedClient.value = await createSSOClient({
      clientName: ssoCreateForm.clientName.trim(),
      redirectUris: normalizeLines(ssoCreateForm.redirectUrisText),
      postLogoutRedirectUris: normalizeLines(ssoCreateForm.postLogoutRedirectUrisText),
      scopes: [...ssoCreateForm.scopes],
      audiences: normalizeLines(ssoCreateForm.audiencesText),
      requirePkce: ssoCreateForm.requirePkce,
    })
    oauthCreatedApp.value = null
    ssoCreateDialogVisible.value = false
    secretDialogVisible.value = true
    ElMessage.success('SSO 应用创建成功')
    await loadSSOClients()
  } finally {
    ssoSubmitting.value = false
  }
}

const handleSSOEdit = async () => {
  await ssoEditFormRef.value?.validate()
  if (!editingSSOClientId.value) {
    ElMessage.error('缺少客户端标识')
    return
  }

  ssoEditSubmitting.value = true
  try {
    await updateSSOClient(editingSSOClientId.value, {
      clientName: ssoEditForm.clientName.trim(),
      redirectUris: normalizeLines(ssoEditForm.redirectUrisText),
      postLogoutRedirectUris: normalizeLines(ssoEditForm.postLogoutRedirectUrisText),
      scopes: [...ssoEditForm.scopes],
      audiences: normalizeLines(ssoEditForm.audiencesText),
      requirePkce: ssoEditForm.requirePkce,
    })
    ssoEditDialogVisible.value = false
    resetSSOEditForm()
    ElMessage.success('SSO 应用信息已更新')
    await loadSSOClients()
  } finally {
    ssoEditSubmitting.value = false
  }
}

const handleSSODelete = async (clientId: string) => {
  await deleteSSOClient(clientId)
  ElMessage.success('SSO 应用已删除')
  await loadSSOClients()
}

const triggerLogoSelect = (targetType: 'oauth' | 'sso', targetId: string) => {
  logoUploadTargetType.value = targetType
  logoUploadTargetId.value = targetId
  logoFileInput.value?.click()
}

const handleLogoChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    target.value = ''
    return
  }
  if (file.size > MAX_LOGO_SIZE) {
    ElMessage.error('图片大小不能超过 3MB')
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
    ElMessage.error('图片读取失败，请重试')
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
      context.drawImage(image, sourceX, sourceY, sourceSize, sourceSize, 0, 0, canvas.width, canvas.height)
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
    ElMessage.error('缺少目标应用信息')
    return
  }

  try {
    logoUploading.value = true
    const blob = await exportCroppedLogoBlob()
    if (logoUploadTargetType.value === 'oauth') {
      await uploadOAuth2AppLogo(logoUploadTargetId.value, blob)
      await loadOAuthApps()
    } else {
      await uploadSSOClientLogo(logoUploadTargetId.value, blob)
      await loadSSOClients()
    }
    logoCropDialogVisible.value = false
    ElMessage.success('Logo 已更新')
  } catch {
    ElMessage.error('Logo 上传失败')
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
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

onMounted(() => {
  void Promise.all([loadOAuthApps(), loadSSOClients()])
})

onBeforeUnmount(() => {
  resetLogoCropDialog()
})
</script>

<style scoped>
.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.summary-grid,
.notice-banner,
.app-list-card {
  margin-bottom: 16px;
}

.card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
}

.summary-card {
  min-height: 168px;
}

.summary-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}

.summary-value {
  margin-bottom: 12px;
}

.summary-count {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}

.summary-number {
  font-size: 34px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.summary-total {
  color: var(--el-text-color-secondary);
}

.summary-desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.app-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.app-item {
  padding: 18px;
  border-radius: 16px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.app-title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-logo {
  flex-shrink: 0;
  background: linear-gradient(140deg, #1f9d6c 0%, #2fbf7d 100%);
  color: #fff;
  font-weight: 700;
  border-radius: 12px;
  overflow: hidden;
}

.app-logo :deep(img) {
  object-fit: contain;
  background: transparent;
}

.app-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.app-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.app-id {
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.app-actions {
  display: flex;
  align-items: center;
  gap: 4px;
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

  .section-header,
  .app-head,
  .secret-value {
    flex-direction: column;
    align-items: stretch;
  }

  .section-actions {
    justify-content: flex-start;
  }
}
</style>
