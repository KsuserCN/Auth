<template>
  <div class="page">
    <div class="page-heading">
      <div>
        <h1>用户管理</h1>
        <p>查询用户资料，管理角色或手动删除账号。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <section class="panel">
      <form class="toolbar" @submit.prevent="searchUsers">
        <el-input
          v-model="search"
          clearable
          placeholder="搜索 UUID、用户名或邮箱"
          aria-label="搜索用户"
        /><el-select v-model="role" placeholder="全部角色" clearable aria-label="角色筛选"
          ><el-option label="普通用户" value="USER" /><el-option
            label="管理员"
            value="ADMIN" /><el-option label="超级管理员" value="SUPER_ADMIN" /></el-select
        ><el-button native-type="submit" type="primary">查询</el-button
        ><span class="muted">共 {{ total }} 位用户</span>
      </form>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-table
        v-else
        v-loading="loading"
        :data="users"
        row-key="uuid"
        style="width: 100%"
        empty-text="暂无匹配用户"
      >
        <el-table-column label="用户" min-width="180"
          ><template #default="{ row }"
            ><div class="user-cell">
              <el-avatar
                class="user-avatar"
                :size="36"
                :src="row.avatarUrl?.trim() || undefined"
                >{{ avatarFallback(row.username) }}</el-avatar
              >
              <div>
                <strong>{{ row.username }}</strong>
                <div class="muted">{{ row.email }}</div>
              </div>
            </div></template
          ></el-table-column
        >
        <el-table-column label="UUID" min-width="270"
          ><template #default="{ row }"
            ><span class="mono">{{ row.uuid }}</span></template
          ></el-table-column
        >
        <el-table-column label="系统角色" width="135"
          ><template #default="{ row }"
            ><el-tag
              :type="
                row.systemRole === 'SUPER_ADMIN'
                  ? 'danger'
                  : row.systemRole === 'ADMIN'
                    ? 'primary'
                    : 'info'
              "
              effect="plain"
              >{{ roleLabel(row.systemRole) }}</el-tag
            ></template
          ></el-table-column
        >
        <el-table-column label="注册时间" min-width="160"
          ><template #default="{ row }">{{ date(row.createdAt) }}</template></el-table-column
        >
        <el-table-column label="操作" width="285" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="showDetail(row.uuid)">详情</el-button
            ><el-button
              v-if="row.systemRole !== 'SUPER_ADMIN'"
              link
              :type="row.systemRole === 'ADMIN' ? 'danger' : 'primary'"
              @click="requestRole(row)"
              >{{ row.systemRole === 'ADMIN' ? '撤销管理员' : '任命管理员' }}</el-button
            ><el-button
              v-if="row.systemRole !== 'SUPER_ADMIN'"
              link
              type="danger"
              @click="requestDelete(row)"
              >删除账号</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-model:current-page="page"
        class="pagination"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </section>
    <el-drawer v-model="detailVisible" title="用户详情" size="520px">
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
      <div v-else-if="detailLoading" v-loading="true" style="height: 150px" />
      <template v-else-if="detail"
        ><div class="detail-profile">
          <el-avatar
            class="detail-avatar"
            :size="60"
            :src="detail.avatarUrl?.trim() || undefined"
            >{{ avatarFallback(detail.username) }}</el-avatar
          >
          <div>
            <h2>{{ detail.username }}</h2>
            <p>{{ detail.email }}</p>
          </div>
        </div>
        <el-descriptions :column="1" border
          ><el-descriptions-item v-for="field in detailFields" :key="field.key" :label="field.label"
            ><span :class="{ mono: field.key === 'uuid' }">{{
              detailValue(field.key)
            }}</span></el-descriptions-item
          ></el-descriptions
        ></template
      >
    </el-drawer>
    <el-dialog
      v-model="roleVisible"
      :title="pendingRole === 'ADMIN' ? '任命管理员' : '撤销管理员'"
      width="460px"
      :close-on-click-modal="false"
      ><p v-if="roleUser">
        将 <strong>{{ roleUser.username }}</strong
        >（{{ roleUser.email }}）设为{{ roleLabel(pendingRole) }}。
      </p>
      <p class="muted">
        管理员可以使用通行证中的 OAuth 和 SSO 管理功能。此操作会记录在操作记录中。
      </p>
      <el-alert
        v-if="restored"
        title="已完成再次验证，请核对后重新确认本次操作。"
        type="success"
        :closable="false"
      /><template #footer
        ><el-button :disabled="savingRole" @click="roleVisible = false">取消</el-button
        ><el-button type="primary" :loading="savingRole" @click="saveRole"
          >确认{{ pendingRole === 'ADMIN' ? '任命' : '撤销' }}</el-button
        ></template
      ></el-dialog
    ><el-dialog
      v-model="deleteVisible"
      title="永久删除用户账号"
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="!deleting"
      :show-close="!deleting"
      @closed="clearDeleteDialog"
      ><template v-if="deleteTarget"
        ><p>
          即将永久删除 <strong>{{ deleteTarget.username }}</strong
          >（{{ deleteTarget.email }}）及其账号资料、登录凭证、会话和授权。此操作无法撤销。
        </p>
        <p class="muted">输入用户名 <strong>{{ deleteTarget.username }}</strong> 以确认删除。</p>
        <el-input
          v-model="deleteConfirmation"
          :disabled="deleting"
          :placeholder="deleteTarget.username"
          autocomplete="off"
          aria-label="输入用户名确认删除"
        />
        <el-alert
          v-if="restoredDelete"
          title="已完成再次验证。请核对当前用户后，重新输入用户名确认删除。"
          type="warning"
          :closable="false"
          class="delete-notice"
        />
        <p class="muted delete-verification-hint">提交前需要再次验证管理员身份。</p>
      </template>
      <template #footer
        ><el-button :disabled="deleting" @click="deleteVisible = false">取消</el-button
        ><el-button
          type="danger"
          :loading="deleting"
          :disabled="deleteConfirmation.trim() !== deleteTarget?.username"
          @click="saveDelete"
          >永久删除账号</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { consoleApi, type ConsoleUser, type SystemRole } from '@/api/console'
