<template>
  <div class="page">
    <div class="page-heading">
      <div>
        <h1>官方应用</h1>
        <p>配置可以读取真实用户 UUID 的 Ksuser 官方应用。</p>
      </div>
      <el-button type="primary" :disabled="busy" @click="openCreate">新增官方应用</el-button>
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
      <div v-if="selectedApps.length" class="bulk-toolbar">
        <span class="muted">已选择 {{ selectedApps.length }} 个应用</span>
        <el-button
          size="small"
          :disabled="activatableApps.length === 0 || busy"
          @click="setSelectedAppsActive(true)"
          >批量启用<span v-if="activatableApps.length">（{{ activatableApps.length }}）</span></el-button
        >
        <el-button
          size="small"
          type="warning"
          plain
          :disabled="deactivatableApps.length === 0 || busy"
          @click="setSelectedAppsActive(false)"
          >批量停用<span v-if="deactivatableApps.length">（{{ deactivatableApps.length }}）</span></el-button
        >
        <el-button size="small" text @click="clearSelection">取消选择</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-table
        ref="tableRef"
        v-else
        v-loading="loading"
        :data="filtered"
        row-key="clientId"
        empty-text="暂无官方应用"
        @selection-change="onSelectionChange"
        @sort-change="onSortChange"
      >
        <el-table-column type="selection" width="48" :selectable="selectableApp" />
        <el-table-column label="应用" prop="clientName" min-width="240" sortable="custom"
          ><template #default="{ row }"
            ><div class="app-cell">
              <el-avatar
                shape="square"
                :size="40"
                :src="row.logoUrl?.trim() || DEFAULT_APPLICATION_LOGO_URL"
                >{{
                row.clientName.slice(0, 1)
              }}</el-avatar>
              <div>
                <strong>{{ row.clientName }}</strong>
                <div class="mono muted">{{ row.clientId }}</div>
              </div>
            </div></template
          ></el-table-column
        >
        <el-table-column label="类型" prop="clientType" width="145" sortable="custom"
          ><template #default="{ row }">{{
            row.clientType === 'public' ? '公开客户端 / SPA' : '服务端客户端'
          }}</template></el-table-column
        >
        <el-table-column label="状态" prop="isActive" width="160" sortable="custom"
          ><template #default="{ row }"
            ><div class="status-tags">
              <el-tag :type="row.isActive ? 'success' : 'info'" effect="plain">{{
                row.isActive ? '已启用' : '已停用'
              }}</el-tag>
              <el-tag v-if="row.reserved" size="small" type="warning">保留</el-tag>
            </div></template
          ></el-table-column
        >
        <el-table-column label="允许的资料范围" prop="scopes" min-width="240" sortable="custom"
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
            ><el-button link type="primary" :disabled="busy" @click="openEdit(row)">配置</el-button
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
              <el-avatar
                shape="square"
                :size="64"
                :src="form.logoUrl.trim() || DEFAULT_APPLICATION_LOGO_URL"
              >
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
                    v-if="form.logoUrl !== DEFAULT_APPLICATION_LOGO_URL"
                    link
                    type="primary"
                    :disabled="uploadingLogo"
                    @click="form.logoUrl = DEFAULT_APPLICATION_LOGO_URL"
                  >
                    恢复默认
                  </el-button>
                </div>
                <span class="muted">默认使用 Ksuser 标志；可上传 PNG、JPG、WebP 或 GIF（最大 3 MB）</span>
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
import { computed, onMounted, ref, watch } from 'vue'
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
const DEFAULT_APPLICATION_LOGO_URL = 'https://static.ksuser.cn/favicon.png'
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
type ApplicationSortField = 'clientName' | 'clientType' | 'isActive' | 'scopes'
const sortField = ref<ApplicationSortField>('clientName')
const sortOrder = ref<'asc' | 'desc' | ''>('')
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const tableRef = ref<{ clearSelection: () => void } | null>(null)
const selectedApps = ref<OfficialApplication[]>([])
const activatableApps = computed(() => selectedApps.value.filter((app) => !app.isActive && !app.reserved))
const deactivatableApps = computed(() => selectedApps.value.filter((app) => app.isActive && !app.reserved))
const filtered = computed(() => {
  const result = applications.value.filter((app) =>
    `${app.clientName} ${app.clientId}`
      .toLocaleLowerCase()
      .includes(search.value.trim().toLocaleLowerCase()),
  )
  if (!sortOrder.value) return result
  const value = (app: OfficialApplication) => {
    switch (sortField.value) {
      case 'clientName':
        return app.clientName.toLocaleLowerCase()
      case 'clientType':
        return app.clientType.toLocaleLowerCase()
      case 'isActive':
        return app.isActive ? '1' : '0'
      case 'scopes':
        return [...app.scopes].sort().join(',')
    }
  }
  const direction = sortOrder.value === 'asc' ? 1 : -1
  return result.sort((left, right) => {
    const compared = value(left).localeCompare(value(right), 'zh-Hans', { numeric: true })
    return compared === 0 ? left.clientId.localeCompare(right.clientId) : compared * direction
  })
})
const emptyForm = (): ApplicationDraft => ({
  clientId: '',
  clientName: '',
  logoUrl: DEFAULT_APPLICATION_LOGO_URL,
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
const clearSelection = () => {
  selectedApps.value = []
  tableRef.value?.clearSelection()
}
const onSelectionChange = (selection: OfficialApplication[]) => {
  selectedApps.value = selection
}
const selectableApp = (app: OfficialApplication) => !app.reserved
watch(search, clearSelection)
const onSortChange = (event: { prop: string; order: 'ascending' | 'descending' | null }) => {
  const prop = event.prop as ApplicationSortField
  if (['clientName', 'clientType', 'isActive', 'scopes'].includes(prop) && event.order) {
    sortField.value = prop
    sortOrder.value = event.order === 'ascending' ? 'asc' : 'desc'
  } else {
    sortOrder.value = ''
  }
  clearSelection()
}
const load = async () => {
  clearSelection()
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
    logoUrl: app.logoUrl || DEFAULT_APPLICATION_LOGO_URL,
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
    logoUrl: form.value.logoUrl.trim() || DEFAULT_APPLICATION_LOGO_URL,
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
const setSelectedAppsActive = async (isActive: boolean) => {
  const targets = isActive ? [...activatableApps.value] : [...deactivatableApps.value]
  if (!targets.length || busy.value) return
  const names = targets.map((app) => app.clientName).join('、')
  try {
    await ElMessageBox.confirm(
      isActive
        ? `将启用以下 ${targets.length} 个官方应用：${names}`
        : `将停用以下 ${targets.length} 个官方应用。停用后，现有令牌将无法获取用户资料：${names}`,
      isActive ? '批量启用官方应用' : '批量停用官方应用',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: isActive ? 'info' : 'warning' },
    )
  } catch {
    return
  }
  busy.value = true
  let succeeded = 0
  const failures: string[] = []
  let canceled = false
  try {
    for (const app of targets) {
      try {
        await withConsoleVerification(() =>
          consoleApi.updateApplication(app.clientId, {
            clientName: app.clientName,
            logoUrl: app.logoUrl,
            redirectUris: app.redirectUris,
            scopes: app.scopes,
            isActive,
          }),
        )
        succeeded += 1
      } catch (failure) {
        if (failure instanceof VerificationCancelledError) {
          canceled = true
          break
        }
        failures.push(app.clientName)
      }
    }
    if (failures.length) {
      ElMessage.warning(`已更新 ${succeeded} 个应用，${failures.length} 个失败：${failures.join('、')}`)
    } else if (canceled) {
      ElMessage.warning(`已更新 ${succeeded} 个应用，其余操作已取消。`)
    } else {
      ElMessage.success(`已${isActive ? '启用' : '停用'} ${succeeded} 个官方应用`)
    }
    await load()
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
  border: 1px solid var(--console-accent-border);
  background: var(--console-accent-soft);
  overflow-wrap: anywhere;
  user-select: all;
}
</style>
