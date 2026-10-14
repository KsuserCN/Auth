<template>
  <div class="page">
    <div class="page-heading">
      <div>
        <h1>认证用户应用</h1>
        <p>按个人认证、企业认证或管理员账号，查看其创建的 OAuth 应用。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <section class="panel">
      <form class="toolbar" @submit.prevent="searchUsers">
        <el-input
          v-model="search"
          clearable
          placeholder="搜索 UUID、用户名或邮箱"
          aria-label="搜索认证用户"
        />
        <el-select v-model="verification" aria-label="认证用户类型">
          <el-option label="全部认证用户" value="verified" />
          <el-option label="个人认证" value="personal" />
          <el-option label="企业认证" value="enterprise" />
          <el-option label="管理员" value="admin" />
        </el-select>
        <el-button native-type="submit" type="primary">查询</el-button>
        <span class="muted">共 {{ total }} 位用户</span>
      </form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-table
        v-else
        v-loading="loading"
        :data="users"
        row-key="uuid"
        empty-text="暂无匹配的认证用户"
      >
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar
                class="user-avatar"
                :size="36"
                :src="row.avatarUrl?.trim() || undefined"
              >
                {{ avatarFallback(row.username) }}
              </el-avatar>
              <div>
                <strong>{{ row.username }}</strong>
                <div class="muted">{{ row.email }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="认证主体" min-width="180">
          <template #default="{ row }">{{
            row.verificationEntityName || '主体资料未登记'
          }}</template>
        </el-table-column>
        <el-table-column label="认证与角色" min-width="180">
          <template #default="{ row }">
            <div class="identity-tags">
              <el-tag
                v-if="row.developerVerificationType !== 'none'"
                type="success"
                effect="plain"
                >{{ verificationLabel(row.developerVerificationType) }}</el-tag
              >
              <el-tag v-if="row.systemRole !== 'USER'" effect="plain">{{
                roleLabel(row.systemRole)
              }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="UUID" min-width="270">
          <template #default="{ row }"
            ><span class="mono">{{ row.uuid }}</span></template
          >
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }"
            ><el-button link type="primary" @click="showApplications(row.uuid)"
              >查看应用</el-button
            ></template
          >
        </el-table-column>
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
    <el-drawer
      v-model="appsVisible"
      title="用户创建的应用"
      size="min(960px, 100vw)"
      @closed="closeApplications"
    >
      <div v-if="appsError">
        <el-alert :title="appsError" type="error" :closable="false" show-icon />
        <el-button class="retry-button" @click="retryApplications">重试</el-button>
      </div>
      <div v-else-if="appsLoading" v-loading="true" style="height: 160px" />
      <template v-else-if="selected">
        <div class="owner-heading">
          <h2>{{ selected.user.username }}</h2>
          <p class="muted">
            {{ selected.user.email }} ·
            {{ selected.user.verificationEntityName || '主体资料未登记' }}
          </p>
          <span class="mono">{{ selected.user.uuid }}</span>
        </div>
        <form class="toolbar" @submit.prevent>
          <el-select v-model="protocol" aria-label="应用协议">
            <el-option label="全部协议" value="all" />
            <el-option label="OAuth" value="oauth2" />
          </el-select>
          <span class="muted">共 {{ filteredApps.length }} 个应用</span>
          <el-button @click="loadApplications(selected.user.uuid)">刷新应用</el-button>
        </form>
        <el-table
          :data="filteredApps"
          :row-key="(app: OwnedApplication) => `${app.protocol}:${app.clientId}`"
          empty-text="该用户暂无此类应用"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <el-descriptions class="app-details" :column="1" border>
                <el-descriptions-item label="回调地址"
                  ><div v-for="uri in row.redirectUris" :key="uri" class="mono">
                    {{ uri }}
                  </div></el-descriptions-item
                >
                <el-descriptions-item label="权限范围">{{
                  row.scopes.join(' ') || '—'
                }}</el-descriptions-item>
                <el-descriptions-item label="联系信息">{{
                  row.contactInfo || '—'
                }}</el-descriptions-item>
                <el-descriptions-item label="更新于">{{
                  date(row.updatedAt)
                }}</el-descriptions-item>
              </el-descriptions>
            </template>
          </el-table-column>
          <el-table-column label="应用" min-width="220">
            <template #default="{ row }">
              <div class="user-cell">
                <el-avatar shape="square" :size="36" :src="row.logoUrl?.trim() || undefined">{{
                  avatarFallback(row.clientName)
                }}</el-avatar>
                <div>
                  <strong>{{ row.clientName }}</strong>
                  <div class="mono">{{ row.clientId }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="协议" width="120"
            ><template #default="{ row }">{{
              'OAuth'
            }}</template></el-table-column
          >
          <el-table-column label="状态" width="90"
            ><template #default="{ row }"
              ><el-tag :type="row.isActive ? 'success' : 'info'" effect="plain">{{
                row.isActive ? '启用' : '停用'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="创建时间" min-width="175"
            ><template #default="{ row }">{{ date(row.createdAt) }}</template></el-table-column
          >
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  consoleApi,
  type ConsoleUser,
  type OwnedApplication,
  type UserApplications,
} from '@/api/console'
import { avatarFallback } from '@/utils/avatar'
import { roleLabel, verificationLabel } from '@/utils/userIdentity'

const route = useRoute()
const router = useRouter()
const search = ref('')
const verification = ref<'verified' | 'personal' | 'enterprise' | 'admin'>('verified')
const page = ref(1)
const size = 20
const users = ref<ConsoleUser[]>([])
const total = ref(0)
const loading = ref(true)
const error = ref('')
const appsVisible = ref(false)
const appsLoading = ref(false)
const appsError = ref('')
const selected = ref<UserApplications | null>(null)
const protocol = ref<'all' | 'oauth2'>('all')
const filteredApps = computed(() =>
  (selected.value?.applications || []).filter(
    (app) => protocol.value === 'all' || app.protocol === protocol.value,
  ),
)
const date = (value: string) => (value ? new Date(value).toLocaleString('zh-CN') : '—')
let loadGeneration = 0
let appsGeneration = 0

const load = async () => {
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const result = await consoleApi.users({
      page: page.value - 1,
      size,
      search: search.value.trim(),
      verification: verification.value,
      sort: 'createdAt',
      direction: 'desc',
    })
    if (generation !== loadGeneration) return
    users.value = result.items
    total.value = result.total
  } catch (failure) {
    if (generation === loadGeneration)
      error.value = failure instanceof Error ? failure.message : '认证用户加载失败'
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}
const searchUsers = () => {
  page.value = 1
  void load()
}
const loadApplications = async (uuid: string) => {
  const generation = ++appsGeneration
  selected.value = null
  appsLoading.value = true
  appsError.value = ''
  appsVisible.value = true
  try {
    const result = await consoleApi.userApplications(uuid)
    if (generation === appsGeneration) selected.value = result
  } catch (failure) {
    if (generation === appsGeneration)
      appsError.value = failure instanceof Error ? failure.message : '用户应用加载失败'
  } finally {
    if (generation === appsGeneration) appsLoading.value = false
  }
}
const showApplications = (uuid: string) => {
  if (route.query.user === uuid) void loadApplications(uuid)
  else void router.replace({ query: { ...route.query, user: uuid } })
}
const retryApplications = () => {
  if (typeof route.query.user === 'string') void loadApplications(route.query.user)
}
const closeApplications = () => {
  ++appsGeneration
  selected.value = null
  appsLoading.value = false
  if (route.query.user) {
    const query = { ...route.query }
    delete query.user
    void router.replace({ query })
  }
}
watch(
  () => route.query.user,
  (uuid) => {
    protocol.value = 'all'
    if (typeof uuid === 'string' && uuid) void loadApplications(uuid)
    else {
      ++appsGeneration
      appsVisible.value = false
      selected.value = null
    }
  },
  { immediate: true },
)
onMounted(load)
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}
.user-avatar {
  color: var(--console-link);
  background-color: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
  font-weight: 600;
  flex-shrink: 0;
}
.user-cell .muted,
.user-cell .mono {
  margin-top: 4px;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.identity-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.owner-heading {
  margin-bottom: 24px;
  overflow-wrap: anywhere;
}
.owner-heading h2 {
  margin: 0;
  font-size: 22px;
}
.owner-heading p {
  margin: 10px 0;
}
.app-details {
  padding: 12px;
  overflow-wrap: anywhere;
}
.retry-button {
  margin-top: 16px;
}
</style>