import { avatarFallback } from '@/utils/avatar'
import { VerificationCancelledError, withConsoleVerification } from '@/utils/consoleVerification'
import {
  consumeRoleDraft,
  consumeUserDeleteDraft,
} from '@/utils/navigation'
const search = ref('')
const role = ref('')
const page = ref(1)
const size = 20
const users = ref<ConsoleUser[]>([])
const total = ref(0)
const loading = ref(true)
const error = ref('')
const detail = ref<ConsoleUser | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const roleVisible = ref(false)
const roleUser = ref<ConsoleUser | null>(null)
const pendingRole = ref<'ADMIN' | 'USER'>('ADMIN')
const savingRole = ref(false)
const restored = ref(false)
const deleteVisible = ref(false)
const deleteTarget = ref<ConsoleUser | null>(null)
const deleteConfirmation = ref('')
const deleting = ref(false)
const restoredDelete = ref(false)
const roleLabel = (value: SystemRole) =>
  ({ USER: '普通用户', ADMIN: '管理员', SUPER_ADMIN: '超级管理员' })[value]
const date = (value?: string) => (value ? new Date(value).toLocaleString('zh-CN') : '—')
const detailFields: { key: keyof ConsoleUser; label: string }[] = [
  { key: 'uuid', label: 'UUID' },
  { key: 'systemRole', label: '系统角色' },
  { key: 'developerVerificationType', label: '开发者认证' },
  { key: 'realName', label: '真实姓名' },
  { key: 'gender', label: '性别' },
  { key: 'birthDate', label: '出生日期' },
  { key: 'region', label: '所在地区' },
  { key: 'bio', label: '个人简介' },
  { key: 'createdAt', label: '注册时间' },
  { key: 'updatedAt', label: '更新于' },
]
const detailValue = (key: keyof ConsoleUser) => {
  if (!detail.value) return '—'
  if (key === 'systemRole') return roleLabel(detail.value.systemRole)
  if (key === 'createdAt' || key === 'updatedAt') return date(detail.value[key])
  if (key === 'developerVerificationType')
    return (
      (
        {
          none: '未认证',
          personal: '个人开发者',
          enterprise: '企业开发者',
        } as Record<string, string>
      )[detail.value[key]] ?? detail.value[key]
    )
  return detail.value[key] || '—'
}
let loadGeneration = 0
const load = async () => {
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const result = await consoleApi.users({
      page: page.value - 1,
      size,
      search: search.value.trim(),
      role: role.value,
    })
    if (generation !== loadGeneration) return
    users.value = result.items
    total.value = result.total
  } catch (failure) {
    if (generation === loadGeneration)
      error.value = failure instanceof Error ? failure.message : '用户加载失败'
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}
const searchUsers = () => {
  page.value = 1
  void load()
}
const showDetail = async (uuid: string) => {
  detail.value = null
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await consoleApi.user(uuid)
  } catch (failure) {
    detailError.value = failure instanceof Error ? failure.message : '用户详情加载失败'
  } finally {
    detailLoading.value = false
  }
}
const requestRole = (user: ConsoleUser) => {
  roleUser.value = user
  pendingRole.value = user.systemRole === 'ADMIN' ? 'USER' : 'ADMIN'
  restored.value = false
  roleVisible.value = true
}
const requestDelete = (user: ConsoleUser) => {
  if (user.systemRole === 'SUPER_ADMIN') return
  deleteTarget.value = user
  deleteConfirmation.value = ''
  restoredDelete.value = false
  deleteVisible.value = true
}
const clearDeleteDialog = () => {
  deleteTarget.value = null
  deleteConfirmation.value = ''
  restoredDelete.value = false
}
const saveDelete = async () => {
  const target = deleteTarget.value
  if (!target || deleting.value || deleteConfirmation.value.trim() !== target.username) return
  deleting.value = true
  try {
    await withConsoleVerification(() => consoleApi.deleteUser(target.uuid))
    deleteVisible.value = false
    if (detail.value?.uuid === target.uuid) detailVisible.value = false
    ElMessage.success('用户账号已删除')
    await load()
    if (users.value.length === 0 && page.value > 1) {
      page.value -= 1
      await load()
    }
  } catch (failure) {
    if (!(failure instanceof VerificationCancelledError))
      ElMessage.error(failure instanceof Error ? failure.message : '账号删除失败')
  } finally {
    deleting.value = false
  }
}
const saveRole = async () => {
  const target = roleUser.value
  if (!target || savingRole.value) return
  const nextRole = pendingRole.value
  savingRole.value = true
  try {
    await withConsoleVerification(() => consoleApi.setRole(target.uuid, nextRole))
    roleVisible.value = false
    ElMessage.success('用户角色已更新')
    await load()
  } catch (failure) {
    if (!(failure instanceof VerificationCancelledError))
      ElMessage.error(failure instanceof Error ? failure.message : '角色更新失败')
  } finally {
    savingRole.value = false
  }
}
onMounted(async () => {
  void load()
  const roleDraft = consumeRoleDraft()
  if (roleDraft) {
    try {
      roleUser.value = await consoleApi.user(roleDraft.uuid)
      if (roleUser.value.systemRole !== 'SUPER_ADMIN') {
        pendingRole.value = roleDraft.systemRole
        restored.value = true
        roleVisible.value = true
      }
    } catch (failure) {
      ElMessage.error(failure instanceof Error ? failure.message : '角色操作恢复失败')
    }
  }
  const deleteDraft = consumeUserDeleteDraft()
  if (deleteDraft) {
    try {
      const target = await consoleApi.user(deleteDraft.uuid)
      if (target.systemRole !== 'SUPER_ADMIN') {
        deleteTarget.value = target
        deleteConfirmation.value = ''
        restoredDelete.value = true
        deleteVisible.value = true
      }
    } catch (failure) {
      ElMessage.error(failure instanceof Error ? failure.message : '账号删除操作恢复失败')
    }
  }
})
</script>
<style scoped>
.user-cell {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 6px 0;
}
.user-cell strong {
  font-weight: 550;
}
.user-avatar,
.detail-avatar {
  color: #805c00;
  background-color: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
  font-weight: 600;
  flex-shrink: 0;
}
.user-cell .muted {
  font-size: 12px;
  margin-top: 3px;
}
.detail-profile {
  display: flex;
  gap: 18px;
  align-items: center;
  margin-bottom: 25px;
}
h2 {
  margin: 0;
  font-size: 20px;
}
.detail-profile p {
  margin: 7px 0 0;
  color: #647386;
}
.el-descriptions {
  overflow-wrap: anywhere;
}
.el-alert {
  margin-top: 10px;
}
.delete-notice {
  margin-top: 18px;
}
.delete-verification-hint {
  margin: 16px 0 0;
}
</style>
