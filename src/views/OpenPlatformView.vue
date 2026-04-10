<template>
  <div class="open-platform-page">
    <div class="content-header">
      <div>
        <h1 class="page-title">开放平台</h1>
        <p class="page-subtitle">创建并管理可供第三方接入的 OAuth2.0 应用</p>
      </div>
      <el-button
        class="primary-btn"
        type="primary"
        :disabled="!overview?.canCreate"
        @click="openCreateDialog"
      >
        创建应用
      </el-button>
    </div>

    <el-row :gutter="16" class="summary-grid">
      <el-col :xs="24" :md="8">
        <el-card class="card summary-card" shadow="never">
          <div class="summary-label">认证状态</div>
          <div class="summary-value">
            <el-tag :type="verificationTagType" effect="light" size="large">
              {{ verificationLabel }}
            </el-tag>
          </div>
          <div class="summary-desc">只有个人认证或企业认证账号可以创建 OAuth2.0 应用。</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card class="card summary-card" shadow="never">
          <div class="summary-label">应用数量</div>
          <div class="summary-count">
            <span class="summary-number">{{ overview?.currentCount ?? 0 }}</span>
            <span class="summary-total">/ {{ overview?.maxApps ?? 5 }}</span>
          </div>
          <div class="summary-desc">单个认证账号当前最多可创建 5 个应用。</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card class="card summary-card" shadow="never">
          <div class="summary-label">支持权限</div>
          <div class="scope-pills">
            <span class="scope-pill">`openid`</span>
            <span class="scope-pill">`unionid`</span>
            <span class="scope-pill">`profile`</span>
            <span class="scope-pill">`email`</span>
          </div>
          <div class="summary-desc">`openid` 与 `unionid` 固定可获取，其他字段由 scope 控制。</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert
      v-if="overview && !overview.verified"
      type="warning"
      :closable="false"
      show-icon
      class="notice-banner"
      title="当前账号尚未完成个人/企业认证"
      description="认证状态暂只支持管理员直接写库维护，请在 users.verification_type 中设置为 personal 或 enterprise。"
    />

    <el-card class="card app-list-card" shadow="never">
      <template #header>
        <div class="section-header">
          <div>
            <div class="section-title">我的应用</div>
            <div class="section-subtitle">已登记的 OAuth2.0 应用会显示 AppID、回调地址和授权范围。</div>
          </div>
          <el-button text @click="loadApps" :loading="loading">刷新</el-button>
        </div>
      </template>

      <el-skeleton v-if="loading" :rows="5" animated />

      <el-empty v-else-if="!overview?.apps.length" description="还没有创建任何应用" />

      <div v-else class="app-list">
        <div v-for="app in overview?.apps" :key="app.appId" class="app-item">
          <div class="app-head">
            <div>
              <div class="app-name">{{ app.appName }}</div>
              <div class="app-id">{{ app.appId }}</div>
            </div>
            <div class="app-actions">
              <el-button text @click="openEditDialog(app)">编辑</el-button>
              <el-popconfirm
                title="确认删除应用？"
                description="删除后该应用将无法继续进行 OAuth 授权。"
                confirm-button-text="删除"
                cancel-button-text="取消"
                @confirm="handleDelete(app.appId)"
              >
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
                  {{ scopeLabel(scope) }}
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

    <el-dialog v-model="createDialogVisible" title="创建 OAuth2.0 应用" width="620px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="createForm.appName" maxlength="100" placeholder="例如：Ksuser Demo" />
        </el-form-item>

        <el-form-item label="回调地址" prop="redirectUri">
          <el-input
            v-model="createForm.redirectUri"
            placeholder="https://example.com/oauth/callback 或 http://localhost:3000/callback"
          />
        </el-form-item>

        <el-form-item label="联系方式" prop="contactInfo">
          <el-input
            v-model="createForm.contactInfo"
            maxlength="120"
            placeholder="邮箱、工单地址或开发者说明"
          />
        </el-form-item>

        <el-form-item label="可授权范围" prop="scopes">
          <el-checkbox-group v-model="createForm.scopes">
            <el-checkbox label="profile">获取昵称与头像</el-checkbox>
            <el-checkbox label="email">获取邮箱</el-checkbox>
          </el-checkbox-group>
          <div class="field-hint">`openid` 与 `unionid` 不需要额外勾选，会在换取令牌后固定返回。</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建应用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="编辑 OAuth2.0 应用" width="620px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="editForm.appName" maxlength="100" placeholder="例如：Ksuser Demo" />
        </el-form-item>

        <el-form-item label="回调地址" prop="redirectUri">
          <el-input
            v-model="editForm.redirectUri"
            placeholder="https://example.com/oauth/callback 或 http://localhost:3000/callback"
          />
        </el-form-item>

        <el-form-item label="联系方式" prop="contactInfo">
          <el-input
            v-model="editForm.contactInfo"
            maxlength="120"
            placeholder="邮箱、工单地址或开发者说明"
          />
        </el-form-item>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="当前编辑不会修改 AppID、AppSecret 和权限范围。"
        />
      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretDialogVisible" title="应用已创建" width="560px">
      <div v-if="createdApp" class="secret-panel">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="AppSecret 只会展示这一次，请立即保存。"
        />

        <div class="secret-item">
          <span class="secret-label">AppID</span>
          <div class="secret-value">
            <code>{{ createdApp.appId }}</code>
            <el-button text @click="copyText(createdApp.appId)">复制</el-button>
          </div>
        </div>

        <div class="secret-item">
          <span class="secret-label">AppSecret</span>
          <div class="secret-value">
            <code>{{ createdApp.appSecret }}</code>
            <el-button text @click="copyText(createdApp.appSecret)">复制</el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createOAuth2App,
  deleteOAuth2App,
  getOAuth2Apps,
  updateOAuth2App,
  type CreateOAuth2AppResponse,
  type OAuth2App,
  type OAuth2AppsOverview,
  type OAuth2Scope,
} from '@/api/oauth2'

