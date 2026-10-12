<template>
  <div class="page">
    <div class="page-heading">
      <div>
        <h1>官方应用</h1>
        <p>配置可以读取真实用户 UUID 的 Ksuser 官方应用。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增官方应用</el-button>
    </div>
    <el-alert
      title="应用仅能读取用户已授权的资料范围。服务端应用密钥只在创建和轮换时显示一次。"
      type="info"
      :closable="false"
      show-icon
    />
    <section class="panel">
      <div class="toolbar">
        <el-input
          v-model="search"
          placeholder="搜索名称或客户端 ID"
          clearable
          aria-label="搜索应用"
        /><el-button @click="load">刷新</el-button
        ><span class="muted">{{ applications.length }} 个官方应用</span>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-table
        v-else
        v-loading="loading"
        :data="filtered"
        row-key="clientId"
        empty-text="暂无官方应用"
      >
        <el-table-column label="应用" min-width="240"
          ><template #default="{ row }"
            ><div class="app-cell">
              <el-avatar shape="square" :size="40" :src="row.logoUrl?.trim() || undefined">{{
                row.clientName.slice(0, 1)
              }}</el-avatar>
              <div>
                <strong>{{ row.clientName }}</strong>
                <div class="mono muted">{{ row.clientId }}</div>
              </div>
            </div></template
          ></el-table-column
        >
        <el-table-column label="类型" width="145"
          ><template #default="{ row }">{{
            row.clientType === 'public' ? '公开客户端 / SPA' : '服务端客户端'
          }}</template></el-table-column
        >
        <el-table-column label="状态" width="160"
          ><template #default="{ row }"
            ><div class="status-tags">
              <el-tag :type="row.isActive ? 'success' : 'info'" effect="plain">{{
                row.isActive ? '已启用' : '已停用'
              }}</el-tag>
              <el-tag v-if="row.reserved" size="small" type="warning">保留</el-tag>
            </div></template
          ></el-table-column
        >
        <el-table-column label="允许的资料范围" min-width="240"
          ><template #default="{ row }"
            ><div class="scope-tags">
              <el-tag
                v-for="scope in row.scopes"
                :key="scope"
                size="small"
                type="info"
                effect="plain"
                >{{ scopeLabel(scope) }}</el-tag
              >
            </div></template
          ></el-table-column
        >
        <el-table-column label="操作" width="200" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openEdit(row)">配置</el-button
            ><el-button
              v-if="row.clientType === 'confidential'"
              link
              type="primary"
              :disabled="busy"
              @click="rotate(row)"
              >轮换密钥</el-button
            ><el-button
              v-if="!row.reserved"
              link
              :type="row.isActive ? 'warning' : 'success'"
              :disabled="busy"
              @click="toggle(row)"
              >{{ row.isActive ? '停用' : '启用' }}</el-button
            ></template
          ></el-table-column
        >
      </el-table>
    </section>
    <el-dialog
      v-model="dialogVisible"
      :title="form.editing ? '配置官方应用' : '新增官方应用'"
      width="650px"
      :close-on-click-modal="false"
      @closed="onDialogClosed"
    >
      <el-alert
        v-if="restored"
        title="已恢复未提交的配置，请核对后再次保存。"
        type="success"
        :closable="false"
        class="form-notice"
      />
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="应用名称" required
          ><el-input v-model="form.clientName" maxlength="100" placeholder="例如：Ksuser 校园服务"
        /></el-form-item>
        <el-form-item
          :label="form.editing ? '客户端 ID（创建后固定）' : '客户端 ID（可选，留空自动生成）'"
          ><el-input
            v-model="form.clientId"
            :disabled="form.editing"
            maxlength="100"
            placeholder="例如：ksuser_campus"
        /></el-form-item>
        <el-form-item label="客户端类型（创建后固定）" required
          ><el-radio-group v-model="form.clientType" :disabled="form.editing"
            ><el-radio value="public">公开客户端 / SPA</el-radio
            ><el-radio value="confidential">服务端客户端</el-radio></el-radio-group
          ></el-form-item
        >
        <el-form-item label="应用图片">
          <div class="logo-editor">
            <div class="logo-upload-row">
              <el-avatar shape="square" :size="64" :src="form.logoUrl.trim() || undefined">
                {{ form.clientName.trim().slice(0, 1) || '应用' }}
              </el-avatar>
              <div class="logo-upload-actions">
                <input
                  ref="logoInput"
                  class="visually-hidden"
                  type="file"
                  accept="image/png,image/jpeg,image/webp,image/gif"
                  @change="uploadLogo"
                />
                <div>
                  <el-button type="primary" plain :loading="uploadingLogo" @click="selectLogo">
                    上传图片
                  </el-button>
                  <el-button
                    v-if="form.logoUrl"
                    link
                    type="danger"
                    :disabled="uploadingLogo"
                    @click="form.logoUrl = ''"
                  >
                    清除图片
                  </el-button>
                </div>
                <span class="muted">PNG、JPG、WebP 或 GIF，最大 3 MB</span>
              </div>
            </div>
            <el-input v-model="form.logoUrl" placeholder="也可以填写图片 URL" />
          </div>
        </el-form-item>
        <el-form-item label="回调地址（每行一个，必须精确匹配）" required
          ><el-input
            v-model="form.redirectText"
            type="textarea"
            :rows="3"
            placeholder="https://example.com/auth/callback"
        /></el-form-item>
        <el-form-item label="可申请的资料范围" required
          ><el-checkbox-group v-model="form.scopes"
            ><el-checkbox
              v-for="(label, scope) in scopeLabels"
              :key="scope"
              :value="scope"
              :disabled="scope === 'openid'"
              >{{ label }}</el-checkbox
            ></el-checkbox-group
          ></el-form-item
        >
        <el-form-item label="应用状态"
          ><el-switch
            v-model="form.isActive"
            :disabled="editingReserved"
            active-text="启用"
            inactive-text="停用"
        /></el-form-item>
        <el-alert
          v-if="editingReserved"
          title="Console 为保留应用；当前登录回调必须保留，且不能停用或删除。"
          type="info"
          :closable="false"
        />
        <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon />
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="dialogVisible = false">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          :disabled="uploadingLogo"
          @click="save"
          >保存配置</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      :model-value="!!clientSecret"
      title="保存应用密钥"
      width="600px"
      :close-on-click-modal="false"
      @update:model-value="clientSecret = ''"
    >
      <el-alert
        title="请立即保存密钥。关闭后无法再次读取；遗失时请轮换密钥。"
        type="warning"
        show-icon
        :closable="false"
      />
      <p class="muted">应用：{{ secretApplicationName }}</p>
      <div class="secret-value mono">{{ clientSecret }}</div>
      <el-button type="primary" @click="copySecret">复制密钥</el-button>
      <template #footer><el-button @click="clientSecret = ''">我已保存，关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  consoleApi,
  type ApplicationInput,
  type OfficialApplication,
  type OfficialScope,
} from '@/api/console'
import { VerificationCancelledError, withConsoleVerification } from '@/utils/consoleVerification'
import {
  clearApplicationDraft,
  consumeApplicationDraft,
  type ApplicationDraft,
} from '@/utils/navigation'
const scopeLabels: Record<OfficialScope, string> = {
  openid: '真实 UUID',
  profile: '用户名和头像',
  email: '邮箱',
  real_name: '真实姓名',
  gender: '性别',
  birth_date: '出生日期',
  region: '所在地区',
  bio: '个人简介',
}
const scopeLabel = (scope: string) => scopeLabels[scope as OfficialScope] ?? scope
const applications = ref<OfficialApplication[]>([])
const search = ref('')
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const filtered = computed(() =>
  applications.value.filter((app) =>
    `${app.clientName} ${app.clientId}`
      .toLocaleLowerCase()
      .includes(search.value.trim().toLocaleLowerCase()),
  ),
)
const emptyForm = (): ApplicationDraft => ({
  clientId: '',
  clientName: '',
  logoUrl: '',
  clientType: 'public',
  redirectText: '',
  scopes: ['openid', 'profile', 'email'],
  isActive: true,
  editing: false,
})
const form = ref<ApplicationDraft>(emptyForm())
const formError = ref('')
const dialogVisible = ref(false)
const saving = ref(false)
const restored = ref(false)
const logoInput = ref<HTMLInputElement | null>(null)
const uploadingLogo = ref(false)
const clientSecret = ref('')
const secretApplicationName = ref('')
const editingReserved = computed(
  () =>
    form.value.editing &&
    applications.value.some((app) => app.clientId === form.value.clientId && app.reserved),
)
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    applications.value = await consoleApi.applications()
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '官方应用加载失败'
  } finally {
    loading.value = false
  }
}
const openCreate = () => {
  form.value = emptyForm()
  formError.value = ''
  restored.value = false
  dialogVisible.value = true
}
const openEdit = (app: OfficialApplication) => {
  form.value = {
    clientId: app.clientId,
    clientName: app.clientName,
    logoUrl: app.logoUrl || '',
    clientType: app.clientType,
    redirectText: app.redirectUris.join('\n'),
    scopes: [...app.scopes],
    isActive: app.isActive,
    editing: true,
  }
  formError.value = ''
  restored.value = false
  dialogVisible.value = true
}
const onDialogClosed = () => {
  if (!saving.value) clearApplicationDraft()
}
const selectLogo = () => logoInput.value?.click()
const uploadLogo = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 3 * 1024 * 1024) {
    input.value = ''
    formError.value = '图片不能超过 3 MB'
    return
  }
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    input.value = ''
    formError.value = '仅支持 PNG、JPG、WebP 或 GIF 图片'
    return
  }
  uploadingLogo.value = true
  formError.value = ''
  try {
    const result = await withConsoleVerification(() => consoleApi.uploadOfficialApplicationLogo(file))
    form.value.logoUrl = result.logoUrl
    input.value = ''
    ElMessage.success('应用图片已上传')
  } catch (failure) {
    if (!(failure instanceof VerificationCancelledError)) {
      input.value = ''
      formError.value = failure instanceof Error ? failure.message : '图片上传失败'
    }
  } finally {
    uploadingLogo.value = false
  }
}
const applicationInput = (): ApplicationInput => {
  const redirects = form.value.redirectText
    .split(/\r?\n/)
    .map((uri) => uri.trim())
    .filter(Boolean)
  if (!form.value.clientName.trim()) throw new Error('请输入应用名称')
  if (!redirects.length) throw new Error('请至少填写一个回调地址')
  for (const redirect of redirects) {
    let url: URL
    try {
      url = new URL(redirect)
    } catch {
      throw new Error('回调地址必须是完整 URL')
    }
    if (
      url.hash ||
      !['https:', 'http:'].includes(url.protocol) ||
      (url.protocol === 'http:' && !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname))
    )
      throw new Error('回调地址必须使用 HTTPS，本地开发可使用 localhost HTTP；不可包含片段')
  }
  return {
    clientName: form.value.clientName.trim(),
    logoUrl: form.value.logoUrl.trim() || undefined,
    redirectUris: [...new Set(redirects)],
    scopes: form.value.scopes as OfficialScope[],
    isActive: form.value.isActive,
    ...(!form.value.editing
      ? {
          clientId: form.value.clientId.trim() || undefined,
          clientType: form.value.clientType,
        }
      : {}),
  }
}
const save = async () => {
  if (saving.value || uploadingLogo.value) return
  saving.value = true
  formError.value = ''
  try {
    const input = applicationInput()
    if (form.value.editing)
      await withConsoleVerification(() => consoleApi.updateApplication(form.value.clientId, input))
    else {
      const result = await withConsoleVerification(() => consoleApi.createApplication(input))
      if (result.clientSecret) {
        clientSecret.value = result.clientSecret
        secretApplicationName.value = result.application.clientName
      }
    }
    clearApplicationDraft()
    dialogVisible.value = false
    ElMessage.success('应用配置已保存')
    await load()
  } catch (failure) {
    if (!(failure instanceof VerificationCancelledError))
      formError.value = failure instanceof Error ? failure.message : '配置保存失败'
  } finally {
    saving.value = false
  }
}
const toggle = async (app: OfficialApplication) => {
  if (busy.value) return
  try {
    await ElMessageBox.confirm(
      app.isActive
        ? `停用“${app.clientName}”后，现有令牌将无法获取用户资料。`
        : `启用“${app.clientName}”？`,
      app.isActive ? '停用官方应用' : '启用官方应用',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  busy.value = true
  try {
    await withConsoleVerification(() => consoleApi.updateApplication(app.clientId, {
      clientName: app.clientName,
      logoUrl: app.logoUrl,
      redirectUris: app.redirectUris,
      scopes: app.scopes,
      isActive: !app.isActive,
    }))
    ElMessage.success('应用状态已更新')
    await load()
  } catch (failure) {
    if (!(failure instanceof VerificationCancelledError))
      ElMessage.error(failure instanceof Error ? failure.message : '应用状态更新失败')
  } finally {
    busy.value = false
  }
}
const rotate = async (app: OfficialApplication) => {
  if (busy.value) return
  try {
    await ElMessageBox.confirm(
      `轮换“${app.clientName}”的密钥？旧密钥会立即失效。`,
      '轮换应用密钥',
      { confirmButtonText: '轮换', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  busy.value = true
  try {
    const result = await withConsoleVerification(() => consoleApi.rotateSecret(app.clientId))
    clientSecret.value = result.clientSecret || ''
    secretApplicationName.value = result.application.clientName
    await load()
  } catch (failure) {
    if (!(failure instanceof VerificationCancelledError))
      ElMessage.error(failure instanceof Error ? failure.message : '密钥轮换失败')
  } finally {
    busy.value = false
  }
}
const copySecret = async () => {
  try {
    await navigator.clipboard.writeText(clientSecret.value)
    ElMessage.success('密钥已复制')
  } catch {
    ElMessage.error('复制失败，请手动选中并保存密钥')
  }
}
onMounted(async () => {
  await load()
  const draft = consumeApplicationDraft()
  if (draft) {
    form.value = draft
    restored.value = true
    dialogVisible.value = true
  }
})
</script>
<style scoped>
.app-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}
.app-cell strong {
  font-weight: 550;
}
.app-cell .mono {
  margin-top: 5px;
}
.scope-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.status-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
}
.logo-editor {
  display: grid;
  width: 100%;
  gap: 12px;
}
.logo-upload-row {
  display: flex;
  align-items: center;
  gap: 14px;
}
.logo-upload-actions {
  display: grid;
  gap: 7px;
}
.logo-upload-actions .muted {
  font-size: 12px;
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.form-notice {
  margin-bottom: 20px;
}
.el-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0 12px;
}
.el-checkbox {
  margin-right: 0;
}
.secret-value {
  margin: 18px 0;
  padding: 20px;
  border-radius: 8px;
  border: 1px solid #ffe397;
  background: #fff8df;
  overflow-wrap: anywhere;
  user-select: all;
}
</style>