const loading = ref(false)
const submitting = ref(false)
const editSubmitting = ref(false)
const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const secretDialogVisible = ref(false)
const overview = ref<OAuth2AppsOverview | null>(null)
const createdApp = ref<CreateOAuth2AppResponse | null>(null)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()
const editingAppId = ref('')

const createForm = reactive<{
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

const editForm = reactive<{
  appName: string
  redirectUri: string
  contactInfo: string
}>({
  appName: '',
  redirectUri: '',
  contactInfo: '',
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

const createRules: FormRules = {
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

const editRules: FormRules = {
  appName: createRules.appName,
  redirectUri: createRules.redirectUri,
  contactInfo: createRules.contactInfo,
}

const verificationLabel = computed(() => {
  const type = overview.value?.verificationType || 'none'
  if (type === 'enterprise') return '企业认证'
  if (type === 'personal') return '个人认证'
  return '未认证'
})

const verificationTagType = computed(() => {
  const type = overview.value?.verificationType || 'none'
  if (type === 'enterprise') return 'warning'
  if (type === 'personal') return 'success'
  return 'info'
})

const scopeLabel = (scope: OAuth2Scope) => {
  if (scope === 'email') return '邮箱'
  return '昵称与头像'
}

const formatDateTime = (value?: string) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

const resetCreateForm = () => {
  createForm.appName = ''
  createForm.redirectUri = ''
  createForm.contactInfo = ''
  createForm.scopes = ['profile']
  createFormRef.value?.clearValidate()
}

const resetEditForm = () => {
  editingAppId.value = ''
  editForm.appName = ''
  editForm.redirectUri = ''
  editForm.contactInfo = ''
  editFormRef.value?.clearValidate()
}

const loadApps = async () => {
  loading.value = true
  try {
    overview.value = await getOAuth2Apps()
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  if (!overview.value?.canCreate) {
    ElMessage.warning('当前账号暂不满足创建应用条件')
    return
  }
  resetCreateForm()
  createDialogVisible.value = true
}

const openEditDialog = (app: OAuth2App) => {
  editingAppId.value = app.appId
  editForm.appName = app.appName
  editForm.redirectUri = app.redirectUri
  editForm.contactInfo = app.contactInfo
  editDialogVisible.value = true
}

const handleCreate = async () => {
  await createFormRef.value?.validate()
  submitting.value = true
  try {
    createdApp.value = await createOAuth2App({
      appName: createForm.appName.trim(),
      redirectUri: createForm.redirectUri.trim(),
      contactInfo: createForm.contactInfo.trim(),
      scopes: [...createForm.scopes],
    })
    createDialogVisible.value = false
    secretDialogVisible.value = true
    ElMessage.success('应用创建成功')
    await loadApps()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (appId: string) => {
  await deleteOAuth2App(appId)
  ElMessage.success('应用已删除')
  await loadApps()
}

const handleEdit = async () => {
  await editFormRef.value?.validate()
  if (!editingAppId.value) {
    ElMessage.error('缺少应用标识')
    return
  }

  editSubmitting.value = true
  try {
    await updateOAuth2App(editingAppId.value, {
      appName: editForm.appName.trim(),
      redirectUri: editForm.redirectUri.trim(),
      contactInfo: editForm.contactInfo.trim(),
    })
    editDialogVisible.value = false
    resetEditForm()
    ElMessage.success('应用信息已更新')
    await loadApps()
  } finally {
    editSubmitting.value = false
  }
}

const copyText = async (value: string) => {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

onMounted(() => {
  void loadApps()
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
.notice-banner {
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

.scope-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.scope-pill {
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  font-size: 16px;
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
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: var(--el-fill-color-blank);
}

.app-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.app-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.app-id {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.app-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.meta-value {
  font-size: 14px;
  color: var(--el-text-color-primary);
  line-height: 1.6;
  word-break: break-all;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.meta-scopes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.field-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.secret-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.secret-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
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
  background: var(--el-fill-color-light);
}

.secret-value code {
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

@media (max-width: 900px) {
  .app-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
